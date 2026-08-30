---
tags:
  - entidad
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
tabla: factura_electronica
clase: FacturaElectronica
modulo: "11 — Tarifas, Comisiones, Impuestos y Facturación"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 26
fk_salientes: 5
fk_entrantes: 2
append_only: false
---

# `factura_electronica`

> Módulo [[11_tarifas_comisiones|11 — Tarifas, Comisiones, Impuestos y Facturación]] · clase `FacturaElectronica` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `devengo_id` | UUID | FK IDX | sí | FK, NULL, IDX |
| `usuario_id` | UUID | FK IDX | no | FK, IDX, M1 |
| `datos_facturacion_id` | UUID | FK | no | FK |
| `lote_envio_sin_id` | UUID | FK | sí | FK, NULL |
| `evento_significativo_id` | UUID | FK | sí | FK, NULL |
| `nit_emisor` | VARCHAR(20) | — | no | — |
| `sucursal` | SMALLINT | — | no | — |
| `punto_venta` | SMALLINT | — | no | — |
| `numero_factura` | BIGINT | UQ | no | UQ+sucursal+punto_venta |
| `cuf` | VARCHAR(80) | UQ | no | — |
| `cufd` | VARCHAR(120) | — | no | — |
| `codigo_control` | VARCHAR(20) | — | sí | NULL |
| `fecha_emision` | TIMESTAMPTZ | IDX | no | IDX |
| `monto_total` | DECIMAL(14,2) | — | no | — |
| `monto_iva` | DECIMAL(12,2) | — | no | — |
| `monto_no_sujeto` | DECIMAL(12,2) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `estado_fiscal` | VARCHAR(20) | IDX | no | CK, IDX |
| `url_pdf` | VARCHAR(255) | — | sí | NULL |
| `url_xml` | VARCHAR(255) | — | sí | NULL |
| `hash_documento` | VARCHAR(64) | — | no | — |
| `qr_verificacion` | VARCHAR(255) | — | sí | NULL |
| `leyenda` | VARCHAR(200) | — | sí | NULL |
| `anulada_en` | TIMESTAMPTZ | — | sí | NULL |
| `motivo_anulacion` | VARCHAR(200) | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_factura_offline_evento` | CHECK | `estado_fiscal`, `evento_significativo_id` |
| `uq_factura_correlativo` | UNIQUE | `nit_emisor`, `sucursal`, `punto_venta`, `numero_factura` |
| `uq_factura_cuf` | UNIQUE | `cuf` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `datos_facturacion_id` | [[datos_facturacion]] | 11 | no | [[factura_electronica.datos_facturacion_id → datos_facturacion]] |
| `devengo_id` | [[devengo_comision]] | 11 | sí | [[factura_electronica.devengo_id → devengo_comision]] |
| `evento_significativo_id` | [[evento_significativo_sin]] | 11 | sí | [[factura_electronica.evento_significativo_id → evento_significativo_sin]] |
| `lote_envio_sin_id` | [[lote_envio_sin]] | 11 | sí | [[factura_electronica.lote_envio_sin_id → lote_envio_sin]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[factura_electronica.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[factura_publicidad]] | `factura_electronica_id` | ↗ 14 | [[factura_publicidad.factura_electronica_id → factura_electronica]] |
| [[nota_credito_debito]] | `factura_id` | 11 | [[nota_credito_debito.factura_id → factura_electronica]] |

## Entidades vecinas

[[datos_facturacion]] · [[devengo_comision]] · [[evento_significativo_sin]] · [[factura_publicidad]] · [[lote_envio_sin]] · [[nota_credito_debito]] · [[usuario]]

## Notas del modelo

> Facturacion electronica boliviana: cuf, cufd,
> codigo de control, sucursal y punto de venta.
> UNIQUE (sucursal, punto_venta, numero_factura) y
> UNIQUE (cuf). Emision offline permitida con
> estado EMITIDA_OFFLINE y envio posterior por lote.
> Una factura no se edita ni se borra: se anula y
> se emite nota de credito.

## Ver también

- Justificación de negocio: [[11_tarifas_comisiones]]
- Diagramas: `docs/entidades/11_tarifas_comisiones.puml`
- Índice: [[_Entidades]] · [[Index]]
