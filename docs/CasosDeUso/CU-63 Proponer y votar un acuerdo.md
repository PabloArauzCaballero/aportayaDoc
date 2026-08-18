---
tags:
  - caso-uso
  - modulo/02-grupos-cupos-turnos-y-gobernanza
codigo: CU-63
criticidad: alta
actores: [Participante proponente, Participantes votantes, Sistema]
normas: [Gobernanza del grupo, debido proceso]
---

# CU-63 — Proponer y votar un acuerdo

> **Objetivo.** Que las decisiones que afectan a todos **no las tome uno solo**.
> Condonar una deuda, expulsar a alguien, cambiar el reglamento o disolver el grupo
> son actos colectivos: se proponen, se votan con quórum y quedan registrados con
> el voto de cada uno.

## Actores y disparador

- **Actor principal:** cualquier participante habilitado (según reglamento).
- **Disparador:** una acción que el sistema clasifica como no unilateral, o una
  propuesta espontánea.

## Precondiciones

1. El [[grupo]] está `EN_CURSO` y tiene `quorum_decisiones` configurado.
2. El proponente es [[participante]] `ACTIVO`.

## Flujo principal

1. Se crea [[acuerdo]] con `tipo` (`CONDONACION`, `EXPULSION`, `PERMUTA`,
   `TRASPASO_CUPO`, `CAMBIO_REGLAMENTO`, `DISOLUCION`, `REPETIR_SORTEO`),
   `referencia_afectada_id`, `descripcion`, `quorum_requerido` copiado del grupo y
   `fecha_limite_votacion`. Estado `EN_VOTACION`.
2. Se notifica a todos los participantes con el plazo.
3. Cada uno emite [[voto_participante]] (`A_FAVOR`, `EN_CONTRA`, `ABSTENCION`).
   **El voto es único por participante y acuerdo** (`R-GRP-08`) y no se puede
   cambiar una vez emitido.
4. El peso del voto es el de sus cupos: quien tiene dos manos pesa doble, quien
   tiene media pesa la mitad. La ponderación se guarda en el voto, no se recalcula.
5. Al alcanzarse el quórum o vencer el plazo, **en la misma transacción**:
   - se computa el resultado sobre los votos emitidos;
   - el acuerdo pasa a `APROBADO` o `RECHAZADO` con `fecha_resolucion`;
   - si fue aprobado, se ejecuta el efecto asociado a su tipo;
   - se emite `evento_dominio` `acuerdo.resuelto`.
6. Se notifica el resultado con el detalle de la votación, visible para el grupo.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | El afectado por el acuerdo vota | Su voto **no cuenta** cuando es parte interesada (expulsión, condonación propia): se registra como `ABSTENCION_FORZADA` |
| 5a | Vence el plazo sin quórum | `RECHAZADO_POR_QUORUM`. El acto no se ejecuta y se puede volver a proponer una sola vez |
| 5b | El efecto falla al ejecutarse | La transacción revierte: el acuerdo no queda aprobado sin su efecto |
| 1a | Ya hay un acuerdo abierto del mismo tipo sobre el mismo objeto | Rechazo: no se vota dos veces lo mismo en paralelo |
| — | Acuerdo de expulsión | Antes de votar, el afectado tiene derecho a [[descargo_participante]] dentro del plazo: **primero se lo escucha** |

## Postcondiciones

- Toda decisión colectiva tiene proponente, votos individuales y resultado.
- Ningún efecto colectivo se ejecutó sin acuerdo aprobado.

## Contrato · `openapi/grupos.yaml`

