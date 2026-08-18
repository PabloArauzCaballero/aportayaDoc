---
tags:
  - caso-uso
  - modulo/14-publicidad-campanas
codigo: CU-114
criticidad: alta
actores: [Sistema, Contabilidad]
normas: [SIN facturación en línea, Ley 393]
---

# CU-114 — Liquidar y facturar el gasto publicitario

> **Objetivo.** Que el gasto de un anunciante en un período se cobre por el
> mismo camino riguroso que cualquier otro ingreso de la empresa, sin inventar
> un tercer sistema de facturación.

## Actores y disparador

- **Actor principal:** el sistema, en la corrida periódica de liquidación;
  Contabilidad supervisa.
- **Disparadores:** cierre de un período de facturación (mensual) de una
  [[cuenta_publicitaria]] con consumo registrado.

## Precondiciones

1. Existen [[impresion_anuncio]] y/o [[clic_anuncio]] con `costo` acumulado en
   el período para esa `cuenta_publicitaria`.

## Flujo principal

1. El sistema suma el `costo` de todas las [[impresion_anuncio]] y
   [[clic_anuncio]] del período, agrupado por `cuenta_publicitaria_id`.
2. Se crea [[factura_publicidad]] con `periodo` (`YYYY-MM`), `monto_total`,
   `moneda` y estado `GENERADA`.
3. Se emite la [[factura_electronica]] (módulo 11) como comprobante fiscal,
   enlazada por `factura_electronica_id`; `estado = 'FACTURADA'`.
4. **En la misma transacción** se crea la [[cuenta_por_cobrar]] (módulo 13)
   con `origen_tipo = 'FACTURA_PUBLICIDAD'` y `origen_id` apuntando a esta
   factura, enlazada por `cuenta_por_cobrar_id`.
5. Cuando el anunciante paga, el cobro sigue el camino de
   [[CU-104 Cobrar una cuenta por cobrar]]; al cobrarse por completo,
   `factura_publicidad.estado = 'COBRADA'`.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | El período no tuvo ningún consumo | No se genera `factura_publicidad`: no hay obligación de facturar cero |
| 3a | La emisión de `factura_electronica` falla ante el SIN | `factura_publicidad` queda `GENERADA` sin avanzar a `FACTURADA`; se reintenta, mismo patrón que [[CU-32 Emitir factura electrónica]] |
| — | El anunciante alcanza `limite_gasto_mensual` a mitad de mes | La entrega se detiene (ver [[CU-113 Entregar un anuncio y medir su desempeño]]), pero la liquidación del consumo ya generado sigue este mismo flujo |
| — | Se detecta un cobro duplicado del proveedor de pago | Se trata igual que cualquier otro pago duplicado: se concilia y se revierte el excedente, no se acredita dos veces |

## Postcondiciones

- Todo gasto publicitario de un período queda facturado y con seguimiento de
  cobro, sin un sistema de facturación paralelo al que ya usa el resto de la
  empresa.

## Contrato · `openapi/publicidad.yaml`

```ts
export const EntradaCU114 = z.object({
  cuentaPublicitariaId: z.string().uuid(),
  periodo: z.string().regex(/^\d{4}-\d{2}$/),
}).strict()

export const SalidaCU114 = z.object({
  facturaPublicidadId: z.string().uuid(),
  montoTotal: MontoSchema,
  cuentaPorCobrarId: z.string().uuid(),
}).strict()

export const ErroresCU114 = {
  SIN_CONSUMO_EN_EL_PERIODO: 'AP-CU114-01',
  PERIODO_YA_LIQUIDADO: 'AP-CU114-02',
  EMISION_FISCAL_FALLIDA: 'AP-CU114-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_CONSUMO_EN_EL_PERIODO` | No hay impresiones ni clics con costo en ese período para esa cuenta |
| `PERIODO_YA_LIQUIDADO` | Ya existe una `factura_publicidad` para esa cuenta y ese período |
| `EMISION_FISCAL_FALLIDA` | El SIN rechazó o no respondió la emisión de `factura_electronica` |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `sumarConsumoDelPeriodo` | Suma costos de impresión y clic por cuenta publicitaria; puro |
| Molécula | `FacturaPublicidadRepositorio` | Alta y transición de estados |
| Molécula | `CuentaPorCobrarRepositorio` (M13) | Alta de la cuenta por cobrar de origen `FACTURA_PUBLICIDAD` |
| Organismo | `CU114LiquidarPublicidad` | Orquesta suma, factura electrónica y cuenta por cobrar en una transacción |
| Página | `apps/backoffice` — liquidaciones de publicidad | Listado por cuenta publicitaria y período, con estado de cobro |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `factura_publicidad.generada` | Dispara la emisión de la factura electrónica | Interno (worker mensual) |
| `factura_publicidad.facturada` | Crea la cuenta por cobrar del período | Interno |

## Interfaz

- **App:** El anunciante ve su historial de facturas y su estado de cobro en su
  panel.
- **Backoffice:** Liquidaciones de publicidad por período, con acceso directo a
  la cuenta por cobrar asociada.

## Restricciones aplicables

`R-AUD-01` · `R-PUB-06`

## Evidencia que deja

[[factura_publicidad]] · [[factura_electronica]] · [[cuenta_por_cobrar]]

## Criterios de aceptación

```gherkin
Dada una cuenta_publicitaria con impresiones y clics con costo en el mes vigente
Cuando corre la liquidación mensual
Entonces se crea factura_publicidad con el monto total y su cuenta_por_cobrar enlazada

Dada una cuenta_publicitaria sin ningún consumo en el mes
Cuando corre la liquidación mensual
Entonces no se genera ninguna factura_publicidad para esa cuenta

Dada una factura_publicidad ya generada para una cuenta y un período
Cuando se intenta liquidar ese mismo período de nuevo
Entonces el sistema devuelve PERIODO_YA_LIQUIDADO
```

## Ver también

[[CU-104 Cobrar una cuenta por cobrar]] · [[CU-113 Entregar un anuncio y medir su desempeño]] · [[CU-32 Emitir factura electrónica]]
