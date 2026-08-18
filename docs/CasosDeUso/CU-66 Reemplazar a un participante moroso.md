---
tags:
  - caso-uso
  - modulo/08-garantia-incumplimiento-cobranza-y-sanciones
  - modulo/02-grupos-cupos-turnos-y-gobernanza
codigo: CU-66
criticidad: alta
actores: [Sistema, Grupo, Candidato, Oficial de cobranza]
normas: [Debido proceso, gobernanza del grupo]
---

# CU-66 — Reemplazar a un participante moroso

> **Objetivo.** Que el grupo pueda seguir funcionando cuando alguien deja de
> aportar, **sin condonarle la deuda y sin saltarse el debido proceso**. La deuda
> sobrevive al reemplazo: cambia quién ocupa el cupo, no quién debe.

## Actores y disparador

- **Actor principal:** el sistema, al consolidarse el incumplimiento.
- **Actores secundarios:** el grupo (vota la expulsión), el candidato entrante, el
  gestor de cobranza.
- **Disparador:** [[registro_incumplimiento]] en estado firme tras vencer el plazo
  de descargo.

## Precondiciones

1. Existe [[registro_incumplimiento]] con evidencia y con el plazo de
   [[descargo_participante]] vencido o resuelto en contra.
2. Se aplicó la [[matriz_sancion]] y la [[sancion]] está `FIRME`.
3. Hay [[acuerdo]] de expulsión aprobado ([[CU-63 Proponer y votar un acuerdo]]).

## Flujo principal

1. Se crea [[reemplazo_participante]] con `participante_saliente_id`, el
   incumplimiento que lo origina y estado `BUSCANDO_CANDIDATO`.
2. Se convoca a candidatos: [[candidato_reemplazo]] desde la lista de espera del
   grupo, de [[postulacion_emparejamiento]] o por invitación del organizador.
3. Cada candidato se evalúa contra `requiere_kyc_minimo` y `reputacion_minima`, y
   ve **el estado real del cupo**: cuántos períodos faltan, qué turno tiene y que
   no hereda la deuda del saliente.
4. Elegido el candidato, **en la misma transacción**:
   - se ejecuta el traspaso ([[CU-64 Traspasar un cupo]]) con
     `motivo = 'REEMPLAZO_POR_INCUMPLIMIENTO'`;
   - el saliente pasa a `EXPULSADO`, no a `RETIRADO`: la diferencia importa para
     su historial;
   - la [[deuda_participante]] se mantiene íntegra, con su [[subrogacion]] al fondo
     si hubo cobertura;
   - la [[gestion_cobranza]] continúa: **expulsar no es perdonar**;
   - se registra [[evento_reputacion]] negativo y entra a
     [[historial_incumplimiento_usuario]];
   - se emite `evento_dominio` `participante.reemplazado`.
5. El grupo recibe el aviso con el nuevo integrante y su turno.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | No aparece candidato en el plazo | Se activa el [[plan_contingencia]]: el fondo cubre los aportes faltantes hasta el cierre, o el grupo vota reducir cupos, o se evalúa [[CU-67 Disolver el grupo anticipadamente]] |
| 3a | Ningún candidato cumple los mínimos | El organizador puede pedir al grupo bajar el requisito por acuerdo, dejando constancia |
| 1a | El moroso se pone al día antes del reemplazo | El proceso se detiene: se cierra el incumplimiento como `REGULARIZADO` y conserva su cupo |
| — | El moroso ya había cobrado su turno | El reemplazo es más urgente: el grupo ya le entregó la bolsa. La deuda pasa a cobranza intensiva y se ejecuta el [[aval_participante]] si existe |
| 4a | El saliente apela la sanción | La [[apelacion_sancion]] no frena el reemplazo, pero si prospera se revierte la reputación y puede haber resarcimiento |

## Postcondiciones

- El cupo tiene titular nuevo y el grupo puede completar el ciclo.
- La deuda del expulsado sigue viva, exigible y en gestión.

## Contrato · `openapi/garantia.yaml`

