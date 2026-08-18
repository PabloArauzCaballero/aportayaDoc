---
tags:
  - caso-uso
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
codigo: CU-32
criticidad: alta
actores: [Sistema, Servicio de impuestos]
normas: [SIN — facturación en línea (CUF, CUFD, código de control, eventos significativos)]
---

# CU-32 — Emitir factura electrónica

> **Objetivo.** Que cada comisión cobrada tenga su documento fiscal válido, y que
> **la caída del servicio de impuestos no detenga la operación**.

## Actores y disparador

- **Actor principal:** el sistema.
- **Disparador:** `devengo_comision.estado='COBRADO'`.

## Precondiciones

1. Existen [[datos_facturacion]] del usuario (NIT o CI y razón social).
2. Hay CUFD vigente para la sucursal y punto de venta (vigencia de 24 horas,
   extensible hasta 72 en los casos previstos).

## Flujo principal

1. Se toman los datos de facturación predeterminados del usuario.
2. Se arma el documento: `nit_emisor`, `sucursal`, `punto_venta`,
   `numero_factura` correlativo, `cuf`, `cufd`, `codigo_control`, `monto_total`,
   `monto_iva`, `leyenda` y `qr_verificacion`.
3. Se firma digitalmente y se envía en línea.
4. Aceptado, se guarda [[factura_electronica]] con `estado_fiscal='VALIDADA'`,
   `url_pdf`, `url_xml` y `hash_documento`, y se enlaza al [[devengo_comision]].
5. Se entrega al usuario por su canal y queda disponible en la app.

## Flujos alternativos — contingencia

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | El servicio no responde | Se abre [[evento_significativo_sin]] con `codigo_evento`, `fecha_inicio` y `plazo_registro` calculado |
| 3b | Emisión durante la contingencia | La factura se emite con `estado_fiscal='EMITIDA_OFFLINE'` y `evento_significativo_id`; **la operación comercial no se detiene** |
| — | Se restablece el servicio | Se cierra el evento con `fecha_fin`, se registra dentro del plazo (hasta 48 horas de concluida la contingencia) y se envían los documentos por [[lote_envio_sin]] |
| 4a | El documento es rechazado | `estado_fiscal='RECHAZADA'`; se corrige y se reemite; el rechazo queda registrado |
| — | Corrección de una factura emitida | **Nunca se edita**: se anula y se emite [[nota_credito_debito]] (`R-TAR-10`) |
| 1a | El usuario no dio datos de facturación | Se emite con los datos mínimos previstos y se solicita completar; el cobro no se revierte por eso |

## Postcondiciones

- Toda comisión cobrada tiene documento fiscal o un evento de contingencia abierto
  que lo justifica y un plazo corriendo.

## Contrato · `openapi/tarifas.yaml`

```ts
export const EntradaCU32 = z.object({
  devengoId: z.string().uuid(),
  datosFacturacionId: z.string().uuid().optional(),
}).strict()

export const SalidaCU32 = z.object({
  facturaId: z.string().uuid(),
  cuf: z.string(),
  numeroFactura: z.number().int(),
  estadoFiscal: z.enum(['VALIDADA','EMITIDA_OFFLINE','RECHAZADA']),
  urlPdf: z.string().url(),
  eventoSignificativoId: z.string().uuid().nullable(),
}).strict()

export const ErroresCU32 = {
  SIN_CUFD_VIGENTE: 'AP-CU32-01',
  SERVICIO_FISCAL_CAIDO: 'AP-CU32-02',
  DOCUMENTO_RECHAZADO: 'AP-CU32-03',
  FACTURA_YA_EMITIDA: 'AP-CU32-04',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_CUFD_VIGENTE` | No hay código diario vigente para el punto de venta |
| `SERVICIO_FISCAL_CAIDO` | Se emite offline bajo evento significativo |
| `DOCUMENTO_RECHAZADO` | El servicio de impuestos rechazó el envío |
| `FACTURA_YA_EMITIDA` | El devengo ya tiene factura (R-TAR-09) |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `componerCuf` | Arma el código único desde NIT, fecha, sucursal y correlativo; puro |
| Átomo | `calcularVigenciaCufd` | Ventana de validez del código diario |
| Molécula | `FacturaRepositorio` | Documento fiscal y su estado |
| Molécula | `SiatAdaptador` | Envío en línea y por lote, con reintentos |
| Molécula | `EventoSignificativoRepositorio` | Contingencia con inicio, fin y plazo |
| Organismo | `CU32EmitirFactura` | Transacción: documento, envío y contingencia si aplica |
| Página | — | Sin endpoint: lo dispara el planificador o un evento |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `factura.emitida` | Entrega al usuario por su canal | Interno |
| `contingencia.abierta` | Trabajo que registra el evento dentro del plazo | — |

## Interfaz

- **App:** La factura queda disponible en el detalle de la operación.
- **Backoffice:** Monitor fiscal: emitidas, offline pendientes de envío y rechazadas.

## Restricciones aplicables

`R-TAR-09` · `R-TAR-10` · `R-TAR-13` · `R-AUD-08`

## Evidencia que deja

[[factura_electronica]] · [[evento_significativo_sin]] · [[lote_envio_sin]] ·
[[nota_credito_debito]] (si corrige) · [[datos_facturacion]]

## Criterios de aceptación

```gherkin
Dado un devengo cobrado
Cuando se emite la factura en línea
Entonces existe factura_electronica con CUF único y estado_fiscal VALIDADA

Dada una contingencia del servicio de impuestos
Cuando se emiten documentos
Entonces cada uno queda EMITIDA_OFFLINE con evento_significativo_id
Y el evento tiene plazo_registro guardado

Dado un intento de modificar el monto de una factura validada
Cuando se ejecuta
Entonces se rechaza; solo procede anulación y nota de crédito
```

## Ver también

[[CU-22 Liquidar y entregar el fondo]] · [[CU-31 Devengar y cobrar la comisión]] · [[CU-33 Devolver comisión y emitir nota de crédito]] · [[CU-114 Liquidar y facturar el gasto publicitario]]
