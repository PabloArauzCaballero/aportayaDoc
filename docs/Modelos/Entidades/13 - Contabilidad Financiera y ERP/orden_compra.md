---
tags:
  - entidad
  - modulo/13-contabilidad-financiera-y-erp
tabla: orden_compra
clase: OrdenCompra
modulo: "13 — Contabilidad Financiera y ERP"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 10
fk_salientes: 3
fk_entrantes: 1
append_only: false
---

# `orden_compra`

> Módulo [[13_contabilidad_erp|13 — Contabilidad Financiera y ERP]] · clase `OrdenCompra` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `tercero_comercial_id` | UUID | FK IDX | no | FK, IDX |
| `centro_costo_id` | UUID | FK | sí | FK, NULL |
| `numero` | VARCHAR(30) | UQ | no | UQ |
| `descripcion` | VARCHAR(300) | — | no | — |
| `monto_total` | DECIMAL(14,2) | — | no | CK: > 0 |
| `moneda` | CHAR(3) | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `aprobada_por` | UUID | FK | sí | FK, NULL |
| `creada_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_orden_compra_aprobacion` | CHECK | `aprobada_por`, `estado` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `aprobada_por` | [[usuario]] | ↗ 01 | sí | [[orden_compra.aprobada_por → usuario]] |
| `centro_costo_id` | [[centro_costo]] | 13 | sí | [[orden_compra.centro_costo_id → centro_costo]] |
| `tercero_comercial_id` | [[tercero_comercial]] | 13 | no | [[orden_compra.tercero_comercial_id → tercero_comercial]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[factura_proveedor]] | `orden_compra_id` | 13 | [[factura_proveedor.orden_compra_id → orden_compra]] |

## Entidades vecinas

[[centro_costo]] · [[factura_proveedor]] · [[tercero_comercial]] · [[usuario]]

## Ver también

- Justificación de negocio: [[13_contabilidad_erp]]
- Diagramas: `docs/entidades/13_contabilidad_erp.puml`
- Índice: [[_Entidades]] · [[Index]]
