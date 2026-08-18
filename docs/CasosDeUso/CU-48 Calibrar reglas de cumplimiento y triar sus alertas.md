---
tags:
  - caso-uso
  - modulo/09-auditoria-reportes-y-cumplimiento
  - modulo/10-billetera-custodia-y-dinero-electronico
codigo: CU-48
criticidad: alta
actores: [Oficial de cumplimiento, Analista, Riesgos, Sistema]
normas: [UIF monitoreo, ASFI gestión de riesgo operativo, antifraude]
---

# CU-48 — Calibrar reglas de cumplimiento y triar sus alertas

> **Objetivo.** Que las reglas que vigilan la operación se puedan cambiar sin tocar
> código, que cada alerta que generan tenga dueño y conclusión, y que una regla que
> solo produce falsos positivos se detecte y se corrija en vez de enseñarle al
> equipo a ignorar alertas.

## Actores y disparador

- **Actor principal:** oficial de cumplimiento para las reglas de cumplimiento;
  riesgos para las de antifraude.
- **Disparadores:** control definido en una evaluación de producto
  ([[CU-47 Evaluar el riesgo del producto antes de lanzarlo]]); tipología nueva;
  exceso de falsos positivos; observación del supervisor; incidente de fraude.

## Precondiciones

1. Existe [[oficial_cumplimiento]] activo y, para antifraude, responsable de riesgos
   designado.
2. Los umbrales que la regla usa viven en [[umbral_reporte_uif]],
   [[umbral_operativo]] o [[limite_operativo_billetera]] **con vigencia**, no
   escritos dentro de la expresión (`R-UIF-01`).

## Flujo principal

1. Se define [[regla_cumplimiento]] con `codigo`, `categoria`, `expresion`,
   `umbral`, `ventana_horas`, `severidad`, `accion_automatica` y `activa`. Para
   fraude, la equivalente es [[regla_antifraude]] con `expresion` en JSON,
   `umbral_puntaje` y `accion`.
2. La `accion_automatica` dice qué hace el sistema **antes** de que intervenga una
   persona: `SOLO_ALERTAR`, `RETENER`, `BLOQUEAR` o `RECHAZAR`. Cuanto más dura la
   acción, más alto el umbral de aprobación para activarla.
3. **Antes de activar se simula sobre la base real**: cuántas operaciones de los
   últimos noventa días habría marcado, cuántas de esas terminaron siendo algo. Una
   regla que marca el 30 % del tráfico no se activa, se recalibra.
4. La activación queda con `vigente_desde` y `aprobada_por`. **Las reglas se
   versionan y no se borran**: hay que poder decir con qué regla se evaluó una
   operación de hace un año.
5. En operación, cada evaluación que dispara crea [[alerta_cumplimiento]] con
   `regla_id`, `usuario_id`, `operacion_tipo`, `operacion_id`, `monto_involucrado`,
   `detalle_deteccion` y `severidad`, en estado `ABIERTA`. Fraude escribe además
   [[evaluacion_antifraude]] con el puntaje y los factores.
6. El triaje asigna `analista_id` y termina **siempre** con `conclusion` escrita:
   `SIN_MERITO`, `MERITA_INVESTIGACION` —abre [[caso_investigacion_lft]] por
   [[CU-44 De alerta de monitoreo a reporte de operación sospechosa]]— o
   `AJUSTAR_REGLA`. **Una alerta no se cierra en blanco** (`R-UIF-07`).
7. Un tablero mensual muestra, por regla: alertas generadas, cerradas sin mérito y
   convertidas en caso. Ese cociente es el que decide si la regla se afina, se
   endurece o se retira.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | La simulación marca más del techo de tráfico definido | La activación se bloquea; hay que recalibrar antes de aprobar |
| 2a | Se pide `BLOQUEAR` en una regla de severidad baja | Rechazo `ACCION_DESPROPORCIONADA`: bloquear a un cliente es caro y tiene que justificarse |
| 5a | Una operación dispara varias reglas | Se genera una alerta por regla, pero el triaje las agrupa por operación para que el analista no trabaje cuatro veces lo mismo |
| 6a | La alerta vence sin analista | Escala automáticamente al oficial de cumplimiento y figura como incumplimiento de plazo interno (`R-UIF-08`) |
| 6b | Conclusión `AJUSTAR_REGLA` | Se abre el pedido de recalibración con la evidencia; hasta resolverlo la regla puede bajar a `SOLO_ALERTAR` |
| — | Regla con cero alertas en seis meses | Se revisa: o el riesgo desapareció, o la regla está mal escrita y da falsa tranquilidad |
| — | Cambio de umbral normativo | Se cambia el dato de umbral, **no la expresión de la regla** ([[CU-34 Publicar un tarifario nuevo con preaviso]] es el análogo comercial) |
| — | Regla de antifraude que retiene un pago legítimo | Se libera con justificación registrada y el caso alimenta la recalibración; el cliente recibe explicación y disculpa |

## Postcondiciones

