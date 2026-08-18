---
tags:
  - caso-uso
  - modulo/07-organizador-y-automatizacion
codigo: CU-95
criticidad: alta
actores: [Organizador, Operaciones, Sistema]
normas: [Gobernanza del grupo, ASFI Consumidor Financiero, control interno]
---

# CU-95 — Definir una regla de automatización

> **Objetivo.** Que el organizador pueda delegar en el sistema lo repetitivo
> —recordar, generar, cerrar— sin que eso le permita automatizar decisiones que
> tienen que seguir siendo de una persona.

## Actores y disparador

- **Actor principal:** organizador para su ámbito; operaciones para las reglas de
  plataforma.
- **Disparadores:** configuración inicial de un grupo; ajuste tras notar trabajo
  manual repetido; incidente causado por una acción automática mal calibrada.

## Precondiciones

1. El organizador está habilitado y con contrato vigente
   ([[CU-91 Firmar y rescindir el contrato de organizador]]).
2. La acción pedida está en el catálogo cerrado de acciones automatizables. **No hay
   ejecución de código arbitrario**: la automatización elige entre acciones que ya
   existen y están probadas.

## Flujo principal

1. Se define [[regla_automatizacion]] con `codigo`, `descripcion`, `disparador`
   —`EVENTO`, `CRON` o `UMBRAL`—, `expresion_disparo`, `condicion`, `accion`,
   `requiere_confirmacion_humana`, `prioridad` y `activa`.
2. **La acción determina si puede ser automática**. Se admiten sin confirmación:
   recordar, generar obligaciones del período, abrir el período siguiente, publicar
   el resumen, marcar mora. **Exigen confirmación humana siempre**: cobrar, entregar,
   sancionar, expulsar, cubrir con el fondo y cualquier cosa que mueva dinero o
   afecte derechos (`R-ORG-06`).
3. La `condicion` se evalúa sobre datos del ámbito del organizador. Un organizador
   **no puede escribir reglas que toquen grupos que no administra**, y eso lo
   garantiza la política de fila, no la interfaz.
4. Antes de activar se simula sobre los últimos períodos: cuántas veces habría
   disparado y sobre qué. Una regla que dispara todos los días sobre todos no
   automatiza, satura.
5. Activada, queda con su `prioridad`: si dos reglas disparan sobre el mismo hecho,
   corre la de mayor prioridad y la otra se registra como no aplicada, con motivo.
6. Las reglas se versionan; desactivar escribe la fecha y **no borra la fila**. Hay
   que poder explicar por qué el sistema hizo algo en marzo.
7. Toda acción automática deja rastro con `es_automatico = true` y la regla que la
   originó, para que en la bitácora se distinga lo que hizo una persona de lo que
   hizo el sistema.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | Se pide automatizar una acción que exige confirmación | Se crea igual, pero con `requiere_confirmacion_humana = true` forzado; el sistema prepara la acción y **espera** |
| 3a | La condición referencia un grupo ajeno | La política de fila devuelve conjunto vacío; la regla nunca dispara y se avisa al definirla |
| 4a | La simulación muestra disparos excesivos | La activación se bloquea hasta recalibrar |
| 5a | Dos reglas de la misma prioridad sobre el mismo hecho | Rechazo al crear la segunda: el empate se resuelve al definir |
| — | Una regla causa un perjuicio | Se desactiva, se registra [[evento_riesgo_operativo]] y se revisan todas las de la misma acción |
| — | El organizador es suspendido | Sus reglas quedan inactivas mientras dure la suspensión; las de plataforma siguen |
| — | Cambia el catálogo de acciones | Las reglas que usaban una acción retirada quedan inactivas con aviso, nunca ejecutando algo distinto de lo definido |

## Postcondiciones

- Ninguna acción con dinero o con efecto sobre derechos se ejecuta sin confirmación
  humana.
- Toda acción automática es atribuible a una regla identificable y vigente.

## Contrato · `openapi/organizador.yaml`

