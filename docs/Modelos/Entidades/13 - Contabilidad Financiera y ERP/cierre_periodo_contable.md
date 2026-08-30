---
tags:
  - entidad
  - modulo/13-contabilidad-financiera-y-erp
  - append-only
tabla: cierre_periodo_contable
clase: CierrePeriodoContable
modulo: "13 — Contabilidad Financiera y ERP"
clave_primaria: [id]
columnas: 8
fk_salientes: 2
fk_entrantes: 0
append_only: true
---

# `cierre_periodo_contable`

> Módulo [[13_contabilidad_erp|13 — Contabilidad Financiera y ERP]] · clase `CierrePeriodoContable` · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `periodo_contable_id` | UUID | FK UQ | no | FK, UQ |
| `cerrado_en` | TIMESTAMPTZ | — | no | — |
| `cerrado_por` | UUID | FK | no | FK |
| `total_debe` | DECIMAL(18,2) | — | no | — |
| `total_haber` | DECIMAL(18,2) | — | no | — |
| `diferencia` | DECIMAL(18,2) | — | no | GENERATED |
| `observaciones` | VARCHAR(300) | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_cierre_periodo_cuadrado` | CHECK | `total_debe`, `total_haber` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cerrado_por` | [[usuario]] | ↗ 01 | no | [[cierre_periodo_contable.cerrado_por → usuario]] |
| `periodo_contable_id` | [[periodo_contable]] | 13 | no | [[cierre_periodo_contable.periodo_contable_id → periodo_contable]] |

## Entidades vecinas

[[periodo_contable]] · [[usuario]]

## Ver también

- Justificación de negocio: [[13_contabilidad_erp]]
- Diagramas: `docs/entidades/13_contabilidad_erp.puml`
- Índice: [[_Entidades]] · [[Index]]
