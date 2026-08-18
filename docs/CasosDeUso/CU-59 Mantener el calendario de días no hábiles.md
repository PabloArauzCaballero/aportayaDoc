---
tags:
  - caso-uso
  - modulo/02-grupos-cupos-turnos-y-gobernanza
codigo: CU-59
criticidad: alta
actores: [Operaciones, Sistema, Grupo]
normas: [ASFI plazos en días hábiles, Consumidor Financiero, Ley 393]
---

# CU-59 — Mantener el calendario de días no hábiles

> **Objetivo.** Que "cinco días hábiles" signifique exactamente lo mismo para el
> sistema, para el cliente y para el supervisor — y que un feriado declarado la
> semana pasada no corra ningún plazo ya calculado.

## Actores y disparador

- **Actor principal:** operaciones.
- **Disparadores:** publicación del calendario anual de feriados; feriado
  departamental o decretado; día no laborable por decisión propia; feriado local que
  afecta a un grupo concreto.

## Precondiciones

1. La fuente del feriado es verificable: decreto, resolución o calendario oficial.
   **Un feriado sin fuente no se carga.**
2. Existe la política que define qué plazos se cuentan en días hábiles y cuáles en
   corridos.

## Flujo principal

1. Se crea [[dia_no_habil]] con `fecha`, `descripcion`, `alcance` —`NACIONAL`,
   `DEPARTAMENTAL`, `PLATAFORMA` o `GRUPO`— y `grupo_id` cuando el alcance es de un
   grupo. La combinación de fecha, alcance y grupo es única.
2. El cálculo de plazos hábiles consulta este calendario. Lo usan, entre otros:
   - el plazo de respuesta de reclamos ([[CU-52 Atender un reclamo en plazo]], `R-CON-01`);
   - el plazo de descargo de un incumplimiento
     ([[CU-25 Declarar el incumplimiento con descargo y evidencia]]);
   - el plazo de remisión de reportes ([[CU-43 Remitir los reportes mensuales a la UIF]]);
   - el vencimiento de aportes cuando el reglamento del grupo así lo pacta.
3. **Los plazos ya calculados no se recalculan** (`R-CON-01`). Si se declara un
   feriado después de haber fijado una fecha límite, esa fecha **no se mueve**; el
   calendario nuevo rige para los plazos que se abran desde su carga. Mover un plazo
   guardado sería reescribir el pasado, y para el cliente sería peor: le cambiamos
   la fecha que ya le habíamos dicho.
4. La excepción es a favor del cliente: si el vencimiento cae en día no hábil
   declarado **antes** del cálculo, se corre al siguiente hábil. Nunca al anterior.
5. La carga masiva del calendario anual valida que no haya duplicados y que ninguna
   fecha esté en el pasado, y deja constancia de la fuente en la descripción.
6. Un control mensual avisa si el calendario del período siguiente está vacío: un
   diciembre sin feriados cargados es un error de operación, no un año sin feriados.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | La fecha ya existe con el mismo alcance | Rechazo `FECHA_DUPLICADA`; para corregir la descripción se edita, no se agrega otra |
| 3a | Se declara un feriado retroactivo | Se carga con su fecha real, pero **los plazos vigentes conservan su vencimiento**; la respuesta lo explica |
| 4a | El feriado cae sobre el vencimiento de un aporte | Se corre al siguiente hábil y **se notifica al grupo**: nadie debería enterarse de un cambio de fecha por su cuenta |
| 1b | Feriado de alcance `GRUPO` sin `grupo_id` | Rechazo `ALCANCE_INCOMPLETO` |
| — | Feriado departamental | Solo aplica a usuarios y grupos de ese departamento; el cálculo toma el domicilio declarado |
| — | Media jornada | No es día no hábil: se maneja con horario de atención, no con el calendario de plazos |
| — | Se borra un feriado cargado por error | La fila se marca inactiva con motivo, no se elimina; hay que poder explicar por qué un plazo dio ese número |

## Postcondiciones

- Todo plazo hábil del sistema se calcula contra un calendario único y auditable.
- Ningún plazo guardado cambia por una carga posterior de calendario.

## Contrato · `openapi/grupos.yaml`

