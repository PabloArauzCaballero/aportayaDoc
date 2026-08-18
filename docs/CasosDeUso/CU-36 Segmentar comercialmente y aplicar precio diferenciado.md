---
tags:
  - caso-uso
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
codigo: CU-36
criticidad: media
actores: [Producto, Sistema, Usuario]
normas: [ASFI Consumidor Financiero, transparencia de precios, no discriminación]
---

# CU-36 — Segmentar comercialmente y aplicar precio diferenciado

> **Objetivo.** Que se pueda cobrar distinto a quien está en otra situación —un
> grupo grande, un cliente antiguo, una campaña— con un criterio escrito, aplicable
> por máquina y explicable al cliente y al supervisor.

## Actores y disparador

- **Actor principal:** producto, que define los segmentos.
- **Disparadores:** lanzamiento de campaña; política de fidelización; convenio
  institucional; corrección de un segmento mal calibrado.

## Precondiciones

1. Existe [[tarifario]] vigente y publicado (`R-CON-07`).
2. El [[segmento_comercial]] tiene `criterio` expresable como datos —antigüedad,
   cantidad de grupos completados, monto acumulado, convenio— y **no como una lista
   de nombres cargada a mano**.
3. La política de precios diferenciados está aprobada, con acta (`R-LIC-03`).

## Flujo principal

1. Se crea [[segmento_comercial]] con `codigo`, `descripcion`, `criterio` en JSON,
   `prioridad` y `activo`. La prioridad resuelve el empate: **un usuario puede
   calificar para varios segmentos y solo uno rige**.
2. El criterio se evalúa contra datos que el sistema ya tiene y puede recalcular:
   [[reputacion_usuario]], grupos completados, antigüedad, [[perfil_transaccional]].
   Nada de banderas manuales sin origen.
3. Al cotizar ([[CU-30 Cotizar la comisión antes de operar]]) se resuelve el segmento
   del usuario en ese momento y se aplica la [[regla_tarifa]] correspondiente. **El
   segmento aplicado se guarda en la [[cotizacion_comision]]**: seis meses después
   hay que poder decir por qué pagó eso.
4. Al crear un grupo, el segmento se congela junto con el tarifario en
   [[tarifa_congelada_grupo]] ([[CU-20 Crear grupo y congelar tarifario]]): perder el
   beneficio a mitad del pasanaku sería cambiar el precio pactado.
5. Las promociones temporales van por [[campana_promocional]] y
   [[aplicacion_promocion]], con vigencia y tope; las exenciones puntuales por
   [[exencion_comision]] con autorización nominada.
6. El usuario ve en el detalle del costo **qué beneficio se le aplicó y por qué**,
   no un precio más bajo sin explicación.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | Dos segmentos con la misma prioridad y criterios solapados | Rechazo al crearlos: la ambigüedad se resuelve al definir, no al cobrar |
| 3a | El usuario deja de cumplir el criterio | Pierde el beneficio en las operaciones **nuevas**; las ya cotizadas y los grupos en curso mantienen su precio |
| 3b | El criterio no se puede evaluar por falta de datos | Se aplica el precio base y se registra el motivo; nunca se adivina a favor ni en contra |
| 5a | Se combina segmento con promoción | Se aplica según la regla de acumulación definida en la política; por defecto **no se acumulan** y rige el más favorable al cliente |
| — | El segmento resulta discriminatorio | Se revisa y se retira: un criterio de precio no puede apoyarse en datos sensibles ni en categorías protegidas |
| — | Corrección retroactiva de un segmento mal aplicado | Se devuelve la diferencia por [[CU-33 Devolver comisión y emitir nota de crédito]]; no se edita la cotización vieja |
| — | Convenio institucional que vence | El segmento tiene vigencia; al vencer, los usuarios vuelven al precio base con preaviso ([[CU-34 Publicar un tarifario nuevo con preaviso]]) |

## Postcondiciones

- Todo precio cobrado es reconstruible: tarifario, segmento, promoción y exención,
  cada uno con su identificador.
- Ningún beneficio se otorga ni se quita sin regla y sin registro.

## Contrato · `openapi/tarifas.yaml`

