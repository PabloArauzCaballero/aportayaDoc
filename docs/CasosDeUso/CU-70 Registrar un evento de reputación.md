---
tags:
  - caso-uso
  - modulo/06-transparencia-y-reputacion
codigo: CU-70
criticidad: media
actores: [Sistema]
normas: [Transparencia con el consumidor, trazabilidad]
---

# CU-70 — Registrar un evento de reputación

> **Objetivo.** Que la reputación sea **una consecuencia de hechos registrados**,
> nunca una opinión ni un número que alguien pueda ajustar a mano. Cada punto que
> sube o baja tiene un hecho detrás, con fecha y referencia.

## Actores y disparador

- **Actor principal:** el consumidor de `evento_dominio` (outbox).
- **Disparadores:** aporte pagado a tiempo, aporte en mora, incumplimiento firme,
  cobertura del fondo, deuda saldada, grupo completado, expulsión, reseña recibida.

## Precondiciones

1. Existe la [[regla_impacto_evento]] activa para ese tipo de hecho.
2. El hecho ya ocurrió y está confirmado: **nunca se puntúa una intención**.

## Flujo principal

1. El consumidor toma el evento de dominio y busca su regla de impacto vigente.
2. Se crea [[evento_reputacion]] (*append-only*) con `usuario_id`, `tipo_evento`,
   `puntos`, `peso`, `referencia_tipo`/`referencia_id` y `ocurrido_en`.
3. **La referencia es obligatoria**: todo evento apunta al hecho que lo causó —una
   obligación, un incumplimiento, una entrega—, de modo que el usuario pueda pedir
   el detalle y verlo.
4. Se marca el puntaje del usuario como *sucio* y se encola el recálculo
   ([[CU-71 Recalcular el puntaje de reputación]]).
5. Se notifica solo si el evento es relevante para el usuario: subir de nivel o
   recibir un impacto negativo se avisan; el resto se acumula en silencio.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | No hay regla vigente para el tipo de evento | No se registra nada y se deja aviso: un hecho sin regla es una omisión de configuración, no un cero |
| 2a | El evento llega dos veces (reintento del outbox) | `UNIQUE (referencia_tipo, referencia_id, tipo_evento)` lo impide (`R-REP-01`) |
| — | Se revierte el hecho que lo originó (por ejemplo, se anula la entrega) | **No se borra el evento**: se registra uno compensatorio con `revertido_por_id`, y ambos quedan visibles |
| — | Un evento fue mal calculado por una regla errónea | Se corrige la regla hacia adelante y se emiten compensaciones; el histórico no se reescribe |

## Postcondiciones

- Existe un rastro completo: cada punto tiene su hecho.
- El puntaje quedó marcado para recálculo, no recalculado a mano.

## Contrato · `openapi/transparencia.yaml`

```ts
export const EntradaCU70 = z.object({
  eventoDominioId: z.string().uuid(),
  usuarioId:       z.string().uuid(),
  tipoEvento:      z.string().max(40),
  referenciaTipo:  z.string().max(30),
  referenciaId:    z.string().uuid(),
  ocurridoEn:      z.string().datetime(),
}).strict()

export const SalidaCU70 = z.object({
  eventoReputacionId: z.string().uuid(),
  puntos: z.string(),          // decimal como string, puede ser negativo
  reglaAplicada: z.string(),
  puntajeMarcadoParaRecalculo: z.boolean(),
}).strict()

export const ErroresCU70 = {
  SIN_REGLA_VIGENTE:   'AP-CU70-01',
  EVENTO_DUPLICADO:    'AP-CU70-02',
  REFERENCIA_INVALIDA: 'AP-CU70-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_REGLA_VIGENTE` | No hay regla de impacto para ese `tipo_evento` en la fecha del hecho |
| `EVENTO_DUPLICADO` | Ya existe ese evento para la misma referencia (`R-REP-01`); el reintento del outbox no suma puntos |
| `REFERENCIA_INVALIDA` | `referencia_tipo`/`referencia_id` no apunta a un hecho existente: un evento sin hecho no es auditable |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `aplicarReglaDeImpacto(regla, contexto)` | Devuelve puntos y peso; puro |
| Molécula | `EventoReputacionRepositorio` · `ReglaImpactoRepositorio` | |
| Organismo | `CU70RegistrarEventoReputacion` | Consumidor idempotente del outbox |
| Página | — | No tiene endpoint: **solo se llega por evento de dominio** |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `reputacion.evento_registrado` | Encola el recálculo del puntaje | Ninguno: proceso interno |
| — | Notificación al usuario solo si el impacto es relevante | — |

## Interfaz

- **App:** *Mi reputación → Historial*: lista de hechos con su impacto y el enlace
  al hecho original. Nada de números sin explicación.
- **Backoffice:** consulta por usuario para responder reclamos sobre reputación.

## Restricciones aplicables

`R-REP-01` · `R-AUD-01` · `R-AUD-04`

## Evidencia que deja

[[evento_reputacion]] (*append-only*) · `evento_dominio` consumido

## Criterios de aceptación

```gherkin
Dado un aporte pagado antes del vencimiento
Cuando se procesa el evento de dominio
Entonces existe un evento_reputacion positivo con referencia a la obligación

Dado el mismo evento de dominio reprocesado
Cuando se consume otra vez
Entonces no se crea un segundo evento_reputacion

Dada una entrega anulada que había sumado puntos
Cuando se procesa la reversa
Entonces existe un evento compensatorio con revertido_por_id
Y el evento original sigue existiendo
```

## Ver también

[[CU-71 Recalcular el puntaje de reputación]] · [[CU-74 Otorgar y revocar una insignia]] · [[CU-76 Reseñar a un participante y moderar la reseña]] · [[CU-80 Despachar una notificación]]
