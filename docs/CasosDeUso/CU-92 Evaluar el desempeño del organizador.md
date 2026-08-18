---
tags:
  - caso-uso
  - modulo/07-organizador-y-automatizacion
codigo: CU-92
criticidad: media
actores: [Sistema, Organizador, Operaciones]
normas: [Gobernanza, ASFI Consumidor Financiero, no discriminación arbitraria]
---

# CU-92 — Evaluar el desempeño del organizador

> **Objetivo.** Medir cómo le va a cada organizador con datos que el sistema ya
> tiene, para poder subirlo de nivel, ayudarlo a tiempo o sancionarlo — siempre con
> el número a la vista y su desglose.

## Actores y disparador

- **Actor principal:** el sistema, por período.
- **Disparadores:** cierre del período de evaluación; pedido de renovación de
  habilitación; incidente que amerite evaluación extraordinaria.

## Precondiciones

1. El [[organizador]] estuvo activo durante el período con al menos un grupo.
2. Están cerrados los períodos de los grupos que entran en la medición: **no se
   evalúa con datos a medio cerrar**.
3. Existen las metas por métrica, con vigencia; el objetivo se fija antes del
   período, no después de ver el resultado.

## Flujo principal

1. Se crea [[evaluacion_desempeno]] con `organizador_id` y `periodo_evaluado`, único
   por ambos (`R-ORG-04`).
2. Se calculan las métricas del período, cada una en su [[metrica_organizador]] con
   `codigo`, `valor`, `meta`, `cumple` y `peso`:
   - `indice_morosidad_cartera` — proporción de obligaciones en mora de sus grupos;
   - `tasa_finalizacion_grupos` — grupos que llegaron al cierre completo;
   - `satisfaccion_participantes` — promedio de reseñas de sus grupos
     ([[CU-76 Reseñar a un participante y moderar la reseña]]);
   - `tiempo_respuesta_promedio_horas` — a incidencias y reclamos;
   - `incidencias_abiertas` y `coberturas_consumidas` del [[fondo_garantia]].
3. `puntaje_global` es la suma ponderada. **El total es exactamente la suma de sus
   componentes** y cada uno queda guardado: el organizador tiene que poder discutir
   una métrica concreta, no un número global.
4. Se deriva `nivel_sugerido` y `accion_recomendada`: mantener, subir de nivel,
   acompañar con capacitación, o abrir sanción
   ([[CU-93 Sancionar al organizador y resolver su apelación]]).
5. **La evaluación no ejecuta la acción**: la sugiere. Subir de nivel o sancionar
   son actos con responsable humano, porque afectan el sustento de una persona.
6. Se notifica al organizador con su tablero: dónde está bien, dónde no, contra qué
   meta, y qué hacer. Se le da plazo para observar antes de que la evaluación quede
   firme.
7. La serie histórica alimenta la renovación de habilitación y el emparejamiento:
   un organizador con buen historial recibe grupos antes.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El organizador tuvo pocos grupos en el período | Se evalúa igual pero se marca la baja representatividad; **no se lo castiga por muestra chica** |
| 2b | Una métrica no se puede calcular | Se registra con `cumple = null` y peso cero para ese período; no se asume el peor caso |
| 3a | El organizador observa una métrica | Se revisa contra los datos de origen; si estaba mal, se recalcula y queda constancia de la corrección |
| 4a | Deterioro fuerte respecto del período anterior | Se dispara acompañamiento antes de sanción: el objetivo es que el organizador mejore, no que se vaya |
| — | Morosidad alta por un factor externo (crisis local, un grupo con un caso grave) | El evaluador humano puede anotar el atenuante en la evaluación; queda escrito y auditado |
| — | Organizador que administra grupos heredados de una rescisión | Sus primeros períodos se marcan como cartera heredada y se ponderan aparte |
| — | Evaluación extraordinaria por incidente | Se genera fuera de ciclo, con período acotado y motivo explícito |

## Postcondiciones

- Cada organizador y período tiene una evaluación con métricas desglosadas.
- Ninguna acción sobre un organizador se toma sin una evaluación que la respalde.

## Contrato · `openapi/organizador.yaml`