```ts
export const EntradaCU36 = z.object({
  codigo:      z.string().max(30),
  descripcion: z.string().max(200),
  criterio:    z.record(z.unknown()),          // AST evaluable, no texto libre
  prioridad:   z.number().int().min(1).max(99),
  activo:      z.boolean().default(true),
}).strict()

export const SalidaResolverCU36 = z.object({
  segmentoId:     z.string().uuid().nullable(),
  segmentoCodigo: z.string().nullable(),
  motivo:         z.string(),                  // por qué califica, en texto para el usuario
  reglaTarifaId:  z.string().uuid(),
  precioBase:     MontoSchema,
  precioAplicado: MontoSchema,
}).strict()

export const ErroresCU36 = {
  CRITERIO_INVALIDO:      'AP-CU36-01',
  PRIORIDAD_DUPLICADA:    'AP-CU36-02',
  SIN_TARIFARIO_VIGENTE:  'AP-CU36-03',
  CRITERIO_NO_EVALUABLE:  'AP-CU36-04',
  SEGMENTO_DISCRIMINATORIO:'AP-CU36-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `CRITERIO_INVALIDO` | El JSON no compila a una expresión evaluable |
| `PRIORIDAD_DUPLICADA` | Otro segmento activo tiene la misma prioridad y criterio solapado |
| `SIN_TARIFARIO_VIGENTE` | No hay tarifario publicado al que colgar la regla (`R-CON-07`) |
| `CRITERIO_NO_EVALUABLE` | Faltan datos del usuario; se cotiza al precio base y se informa |
| `SEGMENTO_DISCRIMINATORIO` | El criterio referencia datos sensibles o categorías protegidas |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `evaluarCriterio(criterio, hechosDelUsuario)` | Devuelve califica/no y el motivo legible; puro |
| Átomo | `elegirSegmento(candidatos)` | Resuelve por prioridad; puro |
| Molécula | `SegmentoRepositorio` | Persistencia y consulta de activos |
| Molécula | `ResolvedorDeSegmento` | Reúne los hechos del usuario y evalúa |
| Organismo | `CU36ResolverPrecio` | Compone segmento, promoción y exención sobre el tarifario |
| Página | `GET /precios/resolver` · `POST /segmentos` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `segmento.publicado` | Recálculo de precios mostrados y aviso si mejora | `TARIFARIO_ADMINISTRAR` |
| `segmento.aplicado` | Se guarda en la cotización para auditoría | — |
| — | Trabajo que reevalúa segmentos al cambiar reputación o cerrarse un grupo | — |

## Interfaz

- **App:** en el detalle del costo, una línea que dice el beneficio aplicado y por
  qué lo tiene. Y en *Mi cuenta*, qué le falta para el siguiente escalón.
- **Backoffice:** editor de segmentos con simulación sobre la base real antes de
  activar, y cuántos usuarios caen en cada uno.

## Restricciones aplicables

`R-TAR-01` · `R-TAR-02` · `R-TAR-03` · `R-TAR-07` · `R-CON-07` · `R-LIC-03`

## Evidencia que deja

[[segmento_comercial]] · [[regla_tarifa]] · [[cotizacion_comision]] ·
[[tarifa_congelada_grupo]] · [[campana_promocional]] · [[aplicacion_promocion]] ·
[[exencion_comision]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dado un usuario con tres grupos completados y un segmento que exige tres
Cuando cotiza una comisión
Entonces la cotizacion_comision guarda el segmento aplicado
Y el precio es el del segmento, no el base

Dado un usuario que califica para dos segmentos activos
Cuando se resuelve su precio
Entonces se aplica el de mayor prioridad y solo uno queda registrado

Dado un grupo creado con un segmento vigente
Cuando el usuario deja de calificar a mitad del ciclo
Entonces el grupo conserva la tarifa congelada

Dado un segmento cuyo criterio referencia un dato sensible
Cuando se intenta crear
Entonces se rechaza con SEGMENTO_DISCRIMINATORIO
```

## Ver también

[[CU-30 Cotizar la comisión antes de operar]] · [[CU-31 Devengar y cobrar la comisión]] · [[CU-34 Publicar un tarifario nuevo con preaviso]] · [[CU-20 Crear grupo y congelar tarifario]]
