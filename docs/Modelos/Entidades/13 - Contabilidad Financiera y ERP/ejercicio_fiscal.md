---
tags:
  - entidad
  - modulo/13-contabilidad-financiera-y-erp
tabla: ejercicio_fiscal
clase: EjercicioFiscal
modulo: "13 — Contabilidad Financiera y ERP"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 7
fk_salientes: 1
fk_entrantes: 2
append_only: false
---

# `ejercicio_fiscal`

> Módulo [[13_contabilidad_erp|13 — Contabilidad Financiera y ERP]] · clase `EjercicioFiscal` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `anio` | SMALLINT | UQ | no | UQ |
| `fecha_inicio` | DATE | — | no | — |
| `fecha_fin` | DATE | — | no | — |
| `estado` | VARCHAR(10) | IDX | no | CK, IDX |
| `cerrado_en` | TIMESTAMPTZ | — | sí | NULL |
| `cerrado_por` | UUID | FK | sí | FK, NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_ejercicio_fiscal_cierre` | CHECK | `cerrado_en`, `cerrado_por`, `estado` |
| `ck_ejercicio_fiscal_rango` | CHECK | `fecha_fin`, `fecha_inicio` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cerrado_por` | [[usuario]] | ↗ 01 | sí | [[ejercicio_fiscal.cerrado_por → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[periodo_contable]] | `ejercicio_fiscal_id` | 13 | [[periodo_contable.ejercicio_fiscal_id → ejercicio_fiscal]] |
| [[presupuesto]] | `ejercicio_fiscal_id` | 13 | [[presupuesto.ejercicio_fiscal_id → ejercicio_fiscal]] |

## Entidades vecinas

[[periodo_contable]] · [[presupuesto]] · [[usuario]]

## Ver también

- Justificación de negocio: [[13_contabilidad_erp]]
- Diagramas: `docs/entidades/13_contabilidad_erp.puml`
- Índice: [[_Entidades]] · [[Index]]
