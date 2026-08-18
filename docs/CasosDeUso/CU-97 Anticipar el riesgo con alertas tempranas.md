---
tags:
  - caso-uso
  - modulo/08-garantia-incumplimiento-cobranza-y-sanciones
  - modulo/06-transparencia-y-reputacion
codigo: CU-97
criticidad: alta
actores: [Sistema, Organizador, Participante, Riesgos]
normas: [ASFI gestión de riesgo, buenas prácticas de cobranza, no discriminación arbitraria]
---

# CU-97 — Anticipar el riesgo con alertas tempranas

> **Objetivo.** Detectar que un participante o un grupo van camino al problema
> **antes** de que el problema ocurra, y usar eso para ofrecer ayuda —no para
> castigar por adelantado.

## Actores y disparador

- **Actor principal:** el sistema.
- **Disparadores:** cierre de período; pago tardío sin llegar a mora; caída de
  métricas del grupo; cambio brusco en el comportamiento de un participante.

## Precondiciones

1. Hay historial suficiente del participante o del grupo: **sin datos no se
   pronostica**, se dice que no se sabe.
2. Existe el modelo de scoring vigente ([[modelo_scoring]]) con su versión.
3. Existen las métricas del grupo calculadas para el período
   ([[metrica_grupo]]).

## Flujo principal

1. Se calculan las [[metrica_grupo]] del período: tasa de pago en término, días
   promedio de atraso, brecha de la bolsa, coberturas consumidas, rotación de
   participantes. Cada una con `umbral_alerta` y `en_alerta`.
2. Se calcula el [[score_riesgo_incumplimiento]] por participante y grupo, con
   `probabilidad_incumplimiento`, `factores_principales` en JSON, `nivel_riesgo` y
   `accion_sugerida`. **Los factores se guardan**: un puntaje sin factores no se
   puede discutir ni corregir.
3. Cuando una métrica cruza su umbral o el score sube de nivel, se crea
   [[alerta_temprana]] con `usuario_id`, `grupo_id`, `codigo`, `descripcion` y
   `severidad`. Las de alcance más amplio —plataforma, cartera— van a
   [[alerta_riesgo]] con su `ambito` y su evidencia.
4. **La alerta no restringe ni sanciona.** Dispara, según severidad:
   - contacto con la oferta de [[plan_regularizacion]] o [[promesa_pago]];
   - aviso al organizador con qué está pasando en su grupo;
   - refuerzo de recordatorios ([[CU-81 Programar recordatorios de aporte]]);
   - preparación del [[plan_contingencia]] si el grupo está en riesgo real.
5. Al participante se le habla de su situación, **nunca de su puntaje de riesgo**:
   decirle a alguien que el sistema cree que va a incumplir es una profecía, no una
   ayuda. Se le dice qué vence, cuánto, y qué opciones tiene.
6. La alerta se cierra cuando la causa desaparece —pagó, la métrica volvió al
   rango— o cuando se materializa en un incumplimiento
   ([[CU-25 Declarar el incumplimiento con descargo y evidencia]]). En ambos casos se
   registra el desenlace.
7. El desenlace alimenta la calibración del modelo: cuántas alertas terminaron en
   incumplimiento y cuántas no. **Un modelo que nunca se contrasta con lo que pasó
   es una superstición con decimales.**

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | Participante sin historial | No se calcula score; se marca `SIN_DATOS`. **No es riesgo alto por defecto**, y confundir ambas cosas excluye a los nuevos |
| 3a | Muchas alertas del mismo grupo | Se agrupan en una alerta de grupo; el organizador recibe una conversación, no veinte avisos |
| 4a | Alerta de severidad alta sobre un participante al día | Se revisa antes de contactar: molestar a quien está cumpliendo daña la relación y el modelo |
| 5a | El participante pregunta por qué lo contactaron | Se le explica con hechos —"te venció el aporte del 5 y todavía no figura pagado"—, sin exponer el modelo |
| 6a | Alerta que se cierra sin desenlace registrado | No se permite: toda alerta se cierra con resultado, igual que las de cumplimiento |
| — | El modelo empieza a fallar sistemáticamente | Se congela la versión, se recalibra y se documenta; las decisiones tomadas con la versión vieja quedan trazadas |
| — | Uso de la alerta para negar servicio | **No corresponde**: restringir exige causa consumada ([[CU-27 Restringir al deudor e incluirlo en la lista interna]]), no pronóstico |
| — | Alerta de cartera por concentración | Va a [[alerta_riesgo]] de ámbito plataforma y se trata en el comité de riesgos |

## Postcondiciones

- Todo riesgo detectado a tiempo genera una acción de acompañamiento registrada.
- Ninguna alerta temprana produce por sí sola una restricción sobre una persona.

## Contrato · `openapi/transparencia.yaml`

