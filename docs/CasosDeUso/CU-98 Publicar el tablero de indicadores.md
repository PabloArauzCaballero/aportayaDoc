---
tags:
  - caso-uso
  - modulo/09-auditoria-reportes-y-cumplimiento
codigo: CU-98
criticidad: media
actores: [Operaciones, Directorio, Riesgos, Sistema]
normas: [ASFI gobierno corporativo, control interno, protección de datos]
---

# CU-98 — Publicar el tablero de indicadores

> **Objetivo.** Que la dirección mire siempre los mismos números, calculados de la
> misma forma, con su meta al lado — para discutir qué hacer en vez de discutir de
> dónde salió la cifra.

## Actores y disparador

- **Actor principal:** el sistema, por período.
- **Disparadores:** cierre diario, mensual o trimestral; sesión de comité
  ([[CU-94 Elevar una decisión al comité de gobierno]]); pedido del supervisor.

## Precondiciones

1. Los cierres del período están cuadrados
   ([[CU-51 Ejecutar el cierre diario]], [[CU-35 Cerrar la liquidación mensual de ingresos]]).
   **Un indicador sobre datos sin cuadrar es una opinión.**
2. Cada indicador tiene definición escrita: fórmula, fuente, dimensión y meta.
3. Las metas del período están fijadas **antes** del período.

## Flujo principal

1. Se calcula cada [[indicador_kpi]] con `codigo`, `nombre`, `valor`, `unidad`,
   `dimension` —`PLATAFORMA`, `GRUPO`, `ORGANIZADOR`, `PRODUCTO`—, `dimension_id`,
   `periodo`, `meta` y `variacion_periodo_anterior`. La combinación de código,
   dimensión, identificador y período es única.
2. El cálculo corre **contra la réplica de lectura**, con la misma consulta que
   define el indicador: no hay una planilla aparte donde alguien recalcula a mano.
3. Los indicadores se agrupan por familia, y cada familia tiene un dueño con nombre:
   - **negocio** — grupos activos, participantes, volumen aportado, entregas;
   - **riesgo** — morosidad, coberturas consumidas, alertas abiertas;
   - **cumplimiento** — reportes en plazo, alertas sin conclusión, capacitación;
   - **operación** — cierres cuadrados, incidencias con SLA vencido, disponibilidad;
   - **finanzas** — ingresos devengados y cobrados, encaje, costo por proveedor.
4. Un indicador **que no cumple su meta no se maquilla**: se muestra en rojo con su
   variación, y el dueño escribe la explicación en el propio tablero.
5. Los indicadores que agregan datos de personas se publican **solo agregados**: por
   debajo del mínimo de casos, no se muestra el valor
   —un promedio de tres personas identifica a las tres.
6. El tablero es reproducible: cada indicador guarda con qué definición y qué
   período se calculó, de modo que un número de hace un año se puede volver a
   obtener igual.
7. Los indicadores alimentan las sesiones de comité y las evaluaciones de
   organizador ([[CU-92 Evaluar el desempeño del organizador]]), sin recalcularse por
   separado en cada lugar.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | El período no está cerrado | El indicador se marca **provisorio** y así se muestra; nunca se presenta un provisorio como definitivo |
| 2a | Cambia la definición de un indicador | Se versiona y se recalcula la serie con la nueva, **manteniendo también la vieja**: los saltos de serie se explican, no se ocultan |
| 4a | Un indicador falla su meta sin explicación cargada | Aparece como pendiente del dueño en la sesión del comité |
| 5a | Muestra por debajo del mínimo de casos | Se suprime el valor con la leyenda correspondiente (`R-SEG-03`) |
| — | Indicador nuevo sin historia | Se muestra sin variación, no con cero: no hay comparación posible todavía |
| — | Dato corregido después de publicado | Se recalcula el período afectado y **queda registrado que se corrigió**, con la fecha |
| — | Pedido de un indicador a medida | Va por [[CU-58 Definir, programar y exportar un reporte]]; el tablero no se llena de excepciones |

## Postcondiciones

- Cada período tiene su juego de indicadores, con meta, variación y dueño.
- Todo número del tablero es reproducible desde su definición y su período.

## Contrato · `openapi/auditoria.yaml`

