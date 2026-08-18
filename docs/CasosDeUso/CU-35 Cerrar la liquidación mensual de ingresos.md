---
tags:
  - caso-uso
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
codigo: CU-35
criticidad: media
actores: [Contabilidad]
normas: [Contabilidad, tributario]
---

# CU-35 — Cerrar la liquidación mensual de ingresos

> **Objetivo.** Saber cuánto ganó realmente la plataforma en el mes —neto de
> exenciones, devoluciones, incobrables, impuestos y costo de proveedores— y que
> ese número **cuadre contra el mayor**.

## Actores y disparador

- **Actor principal:** contabilidad.
- **Disparador:** cierre del período mensual.

## Precondiciones

1. Todos los [[cierre_diario]] del mes están cuadrados ([[CU-51 Ejecutar el cierre diario]]).
2. No hay [[excepcion_conciliacion]] abiertas del período.

## Flujo principal

1. Se agregan los [[devengo_comision]] del `periodo_contable`: total devengado,
   cobrado, exonerado, devuelto e incobrable.
2. Se agregan los [[calculo_impuesto]] del período y los
   [[costo_proveedor_operacion]].
3. Se crea [[liquidacion_ingresos]] con `ingreso_neto` (columna generada) y
   `cantidad_operaciones`.
4. Se contrasta `total_cobrado` contra el saldo de la cuenta de ingresos del mayor.
   **Si no coincide, no se cierra.**
5. Se genera el [[asiento_contable]] de cierre del período y se enlaza.
6. Se marca `estado='CERRADA'` con `cerrada_por` y `cerrada_en`.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 4a | Diferencia contra el mayor | Se abre [[excepcion_conciliacion]] o [[hallazgo_auditoria]]; el período queda abierto |
| — | Reapertura de un período cerrado | Requiere autorización registrada; queda `reabierto` y auditado |
| 1a | Devengos de meses anteriores que se cobran ahora | Se imputan al período de devengo, no al de cobro (criterio devengado) |
| — | Un devengo se declara incobrable después del cierre | Se registra en el período corriente como pérdida, con su asiento; el mes cerrado no se toca |
| 2a | Faltan cierres diarios dentro del mes | El mes no cierra: la liquidación mensual se apoya en días cuadrados, no los reemplaza |

## Postcondiciones

- Existe un resultado mensual reproducible desde los devengos, no desde una
  planilla aparte.

## Contrato · `openapi/tarifas.yaml`

```ts
export const EntradaCU35 = z.object({
  periodo: z.string().regex(/^\d{4}-\d{2}$/),
  cerradaPor: z.string().uuid(),
}).strict()

export const SalidaCU35 = z.object({
  liquidacionId: z.string().uuid(),
  totalDevengado: MontoSchema,
  totalCobrado: MontoSchema,
  totalDevuelto: MontoSchema,
  totalImpuestos: MontoSchema,
  ingresoNeto: MontoSchema,
  cuadraContraMayor: z.boolean(),
}).strict()

export const ErroresCU35 = {
  DIAS_SIN_CERRAR: 'AP-CU35-01',
  EXCEPCIONES_ABIERTAS: 'AP-CU35-02',
  NO_CUADRA_CONTRA_MAYOR: 'AP-CU35-03',
  PERIODO_YA_CERRADO: 'AP-CU35-04',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `DIAS_SIN_CERRAR` | Hay cierres diarios pendientes del período |
| `EXCEPCIONES_ABIERTAS` | Quedan excepciones de conciliación sin resolver |
| `NO_CUADRA_CONTRA_MAYOR` | La liquidación difiere del saldo de la cuenta de ingresos |
| `PERIODO_YA_CERRADO` | Requiere reapertura autorizada |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `consolidarDevengos` | Agrega por estado y período; puro |
| Átomo | `compararConMayor` | Contrasta el total cobrado con el saldo contable |
| Molécula | `LiquidacionIngresosRepositorio` | Consolidación mensual |
| Molécula | `AsientoRepositorio` | Asiento de cierre del período |
| Organismo | `CU35CerrarLiquidacion` | Transacción: consolidación, cuadre y asiento de cierre |
| Página | `POST /contabilidad/liquidaciones/:periodo/cierre` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `liquidacion.cerrada` | Publicación del resultado del período | `CONTABILIDAD` |
| `liquidacion.descuadrada` | Hallazgo de auditoría y bloqueo del cierre | — |

## Interfaz

- **App:** Sin pantalla en la app: es información interna.
- **Backoffice:** Cierre mensual con el cuadre a la vista; no se puede confirmar si no cuadra.

## Restricciones aplicables

`R-AUD-05` · `R-AUD-06` · `R-BIL-12`

## Evidencia que deja

[[liquidacion_ingresos]] · [[asiento_contable]] · [[costo_proveedor_operacion]]

## Criterios de aceptación

```gherkin
Dado un mes con todos los cierres diarios cuadrados
Cuando se cierra la liquidación
Entonces total_cobrado coincide con el saldo de la cuenta de ingresos

Dada una diferencia entre la liquidación y el mayor
Cuando se intenta cerrar
Entonces el cierre se rechaza y queda un hallazgo abierto

Dado un mes con un cierre diario faltante
Cuando se intenta cerrar la liquidación
Entonces el cierre se rechaza y el mes queda abierto

Dada una liquidación mensual ya cerrada
Cuando se reintenta el cierre con la misma clave de idempotencia
Entonces se devuelve la liquidación existente y no se duplican asientos
```

## Ver también

[[CU-24 Registrar el asiento contable de una operación]] · [[CU-31 Devengar y cobrar la comisión]] · [[CU-51 Ejecutar el cierre diario]]