```ts
export const EntradaCU66 = z.object({
  claveIdempotencia:  z.string().uuid(),
  incumplimientoId:   z.string().uuid(),
  candidatoUsuarioId: z.string().uuid().optional(),   // ausente = solo abrir la búsqueda
}).strict()

export const SalidaCU66 = z.object({
  reemplazoId: z.string().uuid(),
  estado: z.enum(['BUSCANDO_CANDIDATO','CANDIDATO_ELEGIDO','EJECUTADO','SIN_CANDIDATOS']),
  candidatos: z.array(z.object({
    usuarioId: z.string().uuid(), reputacion: z.string(), cumpleRequisitos: z.boolean(),
  })),
  deudaQueQuedaConElSaliente: MontoSchema,
}).strict()

export const ErroresCU66 = {
  INCUMPLIMIENTO_NO_FIRME: 'AP-CU66-01',
  SIN_ACUERDO_DE_EXPULSION:'AP-CU66-02',
  CANDIDATO_NO_ELEGIBLE:   'AP-CU66-03',
  MOROSO_REGULARIZADO:     'AP-CU66-04',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `INCUMPLIMIENTO_NO_FIRME` | El plazo de descargo sigue abierto: no se reemplaza a nadie sobre un incumplimiento discutible |
| `SIN_ACUERDO_DE_EXPULSION` | El reglamento exige acuerdo del grupo y no lo hay |
| `CANDIDATO_NO_ELEGIBLE` | No alcanza el KYC mínimo, la reputación mínima, o ya ocupa un cupo del grupo |
| `MOROSO_REGULARIZADO` | Se puso al día durante el proceso: conserva su cupo y el reemplazo se cierra |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `evaluarCandidato(usuario, grupo)` | Elegibilidad pura |
| Átomo | `deudaQueNoSeTraspasa(obligaciones, coberturas)` | Qué queda con el saliente |
| Molécula | `ReemplazoRepositorio` · `CobranzaRepositorio` | |
| Organismo | `CU66ReemplazarParticipante` | Transacción: expulsión + traspaso + continuidad de cobranza |
| Página | `POST /incumplimientos/:id/reemplazo` | |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `reemplazo.abierto` | Convocatoria a candidatos con plazo | `GRUPO_ADMINISTRAR` |
| `participante.reemplazado` | Evento de reputación, continuidad de cobranza, aviso al grupo | `GRUPO_ADMINISTRAR` |
| — | Trabajo que vence la búsqueda y activa el plan de contingencia | — |

## Interfaz

- **App:** el grupo ve *Se busca reemplazo* con el plazo; los candidatos reciben la
  invitación con el detalle del cupo.
- **Backoffice:** bandeja de reemplazos con el incumplimiento de origen, los
  candidatos y la deuda que queda en gestión.

## Restricciones aplicables

`R-GRP-10` · `R-GRP-11` · `R-AUD-01` · `R-AUD-04`

## Evidencia que deja

[[reemplazo_participante]] · [[candidato_reemplazo]] · [[traspaso_cupo]] ·
[[deuda_participante]] · [[gestion_cobranza]] · [[evento_reputacion]] ·
[[historial_incumplimiento_usuario]]

## Criterios de aceptación

```gherkin
Dado un incumplimiento firme con acuerdo de expulsión aprobado
Cuando se elige un candidato elegible
Entonces el cupo cambia de titular conservando su turno
Y el saliente queda EXPULSADO con su deuda intacta

Dado un moroso que paga antes de que se elija candidato
Cuando corre el proceso
Entonces el reemplazo se cancela con MOROSO_REGULARIZADO

Dado que vence el plazo sin candidatos
Cuando corre el trabajo de vencimiento
Entonces se activa el plan de contingencia del grupo
```

## Ver también

[[CU-23 Cubrir un incumplimiento con el fondo]] · [[CU-25 Declarar el incumplimiento con descargo y evidencia]] · [[CU-26 Ejecutar el aval y subrogar la deuda]] · [[CU-64 Traspasar un cupo]] · [[CU-65 Retirarse de un grupo]] · [[CU-67 Disolver el grupo anticipadamente]] · [[CU-68 Postular a un grupo y ser emparejado]]
