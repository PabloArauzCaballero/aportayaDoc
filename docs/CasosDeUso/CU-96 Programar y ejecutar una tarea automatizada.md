---
tags:
  - caso-uso
  - modulo/07-organizador-y-automatizacion
codigo: CU-96
criticidad: alta
actores: [Sistema, Organizador, Operaciones]
normas: [Control interno, ASFI Consumidor Financiero, continuidad operativa]
---

# CU-96 — Programar y ejecutar una tarea automatizada

> **Objetivo.** Que lo que la regla decidió que hay que hacer se haga exactamente
> una vez, a la hora que corresponde, aunque el sistema se caiga en el medio o corra
> en tres réplicas a la vez.

## Actores y disparador

- **Actor principal:** el motor de automatización.
- **Disparadores:** [[regla_automatizacion]] que dispara por evento, cron o umbral;
  confirmación humana de una acción que la requería; reintento programado.

## Precondiciones

1. La [[regla_automatizacion]] está activa y su condición se cumple
   ([[CU-95 Definir una regla de automatización]]).
2. El grupo y el organizador están en estado que admite la acción.
3. Existe la ventana operativa: nada se ejecuta en horario prohibido salvo lo
   obligatorio.

## Flujo principal

1. Al dispararse la regla se crea [[tarea_automatizada]] con `regla_id`, `grupo_id`,
   `tipo`, `programada_para`, estado `PROGRAMADA`, `intentos` en cero y
   **`clave_idempotencia` derivada de (regla, ámbito, hecho disparador)**. Esa clave
   es lo que impide que el mismo hecho genere dos tareas (`R-ORG-07`).
2. Si la regla exige confirmación humana, la tarea queda `ESPERANDO_CONFIRMACION` y
   se notifica a quien deba confirmarla, con **qué va a pasar exactamente** si
   confirma. Sin confirmación no se ejecuta y a las N horas caduca.
3. El trabajador toma tareas vencidas con `SELECT … FOR UPDATE SKIP LOCKED`, de modo
   que dos réplicas no ejecutan la misma.
4. Cada corrida crea [[ejecucion_tarea]] con `iniciada_en` y, al terminar,
   `finalizada_en`, `resultado`, `registros_afectados`, `detalle` y
   `mensaje_error`. **Cada intento deja fila**: sin eso no se puede explicar por qué
   algo pasó tres veces o ninguna.
5. La acción se ejecuta **en su propia transacción**, con la misma clave de
   idempotencia que la tarea: si la transacción confirma y el registro de la tarea
   no, el reintento no duplica el efecto.
6. Resultado `EXITO` cierra la tarea. `FALLO` transitorio la reprograma con espera
   creciente hasta el tope; agotado, queda `FALLIDA` y **se avisa a una persona**:
   una automatización que falla en silencio es peor que no tenerla.
7. Toda ejecución escribe en [[bitacora_evento]] con la regla que la originó, para
   distinguir lo automático de lo manual.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | El mismo hecho dispara la regla dos veces | La clave única corta la segunda tarea (`R-ORG-07`) |
| 2a | Nadie confirma en el plazo | La tarea caduca con motivo y se avisa; **no se ejecuta por vencimiento**, porque el silencio no es consentimiento |
| 3a | Caída del proceso a mitad de ejecución | Al reiniciar, la tarea sigue tomada hasta que vence el bloqueo, y se reintenta con la misma clave |
| 5a | La acción ya no es válida al ejecutar (el período cerró, el participante se fue) | Se cancela con motivo, no se fuerza: la condición se reevalúa al ejecutar, no solo al programar |
| 6a | Fallo permanente por configuración | Se marca `FALLIDA`, se desactiva la regla si el fallo se repite y se avisa a quien la definió |
| — | Ventana de mantenimiento | Las tareas no obligatorias se reprograman; las obligatorias corren igual |
| — | Acumulación de tareas atrasadas | Se procesan por prioridad y antigüedad, y el atraso se mide como indicador operativo |
| — | La regla se desactiva con tareas programadas | Las ya programadas **se cancelan**, salvo las que están en ejecución |

## Postcondiciones

- Cada hecho que dispara una regla genera exactamente una tarea, con su historia de
  ejecuciones.
- Ninguna acción sensible se ejecuta sin confirmación registrada.

## Contrato · `openapi/organizador.yaml`

