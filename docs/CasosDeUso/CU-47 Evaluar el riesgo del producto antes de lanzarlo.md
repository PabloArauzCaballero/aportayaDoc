---
tags:
  - caso-uso
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-47
criticidad: alta
actores: [Producto, Oficial de cumplimiento, Comité de gobierno, ASFI]
normas: [UIF enfoque basado en riesgo, ASFI Res. 540/2025, ASFI no objeción]
---

# CU-47 — Evaluar el riesgo del producto antes de lanzarlo

> **Objetivo.** Que ningún producto, canal o funcionalidad nueva salga a producción
> sin que alguien haya escrito qué puede salir mal en materia de lavado, fraude y
> consumidor, qué control lo mitiga y quién lo aprobó.

## Actores y disparador

- **Actor principal:** oficial de cumplimiento, con producto como solicitante.
- **Disparadores:** producto nuevo; canal nuevo; cambio material en uno existente
  —límites, público objetivo, geografía—; observación del supervisor; revisión
  periódica de la evaluación vigente.

## Precondiciones

1. Existe [[licencia_regulatoria]] vigente y el producto cae dentro de su alcance
   ([[CU-46 Verificar el alcance de la licencia]]).
2. Existe [[matriz_riesgo_lft]] vigente con los factores a evaluar.
3. Hay [[oficial_cumplimiento]] designado y activo
   ([[CU-49 Designar al oficial de cumplimiento y capacitar]]).

## Flujo principal

1. Se crea [[evaluacion_riesgo_producto]] con `producto`, `version` incremental y
   estado `EN_ELABORACION`. **Cada cambio material es una versión nueva**, no una
   edición de la anterior.
2. Se identifican los riesgos en `riesgos_identificados`, con la estructura de la
   matriz: factor, descripción, probabilidad, impacto y riesgo inherente. Se cubren
   como mínimo cliente, producto, canal y geografía.
3. Se define `controles_definidos`: qué control mitiga cada riesgo, si es preventivo
   o detectivo, **automático o manual**, y en qué caso de uso vive. Un control que
   no apunta a un CU o a una restricción es una intención, no un control.
4. Se calcula el riesgo residual y se fija `nivel_riesgo_lft` en `BAJO`, `MEDIO` o
   `ALTO`. El nivel determina la debida diligencia mínima del producto y los
   umbrales de monitoreo que lo acompañan.
5. Se marca `requiere_no_objecion` cuando la norma exige pronunciamiento previo del
   supervisor. **Si es true, el producto no se habilita hasta tener la respuesta**, y
   eso se hace cumplir en el código, no en un procedimiento.
6. El comité aprueba ([[CU-94 Elevar una decisión al comité de gobierno]]), se
   registra `aprobada_por` y `fecha_aprobacion`, queda el [[acta_comite]] y el
   estado pasa a `VIGENTE`.
7. Los controles definidos se instrumentan como [[regla_cumplimiento]] y
   [[regla_monitoreo_lft]] concretas ([[CU-48 Calibrar reglas de cumplimiento y triar sus alertas]]):
   la evaluación no termina en un documento, termina en reglas que corren.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 5a | Se habilita el producto sin la no objeción | La restricción lo impide (`R-LIC-04`); el intento queda en [[bitacora_evento]] y escala como [[hallazgo_auditoria]] |
| 2a | Riesgo alto sin control que lo mitigue | La evaluación no puede aprobarse: **riesgo alto sin control es un no**, no un "se monitorea" |
| 6a | El comité aprueba con condiciones | Quedan como [[plan_accion_riesgo]] con responsable y fecha; el producto sale con las condiciones pendientes visibles |
| 1a | Cambio menor sin impacto en riesgo | Se documenta como nota en la versión vigente, con la justificación de por qué no amerita versión nueva |
| — | El supervisor observa el producto ya lanzado | Se abre [[observacion_regulatoria]], se genera versión nueva y se ajustan los controles con plazo |
| — | Vence la revisión periódica | La evaluación figura vencida en tablero y es un hallazgo; el producto sigue operando pero con el reloj en rojo |
| — | El producto se discontinúa | La evaluación se cierra con motivo, y se conserva por el plazo de retención |

## Postcondiciones