```ts
export const EntradaCU97 = z.object({
  ambito: z.enum(['PARTICIPANTE','GRUPO','CARTERA']),
  referenciaId: z.string().uuid().nullable(),
  periodoId: z.string().uuid().nullable(),
}).strict()

export const SalidaCU97 = z.object({
  alertas: z.array(z.object({
    alertaId: z.string().uuid(),
    codigo: z.string(),
    severidad: z.enum(['BAJA','MEDIA','ALTA','CRITICA']),
    descripcion: z.string(),
    accionSugerida: z.string(),
    mensajeAlUsuario: z.string(),      // en hechos, nunca en probabilidades
  })),
  score: z.object({
    nivelRiesgo: z.enum(['BAJO','MEDIO','ALTO','SIN_DATOS']),
    factoresPrincipales: z.array(z.object({
      factor: z.string(), contribucion: z.string(),
    })),
    versionModelo: z.string(),
  }).nullable(),
  metricasEnAlerta: z.array(z.object({
    codigo: z.string(), valor: z.string(), umbral: z.string(),
  })),
}).strict()

export const ErroresCU97 = {
  SIN_HISTORIAL:        'AP-CU97-01',
  SIN_MODELO_VIGENTE:   'AP-CU97-02',
  PERIODO_NO_CERRADO:   'AP-CU97-03',
  ALERTA_DUPLICADA:     'AP-CU97-04',
  CIERRE_SIN_DESENLACE: 'AP-CU97-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_HISTORIAL` | No hay datos suficientes; se devuelve `SIN_DATOS`, **no riesgo alto** |
| `SIN_MODELO_VIGENTE` | Ningún modelo de scoring vigente a la fecha |
| `PERIODO_NO_CERRADO` | Las métricas del grupo requieren el período cerrado |
| `ALERTA_DUPLICADA` | Ya hay una alerta abierta del mismo código y ámbito |
| `CIERRE_SIN_DESENLACE` | Se intenta cerrar una alerta sin registrar el resultado |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularMetricaGrupo(codigo, hechos)` | Una por métrica, pura y probada aparte |
| Átomo | `evaluarScore(modelo, factores)` | Probabilidad y factores principales; puro |
| Átomo | `mensajeEnHechos(alerta)` | Traduce la alerta a lenguaje de hechos, sin exponer el modelo; puro |
| Molécula | `MetricaGrupoRepositorio` · `AlertaTempranaRepositorio` | Persistencia y unicidad de abierta |
| Molécula | `DisparadorDeAcompanamiento` | Encadena plan, promesa o recordatorio reforzado |
| Organismo | `CU97EvaluarRiesgo` | Transacción: métricas, score, alertas y evento |
| Página | Trabajo `evaluar-riesgo` · `GET /grupos/:id/salud` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `alerta_temprana.generada` | Acompañamiento según severidad y aviso al organizador | — |
| `alerta_temprana.cerrada` | Registro del desenlace para calibrar el modelo | `RIESGOS_GESTIONAR` |
| `alerta_riesgo.generada` | Tratamiento en comité de riesgos | — |
| — | Trabajo por cierre de período y control de alertas abiertas sin acción | — |

## Interfaz

- **App:** para el participante, *Mi situación*: qué vence, cuánto, y los botones de
  plan de pago o promesa. **Nunca un puntaje de riesgo.** Para el organizador,
  *Salud del grupo*: métricas con su umbral y qué hacer.
- **Backoffice:** tablero de riesgo por cartera y grupo, alertas abiertas por
  severidad, y la calibración del modelo contra desenlaces reales.

## Restricciones aplicables

`R-SEG-03` · `R-REP-03` · `R-AUD-01` · `R-AUD-04` · `R-RIS-01` · `R-GAR-07`

## Evidencia que deja

[[alerta_temprana]] · [[alerta_riesgo]] · [[metrica_grupo]] ·
[[score_riesgo_incumplimiento]] · [[modelo_scoring]] · [[plan_regularizacion]] ·
[[promesa_pago]] · [[plan_contingencia]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dado un grupo cuya tasa de pago en término cae bajo el umbral
Cuando se cierra el período
Entonces existe una metrica_grupo con en_alerta en true
Y una alerta_temprana de ámbito grupo

Dado un participante sin historial
Cuando se evalúa su riesgo
Entonces el nivel es SIN_DATOS y no se genera alerta por eso

Dada una alerta temprana sobre un participante
Cuando se le notifica
Entonces el mensaje habla de hechos concretos y no menciona ningún puntaje

Dada una alerta abierta
Cuando se intenta cerrarla sin desenlace
Entonces se rechaza con CIERRE_SIN_DESENLACE
```

## Ver también

[[CU-25 Declarar el incumplimiento con descargo y evidencia]] · [[CU-27 Restringir al deudor e incluirlo en la lista interna]] · [[CU-48 Calibrar reglas de cumplimiento y triar sus alertas]] · [[CU-81 Programar recordatorios de aporte]] · [[CU-96 Programar y ejecutar una tarea automatizada]] · [[CU-98 Publicar el tablero de indicadores]]
