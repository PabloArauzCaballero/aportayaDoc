---
tags:
  - entidad
  - modulo/13-contabilidad-financiera-y-erp
tabla: periodo_contable
clase: PeriodoContable
modulo: "13 — Contabilidad Financiera y ERP"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 6
fk_salientes: 1
fk_entrantes: 5
append_only: false
---

# `periodo_contable`

> Módulo [[13_contabilidad_erp|13 — Contabilidad Financiera y ERP]] · clase `PeriodoContable` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `ejercicio_fiscal_id` | UUID | FK IDX | no | FK, IDX |
| `mes` | SMALLINT | — | no | CK: 1-12 |
| `fecha_inicio` | DATE | — | no | — |
| `fecha_fin` | DATE | — | no | — |
| `estado` | VARCHAR(10) | IDX | no | CK, IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_periodo_contable_rango` | CHECK | `fecha_fin`, `fecha_inicio` |
| `uq_periodo_contable_ejercicio_mes` | UNIQUE | `ejercicio_fiscal_id`, `mes` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `ejercicio_fiscal_id` | [[ejercicio_fiscal]] | 13 | no | [[periodo_contable.ejercicio_fiscal_id → ejercicio_fiscal]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[asiento_contable]] | `periodo_contable_id` | ↗ 03 | [[asiento_contable.periodo_contable_id → periodo_contable]] |
| [[cierre_periodo_contable]] | `periodo_contable_id` | 13 | [[cierre_periodo_contable.periodo_contable_id → periodo_contable]] |
| [[depreciacion_activo]] | `periodo_contable_id` | 13 | [[depreciacion_activo.periodo_contable_id → periodo_contable]] |
| [[estado_financiero_generado]] | `periodo_contable_id` | 13 | [[estado_financiero_generado.periodo_contable_id → periodo_contable]] |
| [[partida_presupuestaria]] | `periodo_contable_id` | 13 | [[partida_presupuestaria.periodo_contable_id → periodo_contable]] |

## Entidades vecinas

[[asiento_contable]] · [[cierre_periodo_contable]] · [[depreciacion_activo]] · [[ejercicio_fiscal]] · [[estado_financiero_generado]] · [[partida_presupuestaria]]

## Notas del modelo

> <<UQ>> compuesta (ejercicio_fiscal_id, mes).
> estado ABIERTO -> CERRADO es de un solo sentido:
> no hay CK que lo impida en el modelo, la
> irreversibilidad la garantiza que no existe ningun
> caso de uso de "reabrir" (fase 5).

## Ver también

- Justificación de negocio: [[13_contabilidad_erp]]
- Diagramas: `docs/entidades/13_contabilidad_erp.puml`
- Índice: [[_Entidades]] · [[Index]]