- Ningún producto vigente carece de evaluación aprobada con controles instrumentados.
- Se puede mostrar al supervisor, para cualquier fecha, con qué evaluación operaba
  cada producto.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU47 = z.object({
  producto: z.string().min(3).max(60),
  riesgosIdentificados: z.array(z.object({
    factor: z.enum(['CLIENTE','PRODUCTO','CANAL','GEOGRAFIA']),
    descripcion: z.string().max(300),
    probabilidad: z.number().int().min(1).max(5),
    impacto:      z.number().int().min(1).max(5),
  })).min(4),
  controlesDefinidos: z.array(z.object({
    riesgoIndice: z.number().int(),
    control: z.string().max(300),
    tipo: z.enum(['PREVENTIVO','DETECTIVO']),
    automatico: z.boolean(),
    referenciaCU: z.string().regex(/^CU-\d{2}$/).nullable(),
    referenciaRestriccion: z.string().regex(/^R-[A-Z]{3}-\d{2}$/).nullable(),
  })).min(1),
  requiereNoObjecion: z.boolean(),
}).strict()

export const SalidaCU47 = z.object({
  evaluacionId: z.string().uuid(),
  version:      z.number().int(),
  nivelRiesgoLft: z.enum(['BAJO','MEDIO','ALTO']),
  estado: z.enum(['EN_ELABORACION','EN_APROBACION','VIGENTE','OBSERVADA','CERRADA']),
  bloqueaHabilitacion: z.boolean(),
  reglasSugeridas: z.array(z.string()),
}).strict()

export const ErroresCU47 = {
  FUERA_DE_ALCANCE:        'AP-CU47-01',
  RIESGO_SIN_CONTROL:      'AP-CU47-02',
  FACTOR_FALTANTE:         'AP-CU47-03',
  SIN_NO_OBJECION:         'AP-CU47-04',
  VERSION_DUPLICADA:       'AP-CU47-05',
  APROBADOR_NO_HABILITADO: 'AP-CU47-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `FUERA_DE_ALCANCE` | El producto excede la licencia vigente (`R-LIC-01`) |
| `RIESGO_SIN_CONTROL` | Hay un riesgo residual alto sin control asociado |
| `FACTOR_FALTANTE` | Falta alguno de los cuatro factores obligatorios |
| `SIN_NO_OBJECION` | Se intenta habilitar un producto que la exige y no la tiene (`R-LIC-04`) |
| `VERSION_DUPLICADA` | Ya existe esa versión para el producto |
| `APROBADOR_NO_HABILITADO` | Quien aprueba no integra el comité con quórum |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `riesgoInherente(probabilidad, impacto)` | Producto y clasificación según la matriz; puro |
| Átomo | `riesgoResidual(inherente, controles)` | Descuenta por control efectivo; puro |
| Molécula | `EvaluacionProductoRepositorio` | Versionado y unicidad por producto y versión |
| Molécula | `VerificadorDeAlcance` | Cruza contra la licencia vigente |
| Organismo | `CU47AprobarEvaluacion` | Transacción: estado, acta, reglas sugeridas y evento |
| Página | `POST /evaluaciones-producto` · `POST /evaluaciones-producto/:id/aprobacion` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `producto.evaluado` | Alta de reglas de monitoreo sugeridas | `CUMPLIMIENTO_EVALUAR` |
| `producto.aprobado` | Habilitación del producto y aviso a las áreas | Quórum del comité |
| `producto.observado` | Plan de acción con plazo | — |
| — | Trabajo que vence evaluaciones y las marca para revisión | — |

## Interfaz

- **App:** sin pantalla.
- **Backoffice:** *Cumplimiento → Productos*: matriz de riesgo por producto, estado
  de la evaluación, controles con su CU o restricción, y el semáforo de vencimiento.

## Restricciones aplicables

`R-LIC-01` · `R-LIC-03` · `R-LIC-04` · `R-UIF-09` · `R-AUD-01` · `R-AUD-04`

## Evidencia que deja

[[evaluacion_riesgo_producto]] · [[acta_comite]] · [[licencia_regulatoria]] ·
[[plan_accion_riesgo]] · [[observacion_regulatoria]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dado un producto nuevo con los cuatro factores evaluados y controles asociados
Cuando el comité lo aprueba con quórum
Entonces la evaluación queda VIGENTE con fecha_aprobacion y acta

Dada una evaluación con un riesgo alto sin control
Cuando se intenta aprobar
Entonces se rechaza con RIESGO_SIN_CONTROL

Dado un producto con requiere_no_objecion en true y sin respuesta del supervisor
Cuando se intenta habilitarlo
Entonces la base lo impide y queda registrado el intento

Dado un cambio material en un producto vigente
Cuando se registra
Entonces se crea una versión nueva y la anterior conserva su histórico
```

## Ver también

[[CU-46 Verificar el alcance de la licencia]] · [[CU-48 Calibrar reglas de cumplimiento y triar sus alertas]] · [[CU-49 Designar al oficial de cumplimiento y capacitar]] · [[CU-94 Elevar una decisión al comité de gobierno]]
