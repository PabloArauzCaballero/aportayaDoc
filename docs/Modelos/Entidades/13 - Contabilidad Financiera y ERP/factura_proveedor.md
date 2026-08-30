---
tags:
  - entidad
  - modulo/13-contabilidad-financiera-y-erp
  - append-only
tabla: factura_proveedor
clase: FacturaProveedor
modulo: "13 — Contabilidad Financiera y ERP"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 14
fk_salientes: 5
fk_entrantes: 2
append_only: true
---

# `factura_proveedor`

> Módulo [[13_contabilidad_erp|13 — Contabilidad Financiera y ERP]] · clase `FacturaProveedor` · Raíz de agregado · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `tercero_comercial_id` | UUID | FK IDX | no | FK, IDX |
| `orden_compra_id` | UUID | FK IDX | sí | FK, NULL, IDX |
| `centro_costo_id` | UUID | FK | sí | FK, NULL |
| `numero_factura` | VARCHAR(30) | — | no | — |
| `fecha_emision` | DATE | — | no | — |
| `fecha_vencimiento` | DATE | — | no | — |
| `monto` | DECIMAL(14,2) | — | no | CK: > 0 |
| `moneda` | CHAR(3) | — | no | — |
| `monto_pagado` | DECIMAL(14,2) | — | no | — |
| `saldo_pendiente` | DECIMAL(14,2) | — | no | GENERATED |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `aprobada_por` | UUID | FK | sí | FK, NULL |
| `asiento_contable_id` | UUID | FK | sí | FK, NULL, M3 |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_factura_proveedor_aprobacion` | CHECK | `aprobada_por`, `estado` |
| `ck_factura_proveedor_pagado` | CHECK | `monto`, `monto_pagado` |
| `ck_factura_proveedor_vencimiento` | CHECK | `fecha_emision`, `fecha_vencimiento` |
| `uq_factura_proveedor_numero` | UNIQUE | `tercero_comercial_id`, `numero_factura` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `aprobada_por` | [[usuario]] | ↗ 01 | sí | [[factura_proveedor.aprobada_por → usuario]] |
| `asiento_contable_id` | [[asiento_contable]] | ↗ 03 | sí | [[factura_proveedor.asiento_contable_id → asiento_contable]] |
| `centro_costo_id` | [[centro_costo]] | 13 | sí | [[factura_proveedor.centro_costo_id → centro_costo]] |
| `orden_compra_id` | [[orden_compra]] | 13 | sí | [[factura_proveedor.orden_compra_id → orden_compra]] |
| `tercero_comercial_id` | [[tercero_comercial]] | 13 | no | [[factura_proveedor.tercero_comercial_id → tercero_comercial]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[activo_fijo]] | `factura_proveedor_id` | 13 | [[activo_fijo.factura_proveedor_id → factura_proveedor]] |
| [[pago_a_proveedor]] | `factura_proveedor_id` | 13 | [[pago_a_proveedor.factura_proveedor_id → factura_proveedor]] |

## Entidades vecinas

[[activo_fijo]] · [[asiento_contable]] · [[centro_costo]] · [[orden_compra]] · [[pago_a_proveedor]] · [[tercero_comercial]] · [[usuario]]

## Ver también

- Justificación de negocio: [[13_contabilidad_erp]]
- Diagramas: `docs/entidades/13_contabilidad_erp.puml`
- Índice: [[_Entidades]] · [[Index]]
