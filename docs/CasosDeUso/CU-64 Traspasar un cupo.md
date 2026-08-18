---
tags:
  - caso-uso
  - modulo/02-grupos-cupos-turnos-y-gobernanza
codigo: CU-64
criticidad: alta
actores: [Participante saliente, Participante entrante, Grupo]
normas: [UIF (alta del entrante), gobernanza del grupo]
---

# CU-64 — Traspasar un cupo a otra persona

> **Objetivo.** Que alguien pueda salirse **sin romper el grupo**: el cupo conserva
> su posición económica en el calendario y entra otra persona en su lugar, con las
> mismas obligaciones y el mismo turno.

## Actores y disparador

- **Actor principal:** participante saliente.
- **Actores secundarios:** participante entrante; el grupo, que aprueba.
- **Disparador:** solicitud del saliente, o consecuencia de
  [[CU-66 Reemplazar a un participante moroso]].

## Precondiciones

1. El [[cupo]] está `OCUPADO` y su [[turno]] **aún no fue cobrado**.
2. El saliente no tiene [[deuda_participante]] ni [[obligacion_aporte]] vencida, o
   la salda como parte del traspaso.
3. El entrante pasó [[CU-01 Registro y apertura de billetera]] y cumple
   `requiere_kyc_minimo` y `reputacion_minima` del grupo.
4. Hay [[acuerdo]] aprobado si el reglamento lo exige (`R-GRP-10`).

## Flujo principal

1. Se crea [[traspaso_cupo]] con `cupo_id`, `participante_origen_id`,
   `participante_destino_id`, `motivo` y `aprobado_por_acuerdo_id`, en estado
   `PROPUESTO`.
2. El entrante acepta: firma [[aceptacion_reglamento]] y el contrato del grupo, y
   ve el estado exacto del cupo — cuánto se aportó, qué turno tiene, qué debe.
3. Se vota si corresponde ([[CU-63 Proponer y votar un acuerdo]]).
4. **En la misma transacción**:
   - el cupo cambia de `participante_id`;
   - el saliente pasa a `RETIRADO` con `motivo_salida` y `fecha_salida`;
   - el entrante se crea o activa como [[participante]];
   - las [[obligacion_aporte]] **futuras** se reasignan al entrante; las **vencidas
     quedan con el saliente** (`R-GRP-11`): la deuda no se traspasa con el cupo;
   - el [[turno]] no se toca: **la posición en el calendario es del cupo, no de la
     persona**;
   - se registra [[evento_reputacion]] para ambos;
   - se emite `evento_dominio` `cupo.traspasado`.
5. Se notifica al grupo con el cambio de integrante.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El entrante no cumple el KYC mínimo del grupo | Rechazo: se le indica qué le falta y puede elevarlo ([[CU-02 Elevar nivel de debida diligencia]]) |
| 1a | El turno del cupo ya fue cobrado | Se puede traspasar igual, pero el entrante hereda **solo las obligaciones**, no un cobro futuro; se le muestra explícitamente antes de aceptar |
| 4a | El saliente tiene deuda y no la salda | El traspaso no procede; o la paga, o se resuelve por [[CU-66 Reemplazar a un participante moroso]] |
| 3a | El grupo rechaza el traspaso | El cupo sigue con el saliente y su obligación de aportar continúa |
| — | Traspaso entre familiares directos | No hay regla especial, pero suma al factor de riesgo si genera concentración: lo evalúa [[CU-44 De alerta de monitoreo a reporte de operación sospechosa]] |

## Postcondiciones

- El cupo tiene un titular nuevo, su turno intacto y sus obligaciones futuras
  reasignadas.
- La deuda del saliente sigue siendo del saliente.

## Contrato · `openapi/grupos.yaml`

```ts
export const EntradaCU64 = z.object({
  claveIdempotencia: z.string().uuid(),
  cupoId:            z.string().uuid(),
  usuarioEntranteId: z.string().uuid(),
  motivo:            z.string().min(10).max(300),
}).strict()

export const SalidaCU64 = z.object({
  traspasoId: z.string().uuid(),
  estado:     z.enum(['PROPUESTO','ACEPTADO','APROBADO','EJECUTADO','RECHAZADO']),
  requiereAcuerdo:  z.boolean(),
  deudaPendienteSaliente: MontoSchema,
  obligacionesReasignadas: z.number().int(),
}).strict()

export const ErroresCU64 = {
  CUPO_NO_TRASPASABLE:    'AP-CU64-01',
  SALIENTE_CON_DEUDA:     'AP-CU64-02',
  ENTRANTE_SIN_KYC:       'AP-CU64-03',
  ENTRANTE_SIN_REPUTACION:'AP-CU64-04',
  ACUERDO_REQUERIDO:      'AP-CU64-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `CUPO_NO_TRASPASABLE` | El grupo está suspendido, en liquidación o el reglamento no admite traspasos |
| `SALIENTE_CON_DEUDA` | Hay obligaciones vencidas impagas: la deuda no viaja con el cupo (`R-GRP-11`) |
| `ENTRANTE_SIN_KYC` | El entrante no alcanza el `requiere_kyc_minimo` del grupo |
| `ENTRANTE_SIN_REPUTACION` | El entrante no alcanza la `reputacion_minima` fijada en el reglamento |
| `ACUERDO_REQUERIDO` | El reglamento exige aprobación del grupo y no hay acuerdo aprobado |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `separarObligaciones(obligaciones, fechaCorte)` | Devuelve las que van al entrante y las que quedan con el saliente; puro |
| Átomo | `cumpleRequisitosDeIngreso(usuario, grupo)` | KYC y reputación mínimos |
| Molécula | `CupoRepositorio` · `ParticipanteRepositorio` · `ObligacionRepositorio` | |
| Organismo | `CU64TraspasarCupo` | Transacción y reasignación |
| Página | `POST /cupos/:id/traspasos` | |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `traspaso.propuesto` | Invitación al entrante + notificación al grupo | `PARTICIPANTE` |
| `cupo.traspasado` | Recálculo de la bolsa esperada + evento de reputación de ambos | `GRUPO_ADMINISTRAR` |

## Interfaz

- **App:** *Mi cupo → Traspasar*; el entrante recibe una invitación con el detalle
  completo del cupo antes de aceptar.
- **Backoffice:** historial de traspasos por grupo, con quién salió, quién entró y
  qué deuda quedó pendiente.

## Restricciones aplicables

`R-GRP-10` · `R-GRP-11` · `R-UIF-09` · `R-AUD-04`

## Evidencia que deja

[[traspaso_cupo]] · [[cupo]] · [[participante]] (saliente y entrante) ·
[[obligacion_aporte]] reasignadas · [[acuerdo]] · [[evento_reputacion]]

## Criterios de aceptación

```gherkin
Dado un cupo con turno futuro y su titular al día
Cuando se ejecuta el traspaso al entrante
Entonces el cupo cambia de participante y el turno conserva su orden
Y las obligaciones vencidas siguen apuntando al saliente

Dado un saliente con deuda vigente
Cuando intenta traspasar
Entonces se rechaza con SALIENTE_CON_DEUDA

Dado un entrante con KYC por debajo del mínimo del grupo
Cuando acepta la invitación
Entonces se rechaza con ENTRANTE_SIN_KYC y se le ofrece elevar su nivel
```

## Ver también

[[CU-63 Proponer y votar un acuerdo]] · [[CU-65 Retirarse de un grupo]] · [[CU-66 Reemplazar a un participante moroso]] · [[CU-68 Postular a un grupo y ser emparejado]]
