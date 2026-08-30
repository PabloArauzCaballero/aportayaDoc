---
tags:
  - entidad
  - modulo/13-contabilidad-financiera-y-erp
tabla: presupuesto
clase: Presupuesto
modulo: "13 — Contabilidad Financiera y ERP"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 7
fk_salientes: 3
fk_entrantes: 1
append_only: false
---

# `presupuesto`

> Módulo [[13_contabilidad_erp|13 — Contabilidad Financiera y ERP]] · clase `Presupuesto` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `centro_costo_id` | UUID | FK IDX | no | FK, IDX |
| `ejercicio_fiscal_id` | UUID | FK IDX | no | FK, IDX |
| `nombre` | VARCHAR(100) | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `aprobado_por` | UUID | FK | sí | FK, NULL |
| `aprobado_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_presupuesto_aprobacion` | CHECK | `aprobado_en`, `aprobado_por`, `estado` |
| `uq_presupuesto_centro_ejercicio` | UNIQUE | `centro_costo_id`, `ejercicio_fiscal_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `aprobado_por` | [[usuario]] | ↗ 01 | sí | [[presupuesto.aprobado_por → usuario]] |
| `centro_costo_id` | [[centro_costo]] | 13 | no | [[presupuesto.centro_costo_id → centro_costo]] |
| `ejercicio_fiscal_id` | [[ejercicio_fiscal]] | 13 | no | [[presupuesto.ejercicio_fiscal_id → ejercicio_fiscal]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[partida_presupuestaria]] | `presupuesto_id` | 13 | [[partida_presupuestaria.presupuesto_id → presupuesto]] |

## Entidades vecinas

[[centro_costo]] · [[ejercicio_fiscal]] · [[partida_presupuestaria]] · [[usuario]]

## Ver también

- Justificación de negocio: [[13_contabilidad_erp]]
- Diagramas: `docs/entidades/13_contabilidad_erp.puml`
- Índice: [[_Entidades]] · [[Index]]
