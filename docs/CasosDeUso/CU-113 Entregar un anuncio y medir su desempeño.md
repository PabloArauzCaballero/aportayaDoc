---
tags:
  - caso-uso
  - modulo/14-publicidad-campanas
codigo: CU-113
criticidad: media
actores: [Sistema, Usuario]
normas: [Política comercial interna]
---

# CU-113 — Entregar un anuncio y medir su desempeño

> **Objetivo.** Que un anuncio se muestre solo mientras su conjunto tiene
> presupuesto y cupo de espacio, y que cada impresión, clic y conversión quede
> registrado para cobrarlo y para que el anunciante vea qué resultado tuvo.

## Actores y disparador

- **Actor principal:** el sistema (motor de entrega); el usuario final que ve
  el anuncio dispara los eventos.
- **Disparadores:** un usuario navega una pantalla con un
  [[espacio_publicitario]] activo.

## Precondiciones

1. Existe al menos un [[anuncio]] en estado `PROGRAMADO` o `EN_ENTREGA`, cuyo
   [[conjunto_anuncios]] está `ACTIVO` y cuya [[pieza_creativa]] está
   `APROBADA`.
2. El [[espacio_publicitario]] correspondiente tiene cupo
   (`capacidad_maxima_simultanea` no alcanzada).

## Flujo principal

1. Al pedirse un espacio, el sistema elige un `anuncio` elegible por espacio,
   segmentación y puja; lo marca `EN_ENTREGA` si era la primera vez.
2. Se registra [[impresion_anuncio]] con `usuario_id` (si hay sesión),
   `mostrada_en` y `costo` según `modelo_puja` del conjunto.
3. Si el usuario hace clic, se registra [[clic_anuncio]] enlazado a esa
   impresión.
4. Si el clic (o la impresión, en campañas de solo visibilidad) deriva en un
   hecho de negocio —postulación a un grupo, registro, descarga—, se registra
   [[conversion_anuncio]] con `tipo` y `referencia_id`.
5. El `costo` acumulado del día se descuenta de
   `conjunto_anuncios.presupuesto_diario`; al agotarse, el conjunto pasa a
   `AGOTADO` y deja de entregar hasta el día siguiente.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | Ningún anuncio elegible cabe en el espacio (todos los conjuntos agotados o pausados) | El espacio no muestra publicidad; nunca se fuerza una entrega fuera de presupuesto |
| 5a | El conjunto se agota a mitad de una ráfaga de impresiones concurrentes | Se acepta un margen de sobregasto controlado del último lote; el conjunto pasa a `AGOTADO` igual, no se revierten las impresiones ya servidas |
| — | El usuario no tiene sesión identificada | `impresion_anuncio.usuario_id` queda `NULL`; la entrega y el costo se registran igual |
| — | `campana_publicitaria.presupuesto_total` se agota antes que el `fecha_fin` | Todos sus `conjunto_anuncios` pasan a `FINALIZADO`, no solo `AGOTADO` del día |

## Postcondiciones

- Ningún anuncio se entrega más allá del presupuesto que su conjunto tiene
  autorizado para el día.
- Cada impresión, clic y conversión queda como evento append-only, base para la
  liquidación del período (ver [[CU-114 Liquidar y facturar el gasto publicitario]]).

## Contrato · `openapi/publicidad.yaml`

```ts
export const EntradaCU113 = z.object({
  espacioPublicitarioId: z.string().uuid(),
  usuarioId: z.string().uuid().optional(),
}).strict()

export const SalidaCU113 = z.object({
  anuncioId: z.string().uuid().nullable(),
  impresionId: z.string().uuid().nullable(),
}).strict()

export const ErroresCU113 = {
  SIN_ANUNCIO_ELEGIBLE: 'AP-CU113-01',
  ESPACIO_SIN_CUPO: 'AP-CU113-02',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_ANUNCIO_ELEGIBLE` | No hay ningún `anuncio` elegible con presupuesto disponible (no es un error de cliente: el espacio simplemente no muestra nada) |
| `ESPACIO_SIN_CUPO` | El `espacio_publicitario` ya está en su `capacidad_maxima_simultanea` |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `elegirAnuncioElegible` | Filtra por espacio, segmento y presupuesto disponible; puro |
| Molécula | `ImpresionAnuncioRepositorio` | Alta append-only de impresiones |
| Molécula | `ClicAnuncioRepositorio` / `ConversionAnuncioRepositorio` | Alta append-only de clics y conversiones |
| Organismo | `CU113EntregarAnuncio` | Selección + registro de impresión en la misma operación |
| Página | Componente de banner/listado destacado en `apps/movil` y en la web pública | Muestra la pieza creativa aprobada del anuncio elegido |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `conjunto_anuncios.agotado` | Detiene la entrega hasta el día siguiente | Interno |
| `campana_publicitaria.finalizada` | Detiene toda entrega de la campaña | Interno |

## Interfaz

- **App / web pública:** Espacios publicitarios definidos (banner de inicio,
  listado de grupos destacado, notificación patrocinada).
- **Backoffice:** Panel de desempeño por campaña — impresiones, clics,
  conversiones y gasto acumulado.

## Restricciones aplicables

`R-AUD-01` (impresión, clic y conversión son append-only) · `R-PUB-04`.

## Evidencia que deja

[[anuncio]] · [[impresion_anuncio]] · [[clic_anuncio]] · [[conversion_anuncio]]

## Criterios de aceptación

```gherkin
Dado un conjunto_anuncios activo con presupuesto_diario disponible
Cuando un usuario ve el espacio publicitario asociado
Entonces se crea una impresion_anuncio y se descuenta su costo del presupuesto diario

Dada una impresion_anuncio reciente
Cuando el usuario hace clic en el anuncio
Entonces se crea clic_anuncio enlazado a esa impresión

Dado un conjunto_anuncios cuyo presupuesto_diario ya se agotó
Cuando se solicita un anuncio para su espacio
Entonces el sistema no entrega ningún anuncio de ese conjunto
```

## Ver también

[[CU-111 Crear y aprobar una campaña publicitaria]] · [[CU-112 Moderar una pieza creativa]] · [[CU-114 Liquidar y facturar el gasto publicitario]]