```ts
export const EntradaCU59 = z.object({
  fecha:       z.string().date(),
  descripcion: z.string().min(3).max(120),
  alcance:     z.enum(['NACIONAL','DEPARTAMENTAL','PLATAFORMA','GRUPO']),
  departamento: z.string().max(30).nullable(),
  grupoId:     z.string().uuid().nullable(),
  fuente:      z.string().max(120),
}).strict()

export const EntradaCalcularCU59 = z.object({
  desde: z.string().datetime(),
  dias:  z.number().int().min(1).max(365),
  alcance: z.enum(['NACIONAL','DEPARTAMENTAL','PLATAFORMA','GRUPO']),
  referenciaId: z.string().uuid().nullable(),
}).strict()

export const SalidaCU59 = z.object({
  fechaLimite: z.string().datetime(),
  diasSalteados: z.array(z.object({
    fecha: z.string().date(), descripcion: z.string(),
  })),
  calendarioVersion: z.string(),   // para poder explicar el cálculo después
}).strict()

export const ErroresCU59 = {
  FECHA_DUPLICADA:    'AP-CU59-01',
  ALCANCE_INCOMPLETO: 'AP-CU59-02',
  FECHA_EN_EL_PASADO: 'AP-CU59-03',
  SIN_FUENTE:         'AP-CU59-04',
  CALENDARIO_VACIO:   'AP-CU59-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `FECHA_DUPLICADA` | Ya existe esa fecha con el mismo alcance y grupo |
| `ALCANCE_INCOMPLETO` | Alcance `GRUPO` sin `grupoId`, o `DEPARTAMENTAL` sin departamento |
| `FECHA_EN_EL_PASADO` | Se intenta cargar una fecha vencida en la carga masiva anual |
| `SIN_FUENTE` | No se indicó el decreto, resolución o calendario que lo respalda |
| `CALENDARIO_VACIO` | Se pide calcular un plazo en un período sin calendario cargado: **se rechaza en vez de contar corridos por defecto** |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `sumarDiasHabiles(desde, dias, noHabiles)` | El cálculo; puro, con pruebas de propiedad y casos de borde en fin de año |
| Átomo | `siguienteHabil(fecha, noHabiles)` | Corrimiento a favor; puro |
| Molécula | `CalendarioRepositorio` | Consulta por rango y alcance, con caché de vida corta |
| Organismo | `CU59CargarCalendario` | Transacción: carga masiva con validación y constancia de fuente |
| Página | `POST /calendario` · `GET /calendario/calcular` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `calendario.actualizado` | Recarga del caché y aviso a los grupos con vencimientos afectados | `CALENDARIO_ADMINISTRAR` |
| — | Control mensual de calendario del período siguiente | — |

## Interfaz

- **App:** en la fecha de vencimiento de un aporte, si se corrió por feriado, se
  muestra por qué. La fecha nunca cambia en silencio.
- **Backoffice:** calendario anual por alcance, con la fuente de cada día y qué
  plazos vigentes lo tomaron en cuenta.

## Restricciones aplicables

`R-CON-01` · `R-CON-02` · `R-AUD-01` · `R-AUD-04` · `R-GRP-16`

## Evidencia que deja

[[dia_no_habil]] · [[bitacora_evento]] · `evento_dominio` ·
[[reclamo_cliente]] (en el plazo que guarda) · [[registro_incumplimiento]]

## Criterios de aceptación

```gherkin
Dado un plazo de 5 días hábiles desde el viernes y un lunes feriado nacional
Cuando se calcula la fecha límite
Entonces el lunes no cuenta y la fecha resultante lo refleja
Y diasSalteados enumera el feriado

Dado un reclamo con fecha límite ya guardada
Cuando se declara un feriado dentro de ese plazo
Entonces la fecha límite del reclamo no cambia

Dado un vencimiento de aporte que cae en feriado declarado antes del cálculo
Cuando se genera la obligación
Entonces el vencimiento se corre al siguiente día hábil y se notifica al grupo

Dado un período sin calendario cargado
Cuando se pide calcular un plazo hábil
Entonces se rechaza con CALENDARIO_VACIO
```

## Ver también

[[CU-25 Declarar el incumplimiento con descargo y evidencia]] · [[CU-43 Remitir los reportes mensuales a la UIF]] · [[CU-52 Atender un reclamo en plazo]] · [[CU-21 Cobrar el aporte del período]]
