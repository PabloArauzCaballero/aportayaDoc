---
tags:
  - caso-uso
  - modulo/02-grupos-cupos-turnos-y-gobernanza
codigo: CU-62
criticidad: media
actores: [Participante solicitante, Participante contraparte, Organizador]
normas: [Transparencia, gobernanza del grupo]
---

# CU-62 — Permutar turnos entre participantes

> **Objetivo.** Que dos personas puedan intercambiar su lugar en el calendario
> **con acuerdo explícito de ambas y a la vista del grupo**, sin que eso rompa la
> aritmética de la bolsa ni permita comprar el primer turno por lo bajo.

## Actores y disparador

- **Actor principal:** participante que necesita cobrar antes (o después).
- **Actor secundario:** la contraparte, que debe aceptar; el organizador, que
  ejecuta si el reglamento lo exige.
- **Disparador:** solicitud desde la app.

## Precondiciones

1. Ambos [[turno]] están en estado `PROGRAMADO`: **un turno ya cobrado o en curso
   no se permuta** (`R-GRP-07`).
2. Ninguno de los dos participantes está en mora ni tiene [[deuda_participante]]
   vigente.
3. El [[reglamento_grupo]] permite permutas.

## Flujo principal

1. Se crea [[solicitud_permuta]] con `turno_origen_id`, `turno_destino_id`,
   `solicitante_id`, `contraparte_id` y `motivo`, en estado `PENDIENTE`.
2. Se notifica a la contraparte, que acepta o rechaza. Sin aceptación no hay nada.
3. Si el reglamento exige aprobación colectiva, se abre un [[acuerdo]]
   ([[CU-63 Proponer y votar un acuerdo]]) y se espera el quórum.
4. Aprobada, **en la misma transacción**:
   - se intercambian `orden_asignado` y `periodo_id` entre los dos turnos;
   - cada turno registra `permutado_con_turno_id` apuntando al otro;
   - `criterio_asignacion` pasa a `PERMUTA` en ambos;
   - se recalculan las [[entrega_fondo]] programadas de los períodos afectados;
   - se emite `evento_dominio` `turnos.permutados`.
5. Se notifica a **todo el grupo**, no solo a los dos implicados: el calendario es
   información común.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | La contraparte rechaza o no responde en el plazo | La solicitud caduca; nada cambia |
| 1a | Alguno de los turnos ya fue cobrado | Rechazo por `R-GRP-07`: el pasado no se reordena |
| 2b | El solicitante tiene deuda | Rechazo: **primero se pone al día**; si no, permutar sería adelantar el cobro de un moroso |
| 3a | El acuerdo no alcanza quórum | La permuta no se ejecuta y queda el registro de la votación |
| — | Se detecta pago entre participantes por la permuta | El reglamento lo prohíbe; si aparece, es un [[evento_reputacion]] negativo y puede escalar a [[CU-44 De alerta de monitoreo a reporte de operación sospechosa]] |

## Postcondiciones

- Los dos turnos intercambiaron posición y cada uno apunta al otro.
- La suma de turnos por período no cambió: sigue habiendo uno por período.

## Contrato · `openapi/grupos.yaml`

```ts
export const EntradaCU62 = z.object({
  claveIdempotencia: z.string().uuid(),
  turnoOrigenId:     z.string().uuid(),
  turnoDestinoId:    z.string().uuid(),
  motivo:            z.string().min(10).max(300),
}).strict()

export const SalidaCU62 = z.object({
  solicitudId: z.string().uuid(),
  estado:      z.enum(['PENDIENTE', 'ACEPTADA', 'RECHAZADA', 'EJECUTADA']),
  requiereAcuerdo: z.boolean(),
}).strict()

export const ErroresCU62 = {
  TURNO_NO_PERMUTABLE:   'AP-CU62-01',
  SOLICITANTE_EN_MORA:   'AP-CU62-02',
  CONTRAPARTE_EN_MORA:   'AP-CU62-03',
  REGLAMENTO_NO_PERMITE: 'AP-CU62-04',
  ACUERDO_SIN_QUORUM:    'AP-CU62-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `TURNO_NO_PERMUTABLE` | El turno ya fue cobrado o está en curso (`R-GRP-07`) |
| `SOLICITANTE_EN_MORA` | Quien pide la permuta tiene deuda vencida: primero se pone al día |
| `CONTRAPARTE_EN_MORA` | La contraparte tiene deuda vencida y aceptar adelantaría su cobro |
| `REGLAMENTO_NO_PERMITE` | El reglamento del grupo prohíbe permutar, o lo prohíbe en esta etapa |
| `ACUERDO_SIN_QUORUM` | El reglamento exigía votación y venció el plazo sin alcanzarla |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `puedePermutar(turnoA, turnoB, deudas)` | Devuelve el motivo del rechazo o `null`; puro |
| Molécula | `TurnoRepositorio` · `SolicitudPermutaRepositorio` | Lectura y escritura |
| Organismo | `CU62PermutarTurnos` | Transacción, intercambio y reprogramación |
| Página | `POST /turnos/permutas` · `POST /turnos/permutas/:id/aceptar` | |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `permuta.solicitada` | Notificación a la contraparte con plazo | `PARTICIPANTE` |
| `turnos.permutados` | Notificación al grupo + recálculo del calendario | `GRUPO_ADMINISTRAR` si el reglamento lo exige |

## Interfaz

- **App:** *Mi turno* → **Proponer cambio**, elegir con quién y por qué; la
  contraparte lo ve como tarjeta con Aceptar / Rechazar.
- **Backoffice:** listado de permutas por grupo, con quién pidió y quién aprobó.

## Restricciones aplicables

`R-GRP-06` · `R-GRP-07` · `R-AUD-04`

## Evidencia que deja

[[solicitud_permuta]] · [[turno]] (ambos, con `permutado_con_turno_id`) ·
[[acuerdo]] y [[voto_participante]] si hubo votación · `evento_dominio`

## Criterios de aceptación

```gherkin
Dados dos turnos PROGRAMADOS y ambos participantes al día
Cuando la contraparte acepta la permuta
Entonces los dos turnos intercambian orden_asignado y periodo_id
Y cada uno referencia al otro en permutado_con_turno_id

Dado un turno ya COBRADO
Cuando se intenta permutarlo
Entonces la operación se rechaza con TURNO_NO_PERMUTABLE

Dado un solicitante con deuda vigente
Cuando propone la permuta
Entonces se rechaza con SOLICITANTE_EN_MORA
```

## Ver también

[[CU-60 Sortear los turnos]] · [[CU-63 Proponer y votar un acuerdo]] · [[CU-22 Liquidar y entregar el fondo]]
