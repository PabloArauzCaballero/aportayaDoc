---
tags:
  - caso-uso
  - modulo/13-contabilidad-erp
codigo: CU-106
criticidad: alta
actores: [Sistema, Contabilidad]
normas: [NIIF, Código de Comercio, Ley 393]
---

# CU-106 — Generar el estado financiero del período

> **Objetivo.** Que "el balance de marzo" sea una fotografía guardada,
> reproducible y demostrable — no un cálculo que puede dar distinto cada vez
> que alguien lo pide.

## Actores y disparador

- **Actor principal:** el sistema, con solicitud de Contabilidad.
- **Disparadores:** cierre de un [[periodo_contable]] (ver
  [[CU-100 Abrir y cerrar el período contable]]); necesidad de un asiento
  recurrente antes del cierre.

## Precondiciones

1. El [[periodo_contable]] a reportar existe y, para el estado financiero
   definitivo, está `CERRADO`.
2. Si se usa una [[asiento_plantilla]], está `activa` y tiene al menos una
   [[linea_plantilla_asiento]].

## Flujo principal

1. (Opcional, antes del cierre) Contabilidad genera un asiento recurrente desde
   una `asiento_plantilla`: el sistema crea el [[asiento_contable]] con las
   [[linea_plantilla_asiento]] como partidas, tomando `monto_referencial` o el
   monto que se indique.
2. Al cerrar el período, Contabilidad solicita el estado financiero.
3. El sistema recorre el [[cuenta_contable]] jerárquico (por `cuenta_padre_id`,
   sumarizando hacia arriba) y calcula los saldos de ese período.
4. Se crea [[estado_financiero_generado]] con `tipo` (`BALANCE_GENERAL` o
   `ESTADO_RESULTADOS`), los `datos` calculados en JSON, y `hash_contenido`.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El período todavía está `ABIERTO` | Se puede generar un estado financiero **provisorio**, marcado como tal en `datos`, pero no reemplaza el definitivo post-cierre |
| — | Ya existe un estado financiero de ese tipo para ese período | `UQ(periodo_contable_id, tipo)`: no se regenera silenciosamente; una corrección explícita crea un registro nuevo con nota, nunca edita el existente (`R-AUD-01`) |
| 1a | La plantilla referencia una cuenta contable inactiva o `es_cuenta_de_movimiento = false` | El asiento recurrente se rechaza antes de crearse |
| — | Se pide comparar dos estados financieros de períodos distintos | Se leen ambos snapshots ya guardados; no se recalculan |

## Postcondiciones

- Cada estado financiero generado es inmutable y verificable después con su
  `hash_contenido`, igual que [[registro_sellado]] en el módulo 09.

## Contrato · `openapi/erp.yaml`

```ts
export const EntradaCU106 = z.object({
  periodoContableId: z.string().uuid(),
  tipo: z.enum(['BALANCE_GENERAL', 'ESTADO_RESULTADOS']),
}).strict()

export const SalidaCU106 = z.object({
  estadoFinancieroId: z.string().uuid(),
  hashContenido: z.string(),
  generadoEn: z.string().datetime(),
}).strict()

export const ErroresCU106 = {
  YA_GENERADO_PARA_ESE_PERIODO: 'AP-CU106-01',
  CUENTA_PLANTILLA_INVALIDA: 'AP-CU106-02',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `YA_GENERADO_PARA_ESE_PERIODO` | Ya existe un `estado_financiero_generado` de ese `tipo` para ese período |
| `CUENTA_PLANTILLA_INVALIDA` | Una `linea_plantilla_asiento` apunta a una cuenta inactiva o sumarizadora |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `sumarizarPlanDeCuentas` | Suma saldos por rama del árbol de `cuenta_contable`; puro |
| Molécula | `AsientoPlantillaRepositorio` | Lee plantillas y sus líneas |
| Molécula | `EstadoFinancieroRepositorio` | Alta append-only del snapshot |
| Organismo | `CU106GenerarEstadoFinanciero` | Orquesta cálculo y sellado con hash |
| Página | `apps/backoffice` — estados financieros | Balance y resultados por período, con comparación entre períodos |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `estado_financiero.generado` | Disponible para consulta y exportación | `CONTABILIDAD_ERP_REPORTES` |
| `asiento_plantilla.aplicada` | Genera el asiento recurrente del período | `CONTABILIDAD_ERP_CERRAR` |

## Interfaz

- **App:** No tiene pantalla.
- **Backoffice:** Balance general y estado de resultados por período, con
  exportación registrada (mismo patrón que `web-backoffice`).

## Restricciones aplicables

`R-AUD-01` · `R-AUD-05` · `R-CTB-02` · `R-CTB-08`

## Evidencia que deja

[[asiento_plantilla]] · [[linea_plantilla_asiento]] · [[estado_financiero_generado]]

## Criterios de aceptación

```gherkin
Dado un período contable cerrado sin estado financiero previo
Cuando Contabilidad solicita el BALANCE_GENERAL de ese período
Entonces se crea estado_financiero_generado con su hash_contenido

Dado un estado financiero ya generado para un período y tipo
Cuando se solicita generarlo de nuevo
Entonces el sistema devuelve YA_GENERADO_PARA_ESE_PERIODO

Dada una asiento_plantilla activa con dos líneas balanceadas
Cuando se aplica antes del cierre del período
Entonces se crea un asiento_contable cuadrado con esas partidas
```

## Ver también

[[CU-100 Abrir y cerrar el período contable]]