```ts
export const EntradaCU96 = z.object({
  reglaId: z.string().uuid(),
  ambitoId: z.string().uuid(),
  hechoDisparadorId: z.string().uuid(),
  programadaPara: z.string().datetime(),
}).strict()

export const EntradaConfirmarCU96 = z.object({
  tareaId: z.string().uuid(),
  decision: z.enum(['CONFIRMAR','CANCELAR']),
  motivo: z.string().max(300).nullable(),
}).strict()

export const SalidaCU96 = z.object({
  tareaId: z.string().uuid(),
  estado: z.enum(['PROGRAMADA','ESPERANDO_CONFIRMACION','EJECUTANDO','COMPLETADA',
                  'FALLIDA','CANCELADA','CADUCADA']),
  intentos: z.number().int(),
  proximoIntento: z.string().datetime().nullable(),
  ultimaEjecucion: z.object({
    resultado: z.enum(['EXITO','FALLO','PARCIAL']),
    registrosAfectados: z.number().int(),
    mensajeError: z.string().nullable(),
  }).nullable(),
  vistaPrevia: z.string(),   // qué va a hacer, en lenguaje llano
}).strict()

export const ErroresCU96 = {
  TAREA_DUPLICADA:        'AP-CU96-01',
  REGLA_INACTIVA:         'AP-CU96-02',
  CONFIRMACION_VENCIDA:   'AP-CU96-03',
  CONDICION_YA_NO_VALIDA: 'AP-CU96-04',
  AGOTADOS_LOS_INTENTOS:  'AP-CU96-05',
  FUERA_DE_VENTANA:       'AP-CU96-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `TAREA_DUPLICADA` | La clave de idempotencia ya existe; se devuelve la tarea existente |
| `REGLA_INACTIVA` | La regla se desactivó entre la programación y la ejecución |
| `CONFIRMACION_VENCIDA` | Se confirma después de que la tarea caducó |
| `CONDICION_YA_NO_VALIDA` | Al ejecutar, la condición dejó de cumplirse |
| `AGOTADOS_LOS_INTENTOS` | Se llegó al tope de reintentos; queda `FALLIDA` y se avisa |
| `FUERA_DE_VENTANA` | Acción no obligatoria fuera del horario permitido; se reprograma |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `claveDeTarea(regla, ambito, hecho)` | Clave determinista de idempotencia; pura |
| Átomo | `esperaConJitter(intento)` | Retroceso exponencial con dispersión; puro |
| Átomo | `describirAccion(regla, contexto)` | Vista previa en lenguaje llano; pura |
| Molécula | `TareaRepositorio` | Toma con `SKIP LOCKED`, unicidad y reprogramación |
| Molécula | `EjecutorDeAccion` | Un ejecutor por acción del catálogo, misma interfaz |
| Organismo | `CU96EjecutarTarea` | Transacción de la acción con la clave, más el registro de ejecución |
| Página | Trabajo `motor-automatizacion` · `POST /tareas/:id/confirmacion` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `tarea.programada` | Notificación de confirmación cuando corresponde | — |
| `tarea.ejecutada` | El efecto de la acción y su bitácora | — |
| `tarea.fallida` | Aviso a una persona y evaluación de desactivar la regla | — |
| — | Trabajo continuo del motor, con bloqueo entre réplicas y control de atraso | — |

## Interfaz

- **App:** *Grupo → Actividad automática*: qué hizo el sistema y cuándo, y las
  acciones que esperan su confirmación, con la vista previa de lo que van a hacer.
- **Backoffice:** cola de tareas por estado, atraso acumulado, tareas fallidas con
  su último error, y el interruptor de emergencia por regla.

## Restricciones aplicables

`R-ORG-06` · `R-ORG-07` · `R-BIL-06` · `R-AUD-01` · `R-AUD-04` · `R-SEG-03`

## Evidencia que deja

[[tarea_automatizada]] · [[ejecucion_tarea]] · [[regla_automatizacion]] ·
[[bitacora_evento]] · `evento_dominio` · [[cola_muerta]]

## Criterios de aceptación

```gherkin
Dada una regla que dispara sobre un hecho
Cuando el hecho se procesa dos veces
Entonces existe una sola tarea_automatizada

Dada una tarea que exige confirmación humana
Cuando nadie confirma dentro del plazo
Entonces queda CADUCADA y no se ejecuta

Dadas tres réplicas del motor
Cuando toman tareas vencidas a la vez
Entonces ninguna tarea se ejecuta dos veces

Dada una tarea cuya condición dejó de cumplirse al ejecutar
Cuando el motor la toma
Entonces se cancela con motivo y no se fuerza la acción
```

## Ver también

[[CU-95 Definir una regla de automatización]] · [[CU-81 Programar recordatorios de aporte]] · [[CU-83 Enrutar el envío por proveedor de mensajería]] · [[CU-97 Anticipar el riesgo con alertas tempranas]]
