---
tags:
  - caso-uso
  - modulo/06-transparencia-y-reputacion
codigo: CU-76
criticidad: media
actores: [Participante, Moderador, Sistema]
normas: [Protección de datos, no discriminación arbitraria, ASFI Consumidor Financiero]
---

# CU-76 — Reseñar a un participante y moderar la reseña

> **Objetivo.** Que la experiencia real de convivir en un grupo entre en la
> reputación, sin convertir la plataforma en un lugar donde se pueda dañar a alguien
> gratis o vengarse de una expulsión.

## Actores y disparador

- **Actor principal:** participante del mismo grupo.
- **Disparadores:** cierre del ciclo del grupo; salida de un participante; solicitud
  del moderador tras un reporte.

## Precondiciones

1. Autor y evaluado **compartieron grupo y período**: no se reseña a quien no se
   conoce (`R-REP-06`).
2. El ciclo cerró o el evaluado salió: **reseñar en medio del ciclo presiona el
   comportamiento de pago**, y esa presión no es información, es coerción.
3. Existe política de moderación publicada, con qué se rechaza y por qué.

## Flujo principal

1. Se crea [[resena_participante]] con `grupo_id`, `autor_participante_id`,
   `evaluado_usuario_id`, `calificacion` de 1 a 5, `dimension` —`PUNTUALIDAD`,
   `COMUNICACION`, `COLABORACION`— y `comentario` opcional, en
   `estado_moderacion = 'PENDIENTE'`.
2. **Una reseña por autor, evaluado, grupo y dimensión** (`R-REP-06`): no se acumula
   opinión repitiendo el formulario.
3. La moderación automática marca lo que tiene datos personales, insultos o
   acusaciones de delito. Lo marcado pasa a revisión humana, no se publica ni se
   descarta solo.
4. El moderador aprueba, rechaza o publica sin comentario —conservando la
   calificación numérica—, con `moderada_por`. **El rechazo se le explica al autor**:
   moderar en silencio es lo mismo que censurar.
5. Aprobada, alimenta el factor de reseñas del modelo de reputación
   ([[CU-71 Recalcular el puntaje de reputación]]) con **peso acotado**: la opinión
   pesa menos que el hecho de haber pagado. Los datos duros mandan.
6. El evaluado ve sus reseñas, puede responder una vez cada una, y puede reportarlas.
   No puede borrarlas.
7. Las reseñas son visibles dentro del ámbito del grupo y, agregadas, en el perfil.
   **El comentario individual nunca se muestra fuera del grupo** ni entra al
   certificado de reputación.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | Autor y evaluado nunca compartieron período | Rechazo `SIN_CONVIVENCIA` |
| 2a | El autor fue expulsado del grupo | Puede reseñar, pero la reseña se marca con esa condición y **pesa menos**: el contexto es información, no una excusa para silenciarlo |
| 3a | El comentario contiene un teléfono o un documento | Se retiene y se publica sin el dato, avisando al autor |
| 4a | Reseña rechazada | Se explica el motivo y se permite reescribirla una vez |
| 5a | Un evaluado recibe muchas reseñas negativas del mismo grupo tras un conflicto | El patrón se detecta y el conjunto pasa a revisión: una pelea grupal no es una evaluación |
| 6a | El evaluado reporta una reseña como falsa | Va a moderación con la reseña despublicada mientras se resuelve |
| — | El evaluado ejerce derecho de supresión | Sus reseñas recibidas se anonimizan; las que él escribió también |
| — | Reseña sobre un hecho que corresponde a un reclamo | Se deriva a [[CU-52 Atender un reclamo en plazo]]: una reseña no reemplaza un canal formal |

## Postcondiciones

- Toda reseña publicada tiene convivencia comprobada y pasó por moderación.
- Ninguna reseña individual sale del grupo, y ninguna borra la posibilidad de
  respuesta del evaluado.

## Contrato · `openapi/transparencia.yaml`