```ts
export const EntradaCU63 = z.object({
  claveIdempotencia: z.string().uuid(),
  grupoId:  z.string().uuid(),
  tipo:     z.enum(['CONDONACION','EXPULSION','PERMUTA','TRASPASO_CUPO',
                    'CAMBIO_REGLAMENTO','DISOLUCION','REPETIR_SORTEO']),
  referenciaAfectadaId: z.string().uuid().optional(),
  descripcion: z.string().min(20).max(1000),
  diasVotacion: z.number().int().min(1).max(15).default(5),
}).strict()

export const EntradaVotoCU63 = z.object({
  acuerdoId: z.string().uuid(),
  voto:      z.enum(['A_FAVOR', 'EN_CONTRA', 'ABSTENCION']),
}).strict()

export const SalidaCU63 = z.object({
  acuerdoId: z.string().uuid(),
  estado:    z.enum(['EN_VOTACION','APROBADO','RECHAZADO','RECHAZADO_POR_QUORUM']),
  pesoAFavor: z.string(), pesoEnContra: z.string(),
  quorumRequerido: z.string(), fechaLimite: z.string().datetime(),
}).strict()

export const ErroresCU63 = {
  PROPONENTE_NO_HABILITADO: 'AP-CU63-01',
  ACUERDO_DUPLICADO:        'AP-CU63-02',
  VOTO_YA_EMITIDO:          'AP-CU63-03',
  VOTACION_CERRADA:         'AP-CU63-04',
  PARTE_INTERESADA:         'AP-CU63-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `PROPONENTE_NO_HABILITADO` | No es participante activo del grupo o el reglamento reserva ese tipo al organizador |
| `ACUERDO_DUPLICADO` | Ya hay un acuerdo abierto del mismo tipo y objeto en el grupo |
| `VOTO_YA_EMITIDO` | El participante ya votó; el voto no se cambia |
| `VOTACION_CERRADA` | Se votó fuera de plazo o sobre un acuerdo ya resuelto |
| `PARTE_INTERESADA` | El votante es el afectado directo: su voto se registra como `ABSTENCION_FORZADA` y no pondera |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `computarVotacion(votos, quorum)` | Suma ponderada y veredicto; puro y con pruebas de propiedad |
| Átomo | `esParteInteresada(acuerdo, participante)` | Regla de abstención forzada |
| Molécula | `AcuerdoRepositorio` · `VotoRepositorio` | Persistencia |
| Molécula | `EjecutorDeAcuerdo` | Despacha al efecto según `tipo` |
| Organismo | `CU63ResolverAcuerdo` | Transacción: computa, resuelve y ejecuta |
| Página | `POST /acuerdos` · `POST /acuerdos/:id/votos` | |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `acuerdo.propuesto` | Notificación al grupo con plazo | `PARTICIPANTE` |
| `acuerdo.voto_emitido` | Actualización del tablero del grupo | `PARTICIPANTE` |
| `acuerdo.resuelto` | Ejecución del efecto + notificación con el detalle | — |
| — | Trabajo programado que cierra la votación al vencer el plazo | — |

## Interfaz

- **App:** *Grupo → Decisiones*: tarjeta por acuerdo con la propuesta, el plazo, el
  avance del quórum y los botones de voto. El resultado muestra quién votó qué.
- **Backoffice:** auditoría de acuerdos por grupo, con el efecto ejecutado.

## Restricciones aplicables

`R-GRP-08` · `R-GRP-09` · `R-AUD-01` · `R-AUD-04`

## Evidencia que deja

[[acuerdo]] · [[voto_participante]] · [[descargo_participante]] (en expulsiones) ·
`evento_dominio` · [[bitacora_evento]]

## Criterios de aceptación

```gherkin
Dado un acuerdo con quórum de 66% y seis cupos de peso 1
Cuando cuatro votan A_FAVOR
Entonces el acuerdo queda APROBADO y su efecto se ejecuta

Dado un acuerdo de expulsión
Cuando el participante afectado intenta votar
Entonces su voto se registra como ABSTENCION_FORZADA y no pondera

Dado un participante que ya votó
Cuando intenta votar otra vez
Entonces se rechaza con VOTO_YA_EMITIDO

Dado un acuerdo cuyo plazo venció sin quórum
Cuando corre el trabajo de cierre
Entonces queda RECHAZADO_POR_QUORUM y no se ejecuta ningún efecto
```

## Ver también

[[CU-60 Sortear los turnos]] · [[CU-62 Permutar turnos entre participantes]] · [[CU-64 Traspasar un cupo]] · [[CU-67 Disolver el grupo anticipadamente]]
