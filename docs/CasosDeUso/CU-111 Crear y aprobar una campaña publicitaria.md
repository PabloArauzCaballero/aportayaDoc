---
tags:
  - caso-uso
  - modulo/14-publicidad-campanas
codigo: CU-111
criticidad: media
actores: [Anunciante, Operaciones]
normas: [Política comercial interna]
---

# CU-111 — Crear y aprobar una campaña publicitaria

> **Objetivo.** Que un anunciante pueda armar una campaña con presupuesto,
> segmentación y espacio de entrega definidos, y que nada salga al aire sin
> que alguien de Operaciones la apruebe primero.

## Actores y disparador

- **Actor principal:** el [[anunciante]] (arma la campaña), Operaciones
  (aprueba).
- **Disparadores:** el anunciante quiere lanzar publicidad dentro de la app.

## Precondiciones

1. Existe la [[cuenta_publicitaria]] del anunciante, en estado `ACTIVA`.
2. Existen el [[segmento_audiencia]] y el [[espacio_publicitario]] que se
   quieren usar, y el espacio está `activo`.

## Flujo principal

1. El anunciante crea [[campana_publicitaria]] en estado `BORRADOR`, con
   `objetivo`, `presupuesto_total`, `moneda` y vigencia.
2. Agrega uno o más [[conjunto_anuncios]], cada uno con su
   `segmento_audiencia_id`, `espacio_publicitario_id`, `presupuesto_diario` y
   `puja_maxima`.
3. La campaña pasa a `EN_REVISION`.
4. Operaciones revisa que el presupuesto no exceda
   `cuenta_publicitaria.limite_gasto_mensual` disponible y que la segmentación
   sea admisible; aprueba: `estado = 'ACTIVA'`, `aprobada_por`.
5. Mientras la campaña está `ACTIVA`, cada [[conjunto_anuncios]] entrega
   [[anuncio]] según su espacio y presupuesto (ver
   [[CU-113 Entregar un anuncio y medir su desempeño]]).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 4a | El presupuesto de la campaña excede el límite disponible de la cuenta | Se rechaza la aprobación; el anunciante debe reducir el presupuesto o esperar al siguiente mes |
| — | El anunciante pausa la campaña voluntariamente | `estado = 'PAUSADA'`; los conjuntos dejan de entregar pero conservan su configuración |
| — | La campaña llega a `fecha_fin` sin agotar el presupuesto | `estado = 'FINALIZADA'` automáticamente |
| — | Operaciones rechaza la campaña en revisión | `estado = 'RECHAZADA'` con motivo; el anunciante puede corregir y volver a enviarla como una campaña nueva |

## Postcondiciones

- Ninguna campaña entrega anuncios sin haber pasado por aprobación explícita.

## Contrato · `openapi/publicidad.yaml`

```ts
export const EntradaCU111 = z.object({
  cuentaPublicitariaId: z.string().uuid(),
  nombre: z.string().max(120),
  objetivo: z.enum(['VISIBILIDAD_MARCA', 'TRAFICO', 'POSTULACION_GRUPO', 'DESCARGA_APP', 'CONVERSION']),
  presupuestoTotal: MontoSchema,
  fechaInicio: z.string().datetime(),
  fechaFin: z.string().datetime().optional(),
  conjuntos: z.array(z.object({
    segmentoAudienciaId: z.string().uuid(),
    espacioPublicitarioId: z.string().uuid(),
    presupuestoDiario: MontoSchema,
    pujaMaxima: MontoSchema,
    modeloPuja: z.enum(['CPM', 'CPC']),
  })).min(1),
}).strict()

export const SalidaCU111 = z.object({
  campanaPublicitariaId: z.string().uuid(),
  estado: z.string(),
}).strict()

export const ErroresCU111 = {
  LIMITE_DE_GASTO_EXCEDIDO: 'AP-CU111-01',
  ESPACIO_PUBLICITARIO_INACTIVO: 'AP-CU111-02',
  CUENTA_PUBLICITARIA_NO_ACTIVA: 'AP-CU111-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `LIMITE_DE_GASTO_EXCEDIDO` | El presupuesto total supera el límite disponible de la cuenta publicitaria |
| `ESPACIO_PUBLICITARIO_INACTIVO` | El `espacio_publicitario_id` referenciado tiene `activo = false` |
| `CUENTA_PUBLICITARIA_NO_ACTIVA` | La cuenta publicitaria no está `ACTIVA` |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `validarLimiteDeGasto` | Compara presupuesto contra límite disponible; puro |
| Molécula | `CampanaPublicitariaRepositorio` | Alta, aprobación y transición de estados |
| Molécula | `ConjuntoAnunciosRepositorio` | Alta de conjuntos de una campaña |
| Organismo | `CU111CrearCampana` | Transacción de alta + conjuntos |
| Página | `apps/backoffice` — revisión de campañas | Cola de campañas `EN_REVISION` con acción de aprobar/rechazar |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `campana_publicitaria.aprobada` | Habilita la entrega de anuncios | `PUBLICIDAD_APROBAR_CAMPANA` |
| `campana_publicitaria.finalizada` | Detiene la entrega, dispara la liquidación del período | Interno (worker) |

## Interfaz

- **App:** Un organizador anunciante arma su campaña desde el panel de su
  grupo; un socio comercial usa el mismo flujo desde su propio acceso.
- **Backoffice:** Cola de aprobación de campañas con presupuesto, segmentación
  y espacio solicitado visibles antes de aprobar.

## Restricciones aplicables

`R-PUB-02` · `R-PUB-03`

`limite_gasto_mensual` es un control comercial, no un tope legal.

## Evidencia que deja

[[campana_publicitaria]] · [[conjunto_anuncios]] · [[segmento_audiencia]] ·
[[espacio_publicitario]]

## Criterios de aceptación

```gherkin
Dada una cuenta publicitaria activa con límite disponible suficiente
Cuando el anunciante crea una campaña con un conjunto de anuncios y la envía a revisión
Entonces la campaña queda en estado EN_REVISION

Dada una campaña en revisión con presupuesto dentro del límite
Cuando Operaciones la aprueba
Entonces la campaña pasa a ACTIVA con aprobada_por registrado

Dada una campaña cuyo presupuesto_total excede el límite disponible de la cuenta
Cuando Operaciones intenta aprobarla
Entonces el sistema devuelve LIMITE_DE_GASTO_EXCEDIDO
```

## Ver también

[[CU-110 Dar de alta un anunciante y su cuenta publicitaria]] · [[CU-112 Moderar una pieza creativa]] · [[CU-113 Entregar un anuncio y medir su desempeño]]