```ts
export const EntradaCU76 = z.object({
  grupoId: z.string().uuid(),
  evaluadoUsuarioId: z.string().uuid(),
  calificacion: z.number().int().min(1).max(5),
  dimension: z.enum(['PUNTUALIDAD','COMUNICACION','COLABORACION']),
  comentario: z.string().max(500).nullable(),
}).strict()

export const EntradaModerarCU76 = z.object({
  resenaId: z.string().uuid(),
  decision: z.enum(['APROBAR','RECHAZAR','PUBLICAR_SIN_COMENTARIO']),
  motivo: z.string().min(10).max(300),
}).strict()

export const SalidaCU76 = z.object({
  resenaId: z.string().uuid(),
  estadoModeracion: z.enum(['PENDIENTE','APROBADA','RECHAZADA','SIN_COMENTARIO']),
  pesoEnReputacion: z.string(),
  motivoModeracion: z.string().nullable(),
}).strict()

export const ErroresCU76 = {
  SIN_CONVIVENCIA:   'AP-CU76-01',
  CICLO_EN_CURSO:    'AP-CU76-02',
  RESENA_DUPLICADA:  'AP-CU76-03',
  AUTORRESENA:       'AP-CU76-04',
  CONTENIDO_RECHAZADO:'AP-CU76-05',
  PLAZO_VENCIDO:     'AP-CU76-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_CONVIVENCIA` | Autor y evaluado no compartieron grupo y período (`R-REP-06`) |
| `CICLO_EN_CURSO` | El grupo sigue activo y el evaluado también: se reseña al cerrar |
| `RESENA_DUPLICADA` | Ya reseñó a esa persona en ese grupo y dimensión |
| `AUTORRESENA` | Se intenta reseñar a sí mismo |
| `CONTENIDO_RECHAZADO` | La moderación rechazó el comentario; se explica el motivo |
| `PLAZO_VENCIDO` | Pasó la ventana para reseñar tras el cierre del ciclo |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `compartieronPeriodo(autor, evaluado, grupo)` | Verifica convivencia; puro |
| Átomo | `pesoDeResena(autor, contexto)` | Ajusta por expulsión, antigüedad y volumen; puro |
| Átomo | `detectarDatosPersonales(texto)` | Marca teléfonos, documentos y correos; puro |
| Molécula | `ResenaRepositorio` | Persistencia y unicidad por autor, evaluado, grupo y dimensión |
| Molécula | `ModeradorAutomatico` | Primera pasada, siempre con revisión humana detrás |
| Organismo | `CU76PublicarResena` | Transacción: estado, evento de reputación y notificación |
| Página | `POST /grupos/:id/resenas` · `POST /resenas/:id/moderacion` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `resena.creada` | Moderación automática y, si marca, cola humana | `PARTICIPANTE` |
| `resena.publicada` | Evento de reputación con peso acotado | `MODERACION` |
| `resena.rechazada` | Aviso al autor con el motivo | `MODERACION` |
| — | Trabajo que abre la ventana de reseñas al cerrar el ciclo y la cierra al vencer | — |

## Interfaz

- **App:** al cerrar el grupo, una pantalla breve con las tres dimensiones y un
  comentario opcional. En *Mi perfil*, las reseñas recibidas con el botón de
  responder y el de reportar.
- **Backoffice:** cola de moderación con el motivo del marcado y el historial del
  autor, y detección de conjuntos sospechosos por conflicto grupal.

## Restricciones aplicables

`R-REP-01` · `R-REP-03` · `R-REP-06` · `R-SEG-03` · `R-AUD-01` · `R-AUD-04`

## Evidencia que deja

[[resena_participante]] · [[evento_reputacion]] · [[componente_score]] ·
[[bitacora_evento]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dado un autor que compartió período con el evaluado y un ciclo cerrado
Cuando publica una reseña de dimensión PUNTUALIDAD
Entonces queda PENDIENTE de moderación y no impacta la reputación todavía

Dado un autor que nunca compartió grupo con el evaluado
Cuando intenta reseñarlo
Entonces se rechaza con SIN_CONVIVENCIA

Dado un comentario que incluye un número de teléfono
Cuando pasa la moderación automática
Entonces se retiene para revisión humana y no se publica con el dato

Dada una reseña aprobada
Cuando se recalcula la reputación
Entonces su peso es menor que el de los factores de pago
```

## Ver también

[[CU-70 Registrar un evento de reputación]] · [[CU-71 Recalcular el puntaje de reputación]] · [[CU-74 Otorgar y revocar una insignia]] · [[CU-52 Atender un reclamo en plazo]]
