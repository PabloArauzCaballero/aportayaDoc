---
tags:
  - caso-uso
  - modulo/13-contabilidad-erp
codigo: CU-100
criticidad: alta
actores: [Sistema, Contabilidad]
normas: [Ley 393 (libros y conservación), Código de Comercio, NIIF]
---

# CU-100 — Abrir y cerrar el período contable

> **Objetivo.** Que cada mes contable tenga un principio y un fin verificables, y
> que una vez cerrado, nada pueda asentarse retroactivamente en él.

## Actores y disparador

- **Actor principal:** Contabilidad, con ejecución de cierre por el sistema.
- **Disparadores:** alta de un [[ejercicio_fiscal]] al inicio del año; fin de mes
  calendario para el cierre de un [[periodo_contable]].

## Precondiciones

1. Existe un [[ejercicio_fiscal]] en estado `ABIERTO` que contiene el mes a cerrar.
2. El [[periodo_contable]] a cerrar está en estado `ABIERTO` y es el más antiguo
   sin cerrar del ejercicio (no se puede cerrar marzo con febrero todavía abierto).

## Flujo principal

1. Al crear un `ejercicio_fiscal`, el sistema genera sus doce [[periodo_contable]]
   en estado `ABIERTO`, uno por mes.
2. Contabilidad solicita el cierre del período vigente.
3. El sistema calcula `SUM(debe)` y `SUM(haber)` de todos los [[movimiento_contable]]
   cuyo `asiento_contable.periodo_contable_id` sea ese período.
4. **En la misma transacción**: se crea [[cierre_periodo_contable]] con esos
   totales, se marca `periodo_contable.estado = 'CERRADO'`, y se emite
   `evento_dominio`.
5. Si el período cerrado era el último del ejercicio, `ejercicio_fiscal.estado`
   pasa a `CERRADO`.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | El cuadre da `total_debe ≠ total_haber` | El cierre se rechaza: significa que hay un asiento descuadrado, lo que `R-AUD-05` ya debería impedir; se trata como incidente, no como cierre válido |
| 1a | Se intenta cerrar un período que no es el más antiguo abierto | Se rechaza: los períodos se cierran en orden estricto |
| — | Se intenta registrar un [[asiento_contable]] contra un período `CERRADO` | El modelo lo rechaza; la corrección va en el período siguiente, con glosa que referencia el período corregido |
| — | Se intenta "reabrir" un período cerrado | No existe ese caso de uso: `cierre_periodo_contable` es append-only y el cierre es irreversible por diseño |

## Postcondiciones

- Todo asiento con fecha dentro de un período cerrado quedó fijo para siempre.
- `cierre_periodo_contable` prueba, con fecha y responsable, que el período cuadraba
  al momento del cierre.

## Contrato · `openapi/erp.yaml`

```ts
export const EntradaCU100 = z.object({
  periodoContableId: z.string().uuid(),
}).strict()

export const SalidaCU100 = z.object({
  cierreId: z.string().uuid(),
  totalDebe: MontoSchema,
  totalHaber: MontoSchema,
  cerradoEn: z.string().datetime(),
}).strict()

export const ErroresCU100 = {
  PERIODO_YA_CERRADO: 'AP-CU100-01',
  PERIODO_NO_ES_EL_MAS_ANTIGUO: 'AP-CU100-02',
  DESCUADRE_DETECTADO: 'AP-CU100-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `PERIODO_YA_CERRADO` | El período ya tiene un `cierre_periodo_contable` |
| `PERIODO_NO_ES_EL_MAS_ANTIGUO` | Hay un período anterior del mismo ejercicio todavía abierto |
| `DESCUADRE_DETECTADO` | `total_debe ≠ total_haber` al momento de cerrar |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularCuadrePeriodo` | Suma debe/haber del período; puro, con pruebas de propiedad |
| Molécula | `PeriodoContableRepositorio` | Lee/actualiza estado del período y del ejercicio |
| Molécula | `CierrePeriodoRepositorio` | Alta append-only de `cierre_periodo_contable` |
| Organismo | `CU100CerrarPeriodo` | Abre la transacción, valida orden y cuadre, cierra |
| Página | `apps/backoffice` — pantalla de cierre contable | Botón de cierre con doble confirmación, muestra el cuadre antes de confirmar |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `periodo_contable.cerrado` | Bloquea nuevas escrituras contra ese período | `CONTABILIDAD_ERP_CERRAR` |
| `ejercicio_fiscal.cerrado` | Habilita la generación del estado financiero anual | `CONTABILIDAD_ERP_CERRAR` |

## Interfaz

- **App:** No tiene pantalla: es un proceso interno de la operación de la empresa.
- **Backoffice:** Pantalla de cierre contable con el cuadre del mes, historial de
  cierres anteriores y confirmación explícita antes de un acto irreversible.

## Restricciones aplicables

`R-AUD-01` · `R-AUD-05` · `R-AUD-06` · `R-CTB-01`

## Evidencia que deja

[[ejercicio_fiscal]] · [[periodo_contable]] · [[cierre_periodo_contable]]

## Criterios de aceptación

```gherkin
Dado un período contable abierto con todos sus asientos cuadrados
Cuando Contabilidad solicita el cierre
Entonces se crea cierre_periodo_contable con total_debe = total_haber y el período pasa a CERRADO

Dado un período contable ya cerrado
Cuando se intenta registrar un asiento_contable nuevo contra ese período
Entonces el sistema lo rechaza

Dados dos períodos abiertos consecutivos del mismo ejercicio
Cuando se intenta cerrar el segundo antes que el primero
Entonces el sistema devuelve PERIODO_NO_ES_EL_MAS_ANTIGUO
```

## Ver también

[[CU-101 Presupuestar por centro de costo]] · [[CU-105 Depreciar un activo fijo]] · [[CU-106 Generar el estado financiero del período]]