```ts
export const EntradaCU92 = z.object({
  organizadorId: z.string().uuid(),
  periodoEvaluado: z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/),
  extraordinaria: z.boolean().default(false),
  motivo: z.string().max(300).nullable(),
}).strict()

export const SalidaCU92 = z.object({
  evaluacionId: z.string().uuid(),
  periodoEvaluado: z.string(),
  metricas: z.array(z.object({
    codigo: z.string(), valor: z.string(), meta: z.string(),
    cumple: z.boolean().nullable(), peso: z.string(),
  })),
  puntajeGlobal: z.string(),
  nivelSugerido: z.enum(['BASICO','INTERMEDIO','AVANZADO','REVISION']),
  accionRecomendada: z.string(),
  representatividadBaja: z.boolean(),
  plazoObservacion: z.string().datetime(),
}).strict()

export const ErroresCU92 = {
  PERIODO_NO_CERRADO:    'AP-CU92-01',
  EVALUACION_DUPLICADA:  'AP-CU92-02',
  SIN_ACTIVIDAD:         'AP-CU92-03',
  SIN_METAS_VIGENTES:    'AP-CU92-04',
  PLAZO_OBSERVACION_VENCIDO:'AP-CU92-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `PERIODO_NO_CERRADO` | Quedan períodos de grupos sin liquidar en la ventana |
| `EVALUACION_DUPLICADA` | Ya existe evaluación para ese organizador y período (`R-ORG-04`) |
| `SIN_ACTIVIDAD` | No administró ningún grupo en el período |
| `SIN_METAS_VIGENTES` | No hay metas definidas para el período: **no se inventan al cerrar** |
| `PLAZO_OBSERVACION_VENCIDO` | La observación llegó después de que la evaluación quedó firme |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularMetrica(codigo, hechos, meta)` | Una función por métrica, pura y probada aparte |
| Átomo | `puntajePonderado(metricas)` | Suma ponderada; puro, con la invariante total = suma |
| Átomo | `sugerirAccion(puntaje, tendencia)` | Traduce a recomendación; puro |
| Molécula | `EvaluacionRepositorio` · `MetricaOrganizadorRepositorio` | Persistencia y unicidad |
| Molécula | `LectorDeHechos` | Consulta agregados desde la réplica de lectura |
| Organismo | `CU92EvaluarDesempeno` | Transacción: evaluación, métricas, notificación y evento |
| Página | Trabajo mensual `evaluar-organizadores` · `GET /organizadores/:id/evaluaciones` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `organizador.evaluado` | Notificación con el tablero y el plazo de observación | — |
| `organizador.en_revision` | Acompañamiento o apertura de sanción, según la acción sugerida | `ORGANIZADORES_ADMINISTRAR` |
| — | Trabajo mensual de evaluación y cierre de plazos de observación | — |

## Interfaz

- **App:** *Mi desempeño*: cada métrica con su valor, su meta y su tendencia; y qué
  concretamente movería la aguja. Visible **durante** el período, no solo al final.
- **Backoffice:** ranking por período, con filtros por nivel y por acción sugerida, y
  el detalle de métricas por organizador.

## Restricciones aplicables

`R-ORG-04` · `R-REP-03` · `R-AUD-01` · `R-AUD-04` · `R-SEG-03`

## Evidencia que deja

[[evaluacion_desempeno]] · [[metrica_organizador]] · [[organizador]] ·
[[metrica_grupo]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dado un organizador con tres grupos cerrados en el período
Cuando corre la evaluación mensual
Entonces existe una evaluacion_desempeno con sus métricas desglosadas
Y puntaje_global es exactamente la suma ponderada de sus componentes

Dada una evaluación ya existente para ese organizador y período
Cuando se intenta generar otra
Entonces se rechaza con EVALUACION_DUPLICADA

Dado un organizador con un solo grupo en el período
Cuando se lo evalúa
Entonces la evaluación se marca con representatividad baja

Dada una observación del organizador dentro del plazo
Cuando se comprueba que una métrica estaba mal calculada
Entonces se recalcula y queda constancia de la corrección
```

## Ver también

[[CU-90 Postular a organizador y habilitarse]] · [[CU-91 Firmar y rescindir el contrato de organizador]] · [[CU-93 Sancionar al organizador y resolver su apelación]] · [[CU-95 Definir una regla de automatización]] · [[CU-98 Publicar el tablero de indicadores]]
