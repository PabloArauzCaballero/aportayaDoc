---
tags:
  - caso-uso
  - modulo/02-grupos-cupos-turnos-y-gobernanza
codigo: CU-65
criticidad: media
actores: [Participante, Grupo, Organizador]
normas: [Consumidor financiero, gobernanza del grupo]
---

# CU-65 — Retirarse de un grupo

> **Objetivo.** Que irse sea posible y ordenado. Un pasanaku no es una prisión,
> pero tampoco una suscripción que se cancela: **lo que ya se recibió se devuelve y
> lo que se aportó se liquida**, y el grupo tiene que poder seguir.

## Actores y disparador

- **Actor principal:** participante.
- **Actores secundarios:** el grupo (aprueba si el reglamento lo exige).
- **Disparador:** solicitud desde la app.

## Precondiciones

1. El [[participante]] está `ACTIVO`.
2. El grupo está `EN_CURSO` (si está `CONFORMADO` y no arrancó, el retiro es
   simple: se libera el cupo y no hay nada que liquidar).

## Flujo principal

1. Se crea [[solicitud_retiro]] con `motivo` y estado `PENDIENTE`.
2. El sistema calcula la **posición del participante**, que es la pregunta que
   define todo:
   - **ya cobró su turno** → debe al grupo los aportes restantes del ciclo:
     posición deudora;
   - **no cobró** → el grupo le debe lo aportado, neto de deuda y recargos:
     posición acreedora.
3. Se muestra el número exacto antes de confirmar, con el desglose línea por línea.
4. Según la posición y el reglamento:
   - **acreedora y hay reemplazo disponible** → [[CU-64 Traspasar un cupo]];
   - **acreedora sin reemplazo** → se liquida al cierre del ciclo, no antes: sacar
     plata de la bolsa a mitad de camino perjudica a los que faltan cobrar;
   - **deudora** → se exige plan de pago o garantía antes de aceptar el retiro.
5. Aprobado, **en la misma transacción**:
   - el participante pasa a `RETIRADO` con `motivo_salida` y `fecha_salida`;
   - el cupo queda `LIBRE` o se traspasa;
   - se crea [[liquidacion_participante]] con el detalle;
   - se registra [[evento_reputacion]] según cómo se fue;
   - se emite `evento_dominio` `participante.retirado`.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 4a | Posición deudora y no acepta plan de pago | El retiro no se aprueba; sigue como participante con sus obligaciones, y si no paga entra el circuito de [[CU-23 Cubrir un incumplimiento con el fondo]] |
| 4b | El grupo rechaza el retiro | Puede insistir una vez; el reglamento define si el retiro unilateral es posible |
| 2a | El grupo no arrancó | Retiro inmediato: se libera el cupo, se devuelve lo aportado íntegro y no hay evento de reputación negativo |
| — | Retiro por fuerza mayor documentada | El grupo puede condonar por [[acuerdo]]; queda registrado el motivo |
| — | El participante deja de responder | No es retiro: es incumplimiento. Va por [[CU-66 Reemplazar a un participante moroso]] |

## Postcondiciones

- La posición económica quedó liquidada o formalizada como deuda con plan.
- El cupo está libre, traspasado o en reemplazo: el grupo puede continuar.

## Contrato · `openapi/grupos.yaml`

```ts
export const EntradaCU65 = z.object({
  claveIdempotencia: z.string().uuid(),
  participanteId:    z.string().uuid(),
  motivo:            z.string().min(10).max(400),
  aceptaPlanDePago:  z.boolean().default(false),
}).strict()

export const SalidaCU65 = z.object({
  solicitudId: z.string().uuid(),
  posicion:    z.enum(['ACREEDORA','DEUDORA','NEUTRA']),
  montoNeto:   MontoSchema,
  desglose:    z.array(z.object({ concepto: z.string(), monto: MontoSchema })),
  momentoDeLiquidacion: z.enum(['INMEDIATO','AL_CIERRE_DEL_CICLO']),
  estado:      z.enum(['PENDIENTE','APROBADA','RECHAZADA','EJECUTADA']),
}).strict()

export const ErroresCU65 = {
  PARTICIPANTE_NO_ACTIVO:  'AP-CU65-01',
  POSICION_DEUDORA_SIN_PLAN:'AP-CU65-02',
  RETIRO_RECHAZADO_POR_GRUPO:'AP-CU65-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `PARTICIPANTE_NO_ACTIVO` | Ya está retirado, expulsado o el cupo cambió de manos |
| `POSICION_DEUDORA_SIN_PLAN` | Cobró su turno y quiere irse sin plan de pago ni garantía |
| `RETIRO_RECHAZADO_POR_GRUPO` | El acuerdo requerido por el reglamento fue rechazado |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularPosicionDeSalida(aportes, cobros, deuda, recargos)` | El número y su desglose; puro, con pruebas de propiedad sobre el cuadre |
| Molécula | `LiquidacionRepositorio` · `ParticipanteRepositorio` | |
| Organismo | `CU65RetirarParticipante` | Transacción y liquidación |
| Página | `POST /participantes/:id/retiro` | |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `retiro.solicitado` | Notificación al grupo y al organizador | `PARTICIPANTE` |
| `participante.retirado` | Liquidación programada al cierre del ciclo si corresponde | `GRUPO_ADMINISTRAR` |

## Interfaz

- **App:** *Mi grupo → Salir*: muestra la posición y el desglose **antes** de
  confirmar, nunca después.
- **Backoffice:** cola de retiros por aprobar, con la posición calculada.

## Restricciones aplicables

`R-GRP-11` · `R-GRP-12` · `R-AUD-05`

## Evidencia que deja

[[solicitud_retiro]] · [[liquidacion_participante]] · [[participante]] ·
[[evento_reputacion]] · [[asiento_contable]] de la liquidación

## Criterios de aceptación

```gherkin
Dado un participante que aún no cobró y aportó Bs 1.500 sin deuda
Cuando solicita el retiro
Entonces la posición es ACREEDORA por Bs 1.500
Y el momento de liquidación es AL_CIERRE_DEL_CICLO

Dado un participante que ya cobró su turno
Cuando solicita el retiro
Entonces la posición es DEUDORA por los aportes restantes
Y sin plan de pago aceptado se rechaza

Dado un grupo que todavía no arrancó
Cuando un participante se retira
Entonces el cupo queda LIBRE y se le devuelve lo aportado íntegro
```

## Ver también

[[CU-29 Devolver los aportes del fondo de garantía]] · [[CU-64 Traspasar un cupo]] · [[CU-66 Reemplazar a un participante moroso]] · [[CU-67 Disolver el grupo anticipadamente]]
