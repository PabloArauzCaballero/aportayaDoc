---
tags:
  - entidad
  - modulo/13-contabilidad-financiera-y-erp
  - append-only
tabla: depreciacion_activo
clase: DepreciacionActivo
modulo: "13 — Contabilidad Financiera y ERP"
clave_primaria: [id]
columnas: 7
fk_salientes: 3
fk_entrantes: 0
append_only: true
---

# `depreciacion_activo`

> Módulo [[13_contabilidad_erp|13 — Contabilidad Financiera y ERP]] · clase `DepreciacionActivo` · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `activo_fijo_id` | UUID | FK IDX | no | FK, IDX |
| `periodo_contable_id` | UUID | FK IDX | no | FK, IDX |
| `monto` | DECIMAL(14,2) | — | no | CK: > 0 |
| `moneda` | CHAR(3) | — | no | — |
| `asiento_contable_id` | UUID | FK | sí | FK, NULL, M3 |
| `calculada_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_depreciacion_activo_periodo` | UNIQUE | `activo_fijo_id`, `periodo_contable_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `activo_fijo_id` | [[activo_fijo]] | 13 | no | [[depreciacion_activo.activo_fijo_id → activo_fijo]] |
| `asiento_contable_id` | [[asiento_contable]] | ↗ 03 | sí | [[depreciacion_activo.asiento_contable_id → asiento_contable]] |
| `periodo_contable_id` | [[periodo_contable]] | 13 | no | [[depreciacion_activo.periodo_contable_id → periodo_contable]] |

## Entidades vecinas

[[activo_fijo]] · [[asiento_contable]] · [[periodo_contable]]

## Ver también

- Justificación de negocio: [[13_contabilidad_erp]]
- Diagramas: `docs/entidades/13_contabilidad_erp.puml`
- Índice: [[_Entidades]] · [[Index]]
