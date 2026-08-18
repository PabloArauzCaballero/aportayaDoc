---
name: back-nestjs
description: "Escribir el backend de AportaYa en NestJS: módulo por módulo de la bóveda, un caso de uso por archivo, la transacción en el organismo, contexto de RLS, controladores sin lógica, adaptadores externos e inyección de dependencias. Úsala al crear un endpoint, un caso de uso, un módulo nuevo o al tocar cualquier código de apps/api."
---

# Backend con NestJS

Un módulo de NestJS por módulo de la bóveda; dentro, la
[[ADR-009 Composición atómica|composición atómica]] hecha carpetas.

```
modulos/03_aportes_pagos_qr/
├── aportes.module.ts
├── aplicacion/      ORGANISMOS  · CU21CobrarAporte.ts       ← abre la transacción
├── dominio/         ÁTOMOS      · CalculoDeAporte.ts        ← puro, sin IO
├── infraestructura/ MOLÉCULAS   · ObligacionRepositorio.ts  ← SQL, sin lógica
├── http/            PÁGINA      · aportes.controller.ts     ← HTTP ⇄ caso de uso
└── pruebas/                     · CU21.spec.ts
```

## El controlador no piensa

```ts
@Post('aportes')
async cobrar(@Body() body: unknown, @Ctx() ctx: Contexto) {
  const entrada = EntradaCU21.parse(body)          // contrato estricto
  return this.cu21.ejecutar(ctx, entrada)          // y nada más
}
```

Sin `if` de negocio, sin cálculos, sin SQL, sin transacción. Traduce y delega.

## El caso de uso es el único que abre transacción

```ts
export class CU21CobrarAporte {
  async ejecutar(ctx: Contexto, entrada: EntradaCU21): Promise<SalidaCU21> {
    return conTransaccion(ctx, async (tx) => {          // BEGIN + SET LOCAL
      await this.idempotencia.exigirNueva(tx, entrada.claveIdempotencia)

      const obligacion = await this.obligaciones.tomarParaActualizar(tx, entrada.obligacionId)
      const calculo    = calcularAporte(obligacion, entrada.monto)   // átomo puro

      await this.movimientos.insertarPar(tx, calculo)                 // molécula
      await this.eventos.registrarYEncolar(tx, 'aporte.confirmado', calculo)

      return calculo.aSalida()
    })
  }
}
```

Reglas visibles en ese fragmento, todas obligatorias:

| Regla | Dónde se ve |
| --- | --- |
| Idempotencia **antes** de escribir | `exigirNueva` es la primera línea |
| Contexto de RLS dentro de la transacción | `conTransaccion` hace el `SET LOCAL` |
| El cálculo es un átomo puro | `calcularAporte` no recibe la conexión |
| El saldo se deriva, no se actualiza | `insertarPar` inserta movimiento y contrapartida |
| El efecto externo va por outbox | `registrarYEncolar`, dentro del `COMMIT` |
| Nada de proveedores acá | Ningún `await pasarela.*` dentro de la transacción |

## Inyección de dependencias

- Todo borde externo entra por una **interfaz de dominio** (`PasarelaQr`,
  `ServicioFiscal`, `Mensajeria`), con su adaptador registrado por token.
- Los repositorios reciben `tx`; **no** crean conexiones.
- El reloj y el generador de identificadores se inyectan (`Reloj`, `Ids`): son lo que
  vuelve determinista una prueba de plazos.
- Nada de servicios genéricos tipo `CrudService`: cada caso de uso es explícito.

## Piezas transversales de `comun/`

| Pieza | Qué hace |
| --- | --- |
| `conTransaccion(ctx, fn)` | Abre transacción, fija `app.usuario_id` y `app.rol` con `SET LOCAL`, revierte ante error |
| `Idempotencia` | Busca la clave; si existe, devuelve la respuesta original sin escribir |
| `FiltroDeErrores` | Traduce el rechazo de la base al código `R-XXX-nn` documentado; nunca filtra SQL al cliente |
| `Traza` | Propaga el identificador de traza hasta el worker; toda línea de log lleva `cu` y `usuario_id` |
| `ConfigSchema` | Valida las variables de entorno al arrancar; si falta una, el proceso no levanta |

## Guardas y permisos

- Autenticación resuelve usuario, rol y **dispositivo de confianza** (CU-04).
- La autorización se verifica en el servidor **contra el recurso concreto**, no solo
  contra el rol.
- La guarda es conveniencia; la protección real es la política de fila. Si una
  consulta sin contexto devolvería datos ajenos, el problema no es la guarda: falta
  RLS.

## Errores

| Situación | Qué lanza el caso de uso | Qué devuelve la API |
| --- | --- | --- |
| Regla de aplicación | `ErrorDeNegocio('AP-CU21-02')` | `422` con código y mensaje humano |
| Restricción de base | Error de PostgreSQL | `409` traducido a `R-XXX-nn` |
| Sin permiso | `ErrorDeAutorizacion` | `403` sin detalles |
| Proveedor caído | No aplica: va por cola | `202` aceptado |

## Antipatrones que se rechazan en revisión

- Lógica de negocio en el controlador o en el repositorio.
- Transacción abierta dentro de una molécula.
- `await` a un proveedor externo dentro de `conTransaccion`.
- Servicio que atiende cuatro casos de uso "porque se parecen".
- Consulta fuera de `conTransaccion` sobre tablas con RLS.
- `any` para esquivar el tipo introspectado de una tabla.

## Ver también

`errores-api` · `seguridad-sesion-rls` · `idempotencia-reintentos` · `implementar-desde-boveda` · `arquitectura-atomica` · `datos-kysely` · `contratos-api` ·
`trabajos-outbox` · `docs/Arquitectura/Flujo de una transacción.md`