```ts
export const EntradaCU98 = z.object({
  periodo: z.string().regex(/^\d{4}(-\d{2})?(-\d{2})?$/),
  dimension: z.enum(['PLATAFORMA','GRUPO','ORGANIZADOR','PRODUCTO']),
  dimensionId: z.string().uuid().nullable(),
  familias: z.array(z.enum(['NEGOCIO','RIESGO','CUMPLIMIENTO','OPERACION','FINANZAS'])).min(1),
}).strict()

export const SalidaCU98 = z.object({
  periodo: z.string(),
  provisorio: z.boolean(),
  indicadores: z.array(z.object({
    codigo: z.string(), nombre: z.string(),
    valor: z.string().nullable(),          // null si se suprimió por mínimo de casos
    unidad: z.string(),
    meta: z.string().nullable(),
    cumpleMeta: z.boolean().nullable(),
    variacionPeriodoAnterior: z.string().nullable(),
    suprimidoPorPrivacidad: z.boolean(),
    duenoFamilia: z.string(),
    explicacion: z.string().nullable(),
    definicionVersion: z.string(),
  })),
}).strict()

export const ErroresCU98 = {
  PERIODO_NO_CERRADO:   'AP-CU98-01',
  SIN_DEFINICION:       'AP-CU98-02',
  SIN_META_DEL_PERIODO: 'AP-CU98-03',
  MUESTRA_INSUFICIENTE: 'AP-CU98-04',
  INDICADOR_DUPLICADO:  'AP-CU98-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `PERIODO_NO_CERRADO` | Se pide definitivo sobre un período abierto; se ofrece el provisorio |
| `SIN_DEFINICION` | El código de indicador no tiene definición vigente |
| `SIN_META_DEL_PERIODO` | No hay meta fijada; se calcula el valor pero sin semáforo |
| `MUESTRA_INSUFICIENTE` | La muestra no alcanza el mínimo para publicar sin identificar personas |
| `INDICADOR_DUPLICADO` | Ya existe ese código, dimensión, identificador y período |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `variacion(actual, anterior)` | Variación relativa con manejo de cero y de ausencia; pura |
| Átomo | `cumpleMeta(valor, meta, sentido)` | Semáforo según si más es mejor o peor; puro |
| Átomo | `suprimirPorMinimo(valor, casos, minimo)` | Privacidad por agregación; puro |
| Molécula | `IndicadorRepositorio` | Persistencia y unicidad por código, dimensión y período |
| Molécula | `CalculadorDeIndicador` | Una implementación por indicador, contra la réplica de lectura |
| Organismo | `CU98PublicarTablero` | Calcula la familia completa y publica el período |
| Página | Trabajo `publicar-indicadores` · `GET /tablero` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `tablero.publicado` | Aviso a los dueños de familia con sus indicadores en rojo | `TABLERO_VER` |
| `indicador.fuera_de_meta` | Pedido de explicación al dueño y punto en el comité | — |
| — | Trabajo por cierre diario, mensual y trimestral | — |

## Interfaz

- **App:** sin pantalla. El participante ve la salud de **su** grupo en
  [[CU-97 Anticipar el riesgo con alertas tempranas]], que es otra cosa.
- **Backoffice:** *Tablero*: familias, semáforo por indicador, serie histórica con
  las marcas de cambio de definición, y el campo de explicación del dueño.

## Restricciones aplicables

`R-SEG-03` · `R-AUD-01` · `R-AUD-07` · `R-BIL-12` · `R-LIC-03`

## Evidencia que deja

[[indicador_kpi]] · [[metrica_grupo]] · [[metrica_organizador]] ·
[[cierre_diario]] · [[liquidacion_ingresos]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dado un mes con todos los cierres cuadrados
Cuando se publica el tablero
Entonces cada indicador tiene valor, meta y variación respecto del mes anterior

Dado un período todavía abierto
Cuando se consulta el tablero
Entonces los indicadores se marcan como provisorios

Dado un indicador de dimensión GRUPO con menos casos que el mínimo
Cuando se publica
Entonces el valor se suprime y se informa el motivo

Dado un cambio en la definición de un indicador
Cuando se recalcula la serie
Entonces la serie anterior sigue disponible y el corte queda señalado
```

## Ver también

[[CU-51 Ejecutar el cierre diario]] · [[CU-58 Definir, programar y exportar un reporte]] · [[CU-92 Evaluar el desempeño del organizador]] · [[CU-94 Elevar una decisión al comité de gobierno]] · [[CU-97 Anticipar el riesgo con alertas tempranas]]
