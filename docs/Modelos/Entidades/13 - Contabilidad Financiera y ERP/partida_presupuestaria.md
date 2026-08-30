---
tags:
  - entidad
  - modulo/13-contabilidad-financiera-y-erp
tabla: partida_presupuestaria
clase: PartidaPresupuestaria
modulo: "13 — Contabilidad Financiera y ERP"
clave_primaria: [id]
columnas: 7
fk_salientes: 3
fk_entrantes: 0
append_only: false
---

# `partida_presupuestaria`

> Módulo [[13_contabilidad_erp|13 — Contabilidad Financiera y ERP]] · clase `PartidaPresupuestaria`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `presupuesto_id` | UUID | FK IDX | no | FK, IDX |
| `cuenta_contable_id` | UUID | FK IDX | no | FK, IDX, M3 |
| `periodo_contable_id` | UUID | FK IDX | no | FK, IDX |
| `monto_presupuestado` | DECIMAL(14,2) | — | no | CK: > 0 |
| `moneda` | CHAR(3) | — | no | — |
| `monto_ejecutado` | DECIMAL(14,2) | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_partida_presupuesto_cuenta_periodo` | UNIQUE | `presupuesto_id`, `cuenta_contable_id`, `periodo_contable_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_contable_id` | [[cuenta_contable]] | ↗ 03 | no | [[partida_presupuestaria.cuenta_contable_id → cuenta_contable]] |
| `periodo_contable_id` | [[periodo_contable]] | 13 | no | [[partida_presupuestaria.periodo_contable_id → periodo_contable]] |
| `presupuesto_id` | [[presupuesto]] | 13 | no | [[partida_presupuestaria.presupuesto_id → presupuesto]] |

## Entidades vecinas

[[cuenta_contable]] · [[periodo_contable]] · [[presupuesto]]

## Ver también

- Justificación de negocio: [[13_contabilidad_erp]]
- Diagramas: `docs/entidades/13_contabilidad_erp.puml`
- Índice: [[_Entidades]] · [[Index]]
