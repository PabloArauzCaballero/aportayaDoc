---
tags:
  - caso-uso
  - modulo/14-publicidad-campanas
codigo: CU-112
criticidad: alta
actores: [Anunciante, Moderador]
normas: [Política comercial interna, protección de datos]
---

# CU-112 — Moderar una pieza creativa

> **Objetivo.** Que ninguna imagen, texto o video de un anunciante llegue a
> mostrarse a un usuario sin que alguien la haya revisado antes — moderación
> previa, nunca posterior.

## Actores y disparador

- **Actor principal:** el [[anunciante]] (sube la pieza), un Moderador
  (revisa).
- **Disparadores:** el anunciante quiere usar una pieza creativa nueva en un
  [[anuncio]].

## Precondiciones

1. Existe el [[anunciante]] dueño de la pieza.

## Flujo principal

1. El anunciante sube [[pieza_creativa]] con `titulo`, `url_recurso`,
   `tipo_recurso` y `estado_moderacion = 'PENDIENTE'`.
2. Un Moderador revisa la pieza y crea [[revision_creativa]] con `decision`
   (`APROBADA` o `RECHAZADA`) y, si rechaza, `motivo` obligatorio.
3. **En la misma transacción** se actualiza
   `pieza_creativa.estado_moderacion` con la decisión.
4. Solo si `estado_moderacion = 'APROBADA'`, la pieza puede asociarse a un
   [[anuncio]] dentro de un [[conjunto_anuncios]].

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | La pieza se rechaza | El anunciante puede subir una pieza corregida, que entra como una `pieza_creativa` nueva con su propia revisión — no se reintenta la misma fila |
| — | Se intenta crear un `anuncio` con una pieza `PENDIENTE` o `RECHAZADA` | El sistema lo rechaza |
| — | Una pieza ya `APROBADA` se reemplaza por un anunciante (mismo contenido, otro archivo) | Se trata como pieza nueva, con su propia moderación; la anterior queda con su historial intacto |
| — | El moderador que revisa es el mismo anunciante dueño de la pieza | El sistema lo rechaza: quien sube no se autoaprueba |

## Postcondiciones

- Toda pieza creativa que aparece en algún [[anuncio]] tiene una
  `revision_creativa` con `decision = 'APROBADA'` que la respalda.

## Contrato · `openapi/publicidad.yaml`

```ts
export const EntradaCU112 = z.object({
  piezaCreativaId: z.string().uuid(),
  decision: z.enum(['APROBADA', 'RECHAZADA']),
  motivo: z.string().max(300).optional(),
}).strict()

export const SalidaCU112 = z.object({
  revisionId: z.string().uuid(),
  estadoModeracion: z.enum(['PENDIENTE', 'APROBADA', 'RECHAZADA']),
}).strict()

export const ErroresCU112 = {
  PIEZA_YA_REVISADA: 'AP-CU112-01',
  MOTIVO_OBLIGATORIO_EN_RECHAZO: 'AP-CU112-02',
  MODERADOR_ES_EL_ANUNCIANTE: 'AP-CU112-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `PIEZA_YA_REVISADA` | La pieza ya tiene una `revision_creativa` |
| `MOTIVO_OBLIGATORIO_EN_RECHAZO` | `decision = 'RECHAZADA'` sin `motivo` |
| `MODERADOR_ES_EL_ANUNCIANTE` | Quien revisa es el mismo anunciante dueño de la pieza |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `validarMotivoDeRechazo` | Exige motivo cuando la decisión es `RECHAZADA`; puro |
| Molécula | `PiezaCreativaRepositorio` | Alta y actualización de estado de moderación |
| Molécula | `RevisionCreativaRepositorio` | Alta de la revisión |
| Organismo | `CU112ModerarPieza` | Transacción de revisión + actualización de estado |
| Página | `apps/backoffice` — cola de moderación | Vista previa de la pieza, decisión con motivo obligatorio en rechazo |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `pieza_creativa.aprobada` | Habilita crear anuncios con esa pieza | `PUBLICIDAD_MODERAR` |
| `pieza_creativa.rechazada` | Notifica al anunciante con el motivo | `PUBLICIDAD_MODERAR` |

## Interfaz

- **App:** El anunciante ve el estado de sus piezas (pendiente/aprobada/rechazada
  con motivo) en su panel.
- **Backoffice:** Cola de moderación con vista previa del recurso y decisión con
  motivo obligatorio.

## Restricciones aplicables

`R-PUB-04` · `R-PUB-05`

Mismo principio de segregación que `R-SEG-04` (quien ejecuta no autoriza),
aplicado aquí a quien sube y quien modera.

## Evidencia que deja

[[pieza_creativa]] · [[revision_creativa]]

## Criterios de aceptación

```gherkin
Dada una pieza_creativa recién subida en estado PENDIENTE
Cuando un Moderador la aprueba
Entonces pieza_creativa.estado_moderacion pasa a APROBADA y queda su revision_creativa

Dada una pieza_creativa PENDIENTE
Cuando un Moderador la rechaza sin indicar motivo
Entonces el sistema devuelve MOTIVO_OBLIGATORIO_EN_RECHAZO

Dada una pieza_creativa con estado_moderacion PENDIENTE
Cuando se intenta crear un anuncio que la usa
Entonces el sistema rechaza la operación
```

## Ver también

[[CU-111 Crear y aprobar una campaña publicitaria]] · [[CU-113 Entregar un anuncio y medir su desempeño]]
