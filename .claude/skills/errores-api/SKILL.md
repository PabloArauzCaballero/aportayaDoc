---
name: errores-api
description: "Devolver y traducir errores en AportaYa: códigos AP-CU<NN>-<nn>, mapeo a HTTP, traducción de violaciones de restricción de PostgreSQL a mensajes útiles, y qué nunca se le muestra al usuario. Úsala al implementar un endpoint, al agregar un código de error nuevo, o cuando un error de base llegue crudo hasta la app."
---

# Errores

Un error de esta plataforma tiene que servir a tres lectores a la vez: al usuario
(que necesita saber qué hacer), al soporte (que necesita saber qué pasó) y al
auditor (que necesita saber qué regla actuó). Un `500 Internal Server Error` no
sirve a ninguno.

## Anatomía

```json
{
  "codigo": "AP-CU21-03",
  "mensaje": "No tenés saldo suficiente para este aporte.",
  "detalle": { "faltante": "45.00", "moneda": "BOB" },
  "trazaId": "01J8X…"
}
```

| Campo | Para quién | Regla |
| --- | --- | --- |
| `codigo` | Soporte y auditoría | `AP-CU<NN>-<nn>`, definido en `openapi/<servicio>.yaml` |
| `mensaje` | Usuario | En español, sin jerga, dice **qué hacer** cuando hay algo que hacer |
| `detalle` | App | Datos para armar un mensaje mejor; opcional |
| `trazaId` | Soporte | Correlaciona con la bitácora. Es lo único que el usuario le dicta al soporte |

## Mapeo a HTTP

| Situación | HTTP | Cuerpo |
| --- | --- | --- |
| Entrada inválida por el contrato | `400` | Lista de campos con mensaje |
| Regla de negocio de la aplicación | `422` | `{ codigo: 'AP-CU21-02', … }` |
| Sin permiso o fuera de política de fila | `403` o resultado vacío | Sin detalles internos |
| Restricción de la base rechaza | `409` | `{ codigo: 'R-LIM-02', … }` traducido |
| Clave de idempotencia repetida | `200` | **La respuesta original, íntegra** |
| Proveedor externo indisponible | `202` | Aceptado; se completa por la cola |
| Falla no prevista | `500` | Solo `trazaId`. Nada más |

## Traducir el error de PostgreSQL

Cuando la base rechaza, llega un error con el **nombre de la restricción**. Ese
nombre es la clave del catálogo de traducción:

```ts
const TRADUCCION: Record<string, { codigo: string; mensaje: string; http: number }> = {
  uq_transaccion_clave_idempotencia: { codigo: 'AP-CU21-00', mensaje: 'Operación ya registrada.', http: 200 },
  ck_movimiento_monto_positivo:      { codigo: 'R-BIL-03',   mensaje: 'El importe debe ser mayor a cero.', http: 409 },
  ex_puntaje_vigente:                { codigo: 'R-REP-02',   mensaje: 'Ya hay un puntaje vigente para ese período.', http: 409 },
}
```

Reglas de la traducción:

1. **Nunca dejar pasar el error crudo.** Un mensaje con `duplicate key value
   violates unique constraint "uq_…"` en la pantalla del usuario es una fuga de
   estructura interna y una mala experiencia al mismo tiempo.
2. **Sin traducción ⇒ `500` y alerta.** Una restricción que dispara y no está en el
   catálogo es un caso que nadie previó: se registra como incidente, no se
   improvisa un mensaje genérico y se olvida.
3. **El código de la restricción viaja al cuerpo.** Así el `409` que ve el usuario
   se rastrea hasta la regla y hasta la norma que la obliga.

## Cómo se escribe el mensaje

| En vez de | Escribir |
| --- | --- |
| "Error de validación" | "El monto debe ser igual al de la obligación: Bs 500,00." |
| "Operación no permitida" | "Superaste el límite mensual de tu nivel. Podés ampliarlo verificando tu identidad." |
| "Constraint violation" | "Ese pago ya fue registrado." |
| "Usuario no autorizado" | "No tenés acceso a este grupo." |

Y lo que **nunca** aparece en un mensaje: SQL, nombres de tabla o columna, trazas,
rutas de archivo, identificadores internos de otro usuario, y —caso especial— el
motivo real de un bloqueo por inteligencia financiera (skill `cumplimiento-uif`).

## Registro de códigos

- Un código por regla, definido junto al contrato del caso de uso.
- **Los códigos no se reutilizan.** Si un error deja de existir, su código queda
  retirado; volver a usarlo mezcla incidentes viejos con nuevos en el soporte.
- Cada código del contrato corresponde a un criterio de aceptación o a una
  restricción citada. Un código sin prueba que lo dispare es decorativo.

## Errores en la app y en el backoffice

| Lado | Comportamiento |
| --- | --- |
| App | Muestra `mensaje`, ofrece la acción que corresponde (reintentar, verificar identidad, contactar soporte) y guarda `trazaId` para copiarlo |
| Backoffice | Muestra además `codigo` y `trazaId` visibles: el operador los necesita para escalar |
| Ambos | Un `202` **no es un error**: se muestra como "en proceso", con el estado actualizándose |

## Checklist

- [ ] Todo error previsto tiene código en el contrato del caso de uso.
- [ ] Toda restricción que puede dispararse en ese flujo está en la traducción.
- [ ] Hay prueba de que el reintento devuelve `200` con la respuesta original.
- [ ] Ningún mensaje contiene nombres de tabla, SQL ni trazas.
- [ ] El `trazaId` correlaciona con `bitacora_evento`.
- [ ] Los errores sin traducción generan alerta, no un mensaje genérico.

## Ver también

`contratos-api` · `idempotencia-reintentos` · `seguridad-sesion-rls` ·
`observabilidad` · `restriccion` · `docs/Arquitectura/ADR-006 Contratos y validación.md`