```ts
export const EntradaCU95 = z.object({
  codigo: z.string().max(40),
  descripcion: z.string().max(200),
  ambito: z.object({
    tipo: z.enum(['GRUPO','ORGANIZADOR','PLATAFORMA']),
    referenciaId: z.string().uuid().nullable(),
  }),
  disparador: z.enum(['EVENTO','CRON','UMBRAL']),
  expresionDisparo: z.string().max(80),
  condicion: z.string().max(300),
  accion: z.enum([
    'RECORDAR','GENERAR_OBLIGACIONES','ABRIR_PERIODO','PUBLICAR_RESUMEN','MARCAR_MORA',
    'PROPONER_COBRO','PROPONER_ENTREGA','PROPONER_SANCION','PROPONER_COBERTURA',
  ]),
  prioridad: z.number().int().min(1).max(99),
}).strict()

export const SalidaCU95 = z.object({
  reglaId: z.string().uuid(),
  requiereConfirmacionHumana: z.boolean(),
  activa: z.boolean(),
  simulacion: z.object({
    periodosEvaluados: z.number().int(),
    disparosEstimados: z.number().int(),
    superaTecho: z.boolean(),
    muestra: z.array(z.string()).max(10),
  }),
}).strict()

export const ErroresCU95 = {
  ACCION_NO_AUTOMATIZABLE: 'AP-CU95-01',
  AMBITO_AJENO:            'AP-CU95-02',
  CONDICION_INVALIDA:      'AP-CU95-03',
  PRIORIDAD_DUPLICADA:     'AP-CU95-04',
  SIMULACION_REQUERIDA:    'AP-CU95-05',
  ORGANIZADOR_SUSPENDIDO:  'AP-CU95-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `ACCION_NO_AUTOMATIZABLE` | La acción no está en el catálogo cerrado |
| `AMBITO_AJENO` | El ámbito apunta a un grupo que no administra (`R-SEG-03`) |
| `CONDICION_INVALIDA` | No compila o referencia campos inexistentes |
| `PRIORIDAD_DUPLICADA` | Otra regla activa comparte prioridad y disparador |
| `SIMULACION_REQUERIDA` | Se intenta activar sin simular, o la simulación supera el techo |
| `ORGANIZADOR_SUSPENDIDO` | Está suspendido y no puede definir reglas nuevas |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `exigeConfirmacion(accion)` | Tabla explícita de acciones sensibles; puro |
| Átomo | `compilarCondicion(texto)` | Texto a AST validado; puro |
| Átomo | `resolverPrioridad(reglas, hecho)` | Elige la aplicable; puro |
| Molécula | `ReglaAutomatizacionRepositorio` | Persistencia, versionado y unicidad de prioridad |
| Molécula | `SimuladorDeReglas` | Corre sobre el histórico en solo lectura |
| Organismo | `CU95ActivarRegla` | Transacción: alta, simulación registrada y evento |
| Página | `POST /automatizacion/reglas` · `POST /automatizacion/reglas/:id/simulacion` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `regla_automatizacion.activada` | Recarga del motor sin reinicio | `AUTOMATIZACION_ADMINISTRAR` |
| `regla_automatizacion.desactivada` | Aviso y registro del motivo | `AUTOMATIZACION_ADMINISTRAR` |
| — | Motor que evalúa reglas por evento, cron y umbral | — |

## Interfaz

- **App:** *Grupo → Automatizaciones*: en lenguaje llano, "cuando pase esto, hacé
  esto", con un aviso claro de cuáles van a pedirle confirmación siempre.
- **Backoffice:** catálogo de reglas por ámbito, con el simulador y el registro de
  disparos.

## Restricciones aplicables

`R-SEG-03` · `R-SEG-04` · `R-ORG-06` · `R-AUD-01` · `R-AUD-04`

## Evidencia que deja

[[regla_automatizacion]] · [[tarea_automatizada]] · [[bitacora_evento]] ·
`evento_dominio`

## Criterios de aceptación

```gherkin
Dado un organizador habilitado
Cuando define una regla que recuerda tres días antes del vencimiento
Entonces la regla queda activa sin requerir confirmación humana

Dada una regla cuya acción es PROPONER_ENTREGA
Cuando se guarda
Entonces requiere_confirmacion_humana queda en true forzado

Dada una regla cuyo ámbito apunta a un grupo ajeno
Cuando se intenta crear
Entonces se rechaza con AMBITO_AJENO

Dadas dos reglas activas con la misma prioridad y disparador
Cuando se intenta crear la segunda
Entonces se rechaza con PRIORIDAD_DUPLICADA
```

## Ver también

[[CU-96 Programar y ejecutar una tarea automatizada]] · [[CU-81 Programar recordatorios de aporte]] · [[CU-48 Calibrar reglas de cumplimiento y triar sus alertas]] · [[CU-92 Evaluar el desempeño del organizador]]
