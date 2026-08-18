---
tags:
  - caso-uso
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
codigo: CU-34
criticidad: alta
actores: [Producto, Directorio, Sistema]
normas: [ASFI Consumidor Financiero — transparencia y preaviso]
---

# CU-34 — Publicar un tarifario nuevo con preaviso

> **Objetivo.** Cambiar precios **sin desplegar código** y sin sorprender a nadie:
> versión nueva, simulación, aprobación, preaviso, publicación y entrada en
> vigencia; con el precio de los grupos en curso intacto.

## Actores y disparador

- **Actores:** producto (propone), Directorio o comité (aprueba), sistema (publica
  y notifica).
- **Disparador:** decisión comercial o cambio normativo.

## Precondiciones

1. Existe un [[tarifario]] `VIGENTE` que será sustituido.

## Flujo principal

1. Se clona el tarifario vigente como versión N+1 en estado `BORRADOR` y se editan
   los [[concepto_tarifa]] y [[regla_tarifa]] necesarios.
2. Se corre [[CU-30 Cotizar la comisión antes de operar]] en modo simulación sobre
   la historia real: se guarda [[simulacion_tarifa]] con
   `delta_ingreso_estimado` y `usuarios_impactados`.
3. Se aprueba en comité: [[acta_comite]] y `tarifario.aprobado_por` +
   `acta_aprobacion`.
4. Se crea [[cambio_tarifario]] con `tipo_cambio`. Si es `INCREMENTO` o
   `NUEVO_CONCEPTO`, `requiere_preaviso=true` y `dias_preaviso` conforme a la
   norma (`R-TAR-08`).
5. El tarifario pasa a `EN_PREAVISO`; se notifica por los canales registrados y se
   guardan `fecha_aviso`, `canal_aviso` y `usuarios_notificados`.
6. Se publica el documento: [[documento_publicado]] `tipo='TARIFARIO'` con
   `url_publica`, `hash_documento` y `vigente_desde`.
7. Cumplido el preaviso, el tarifario pasa a `VIGENTE` y el anterior a
   `SUSTITUIDO` **sin borrarse** (`R-TAR-01`, `R-TAR-02`).
8. Los grupos con [[tarifa_congelada_grupo]] siguen con su snapshot (`R-TAR-07`).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 4a | Se intenta activar sin cumplir el preaviso | La transición a `VIGENTE` se rechaza |
| 4b | Reducción de comisiones | Puede entrar en vigencia sin preaviso, pero igual se publica y se registra |
| 5a | El cambio habilita rescisión sin costo | `permite_rescision_sin_costo=true`; el usuario puede cerrar su cuenta sin cargos ([[CU-16 Cerrar billetera y devolver saldo]]) |
| 7a | Se detecta un error después de publicar | No se edita: se emite la versión N+2 con la corrección |

## Postcondiciones

- Nunca hay dos tarifarios vigentes solapados para el mismo ámbito.
- Se puede probar qué se cobraba en cualquier fecha pasada y que se avisó.

## Contrato · `openapi/tarifas.yaml`

```ts
export const EntradaCU34 = z.object({
  tarifarioBaseId: z.string().uuid(),
  cambios: z.array(z.object({ conceptoCodigo: z.string(), campo: z.string(), valor: z.string() })),
  tipoCambio: z.enum(['INCREMENTO','REDUCCION','NUEVO_CONCEPTO','ELIMINACION']),
  diasPreaviso: z.number().int().min(0).max(90),
  aprobadoPor: z.string().uuid(),
  actaComiteId: z.string().uuid(),
}).strict()

export const SalidaCU34 = z.object({
  tarifarioNuevoId: z.string().uuid(),
  version: z.number().int(),
  estado: z.enum(['BORRADOR','EN_PREAVISO','VIGENTE']),
  simulacion: z.object({ deltaIngreso: MontoSchema, usuariosImpactados: z.number().int() }),
  entraEnVigencia: z.string().datetime(),
}).strict()

export const ErroresCU34 = {
  PREAVISO_NO_CUMPLIDO: 'AP-CU34-01',
  TARIFARIO_VIGENTE_INMUTABLE: 'AP-CU34-02',
  SIN_ACTA_DE_APROBACION: 'AP-CU34-03',
  VIGENCIAS_SOLAPADAS: 'AP-CU34-04',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `PREAVISO_NO_CUMPLIDO` | Falta el plazo de aviso para un incremento (R-TAR-08) |
| `TARIFARIO_VIGENTE_INMUTABLE` | Se intentó editar uno ya vigente (R-TAR-02) |
| `SIN_ACTA_DE_APROBACION` | Falta el acta del comité (R-LIC-03) |
| `VIGENCIAS_SOLAPADAS` | Ya hay un tarifario vigente en ese rango (R-TAR-01) |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `simularSobreHistoria` | Recalcula la historia con el tarifario nuevo; puro |
| Átomo | `calcularEntradaEnVigencia` | Fecha de aviso más días de preaviso |
| Molécula | `TarifarioRepositorio` | Versión nueva y cierre de la anterior |
| Molécula | `CambioTarifarioRepositorio` | Expediente del cambio y su aviso |
| Molécula | `DocumentoPublicadoRepositorio` | Publicación con hash y vigencia |
| Organismo | `CU34PublicarTarifario` | Transacción: versión, cambio, publicación y programación del aviso |
| Página | `POST /tarifarios` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `tarifario.en_preaviso` | Notificación masiva a los usuarios alcanzados | `TARIFARIO_PUBLICAR` |
| `tarifario.vigente` | Entrada en vigencia y sustitución del anterior | — |

## Interfaz

- **App:** Aviso del cambio con la fecha desde la que rige y qué se puede hacer.
- **Backoffice:** Editor de tarifario con simulación obligatoria antes de aprobar.

## Restricciones aplicables

`R-TAR-01` · `R-TAR-02` · `R-TAR-07` · `R-TAR-08` · `R-CON-07` · `R-LIC-03`

## Evidencia que deja

[[tarifario]] (N y N+1) · [[cambio_tarifario]] · [[simulacion_tarifa]] ·
[[documento_publicado]] · [[acta_comite]] · [[envio_notificacion]]

## Criterios de aceptación

```gherkin
Dado un incremento de comisión aprobado
Cuando se intenta poner VIGENTE antes de cumplir los días de preaviso
Entonces la operación se rechaza

Dado un tarifario que pasa a VIGENTE
Cuando se consulta el anterior
Entonces existe en estado SUSTITUIDO con su vigencia cerrada

Dado un grupo con tarifa congelada de la versión anterior
Cuando se liquida una entrega tras el cambio
Entonces la comisión se calcula con el snapshot congelado
```

## Ver también

[[CU-05 Aceptar contrato de adhesión y tarifario]] · [[CU-20 Crear grupo y congelar tarifario]] · [[CU-30 Cotizar la comisión antes de operar]] · [[CU-36 Segmentar comercialmente y aplicar precio diferenciado]]
