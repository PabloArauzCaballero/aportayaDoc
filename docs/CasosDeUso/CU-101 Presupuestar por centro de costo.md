---
tags:
  - caso-uso
  - modulo/13-contabilidad-erp
codigo: CU-101
criticidad: media
actores: [Contabilidad, Directorio]
normas: [Control interno, NIIF]
---

# CU-101 — Presupuestar por centro de costo

> **Objetivo.** Que "nos pasamos de presupuesto" sea un hecho verificable contra
> una cifra que alguien autorizó antes del período, no una sensación posterior.

## Actores y disparador

- **Actor principal:** Contabilidad, con aprobación de Directorio.
- **Disparadores:** inicio de un [[ejercicio_fiscal]]; alta de un
  [[centro_costo]] nuevo (área, producto o campaña) que necesita presupuesto.

## Precondiciones

1. Existe el [[centro_costo]] a presupuestar.
2. Existe el [[ejercicio_fiscal]] para el que se presupuesta.

## Flujo principal

1. Contabilidad crea un [[presupuesto]] en estado `BORRADOR` para un
   `centro_costo` y un `ejercicio_fiscal`.
2. Se agregan las [[partida_presupuestaria]] — una por [[cuenta_contable]] y
   [[periodo_contable]], con `monto_presupuestado`.
3. Directorio aprueba: `presupuesto.estado = 'APROBADO'`, con `aprobado_por` y
   `aprobado_en`.
4. Durante el ejercicio, cada [[factura_proveedor]] y [[pago_a_proveedor]]
   contra ese centro de costo actualiza `partida_presupuestaria.monto_ejecutado`.
5. Al cierre del ejercicio (`ejercicio_fiscal.estado = 'CERRADO'`), el
   `presupuesto` pasa a `CERRADO` y ya no admite nuevas partidas.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | Se intenta ejecutar gasto contra un presupuesto en `BORRADOR` | Se permite igual: el presupuesto no bloquea el gasto, solo lo mide; bloquear el gasto es una decisión de negocio explícita, no automática |
| 4a | `monto_ejecutado` supera `monto_presupuestado` | No se rechaza la factura: se registra la desviación, visible en el backoffice, para que alguien la explique |
| — | Se agrega una partida a un presupuesto ya `CERRADO` | Se rechaza |
| — | Se crea un centro de costo sin ningún presupuesto | Válido: el centro de costo existe independientemente, y `partida_presupuestaria` es opcional por diseño |

## Postcondiciones

- Cada centro de costo tiene, si Contabilidad decidió presupuestarlo, un
  registro comparable de lo autorizado contra lo ejecutado, por cuenta y por mes.

## Contrato · `openapi/erp.yaml`

```ts
export const EntradaCU101 = z.object({
  centroCostoId: z.string().uuid(),
  ejercicioFiscalId: z.string().uuid(),
  nombre: z.string().max(100),
  partidas: z.array(z.object({
    cuentaContableId: z.string().uuid(),
    periodoContableId: z.string().uuid(),
    montoPresupuestado: MontoSchema,
  })).min(1),
}).strict()

export const SalidaCU101 = z.object({
  presupuestoId: z.string().uuid(),
  estado: z.enum(['BORRADOR', 'APROBADO', 'CERRADO']),
}).strict()

export const ErroresCU101 = {
  CENTRO_COSTO_INEXISTENTE: 'AP-CU101-01',
  PRESUPUESTO_DUPLICADO: 'AP-CU101-02',
  PARTIDA_SOBRE_PRESUPUESTO_CERRADO: 'AP-CU101-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `CENTRO_COSTO_INEXISTENTE` | El `centro_costo_id` no existe o está inactivo |
| `PRESUPUESTO_DUPLICADO` | Ya existe un presupuesto para ese centro de costo y ejercicio (`UQ`) |
| `PARTIDA_SOBRE_PRESUPUESTO_CERRADO` | Se intenta agregar una partida a un presupuesto `CERRADO` |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularDesviacion` | `monto_ejecutado - monto_presupuestado`; puro |
| Molécula | `PresupuestoRepositorio` | Alta y aprobación del presupuesto |
| Molécula | `PartidaPresupuestariaRepositorio` | Alta de partidas y actualización de ejecutado |
| Organismo | `CU101Presupuestar` | Transacción de alta/aprobación |
| Página | `apps/backoffice` — pantalla de presupuesto por centro de costo | Tabla presupuestado vs ejecutado, con desviación resaltada |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `presupuesto.aprobado` | Habilita la comparación presupuestado/ejecutado en el backoffice | `CONTABILIDAD_ERP_PRESUPUESTO` |
| `partida_presupuestaria.ejecutada` | Actualiza `monto_ejecutado` al confirmarse una factura o pago | Interno |

## Interfaz

- **App:** No tiene pantalla: es gestión administrativa interna.
- **Backoffice:** Tabla de centros de costo con su presupuesto, ejecutado y
  desviación, filtrable por ejercicio y período.

## Restricciones aplicables

`R-CTB-03`

El presupuesto es un control administrativo interno, no un tope legal: la base
garantiza unicidad y coherencia, no impide gastar de más.

## Evidencia que deja

[[centro_costo]] · [[presupuesto]] · [[partida_presupuestaria]]

## Criterios de aceptación

```gherkin
Dado un centro de costo sin presupuesto para el ejercicio vigente
Cuando Contabilidad crea un presupuesto con al menos una partida
Entonces el presupuesto queda en estado BORRADOR hasta que Directorio lo aprueba

Dado un presupuesto aprobado con una partida de una cuenta contable
Cuando se registra una factura_proveedor contra esa cuenta y ese centro de costo
Entonces monto_ejecutado de la partida aumenta en el monto de la factura

Dado un presupuesto en estado CERRADO
Cuando se intenta agregar una partida nueva
Entonces el sistema devuelve PARTIDA_SOBRE_PRESUPUESTO_CERRADO
```

## Ver también

[[CU-100 Abrir y cerrar el período contable]] · [[CU-103 Registrar y pagar una factura de proveedor]]