- Toda regla vigente fue simulada, aprobada y versionada.
- Toda alerta tiene analista, plazo y conclusión escrita.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU48 = z.object({
  codigo:      z.string().max(40),
  descripcion: z.string().max(300),
  categoria:   z.enum(['UIF','FRAUDE','LIMITES','CONSUMIDOR','CONTABLE']),
  expresion:   z.string().max(400),
  umbralReferencia: z.string().max(60).nullable(),   // apunta al catálogo, no al valor
  ventanaHoras: z.number().int().min(1).max(8760).nullable(),
  severidad:   z.enum(['BAJA','MEDIA','ALTA','CRITICA']),
  accionAutomatica: z.enum(['SOLO_ALERTAR','RETENER','BLOQUEAR','RECHAZAR']),
}).strict()

export const EntradaTriajeCU48 = z.object({
  alertaId:   z.string().uuid(),
  conclusion: z.enum(['SIN_MERITO','MERITA_INVESTIGACION','AJUSTAR_REGLA']),
  fundamento: z.string().min(20).max(500),
}).strict()

export const SalidaSimulacionCU48 = z.object({
  operacionesEvaluadas: z.number().int(),
  operacionesMarcadas:  z.number().int(),
  porcentajeTrafico:    z.string(),
  superaTecho:          z.boolean(),
  muestra: z.array(z.object({ operacionId: z.string().uuid(), monto: MontoSchema })).max(20),
}).strict()

export const ErroresCU48 = {
  EXPRESION_INVALIDA:      'AP-CU48-01',
  UMBRAL_CABLEADO:         'AP-CU48-02',
  SIMULACION_REQUERIDA:    'AP-CU48-03',
  ACCION_DESPROPORCIONADA: 'AP-CU48-04',
  ALERTA_SIN_CONCLUSION:   'AP-CU48-05',
  CODIGO_DUPLICADO:        'AP-CU48-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `EXPRESION_INVALIDA` | La expresión no compila o referencia campos inexistentes |
| `UMBRAL_CABLEADO` | La expresión trae un número en vez de apuntar al catálogo (`R-UIF-01`) |
| `SIMULACION_REQUERIDA` | Se intenta activar sin simulación previa, o la simulación supera el techo |
| `ACCION_DESPROPORCIONADA` | La acción automática excede lo que la severidad habilita |
| `ALERTA_SIN_CONCLUSION` | Se intenta cerrar una alerta sin fundamento (`R-UIF-07`) |
| `CODIGO_DUPLICADO` | Ya existe una regla activa con ese código |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `compilarExpresion(texto)` | Texto a AST validado contra el esquema de campos; puro |
| Átomo | `evaluarRegla(ast, operacion, umbrales)` | Devuelve dispara/no y el detalle; puro |
| Átomo | `accionPermitida(severidad, accion)` | Proporcionalidad; puro |
| Molécula | `ReglaRepositorio` · `AlertaCumplimientoRepositorio` | Persistencia y versionado |
| Molécula | `SimuladorDeReglas` | Corre la regla contra el histórico en solo lectura |
| Organismo | `CU48ActivarRegla` · `CU48TriarAlerta` | Transacción: estado, conclusión, caso y evento |
| Página | `POST /reglas` · `POST /reglas/:id/simulacion` · `POST /alertas/:id/triaje` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `regla.activada` | Recarga de reglas en el evaluador, sin reinicio | `CUMPLIMIENTO_REGLAS` |
| `alerta.generada` | Asignación a analista y reloj de plazo | — |
| `alerta.concluida` | Caso de investigación si merita; recalibración si corresponde | `CUMPLIMIENTO_CASOS` |
| — | Trabajo diario que escala alertas vencidas y arma el tablero por regla | — |

## Interfaz

- **App:** sin pantalla. Cuando una regla retiene una operación, el usuario ve un
  mensaje claro con el plazo de revisión, **nunca la regla que lo marcó**.
- **Backoffice:** editor de reglas con simulador al lado, y bandeja de alertas
  agrupadas por operación con el detalle de detección y el formulario de conclusión.

## Restricciones aplicables

`R-UIF-01` · `R-UIF-02` · `R-UIF-07` · `R-UIF-08` · `R-LIM-01` · `R-AUD-01` ·
`R-AUD-04` · `R-LIC-03`

## Evidencia que deja

[[regla_cumplimiento]] · [[regla_antifraude]] · [[alerta_cumplimiento]] ·
[[evaluacion_antifraude]] · [[alerta_monitoreo_lft]] · [[caso_investigacion_lft]] ·
[[bitacora_evento]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dada una regla con umbral apuntando al catálogo y simulación bajo el techo
Cuando el oficial de cumplimiento la activa
Entonces queda vigente con vigente_desde y aprobada_por

Dada una regla cuya expresión trae el número 10000 escrito
Cuando se intenta guardar
Entonces se rechaza con UMBRAL_CABLEADO

Dada una alerta abierta
Cuando el analista intenta cerrarla sin fundamento
Entonces se rechaza con ALERTA_SIN_CONCLUSION

Dada una alerta sin analista pasado su plazo
Cuando corre el trabajo diario
Entonces escala al oficial de cumplimiento y queda registrada como plazo incumplido
```

## Ver también

[[CU-40 Evaluar límites antes de una operación]] · [[CU-44 De alerta de monitoreo a reporte de operación sospechosa]] · [[CU-47 Evaluar el riesgo del producto antes de lanzarlo]] · [[CU-95 Definir una regla de automatización]] · [[CU-97 Anticipar el riesgo con alertas tempranas]]
