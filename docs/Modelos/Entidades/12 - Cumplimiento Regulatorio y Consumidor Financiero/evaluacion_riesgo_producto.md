---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: evaluacion_riesgo_producto
clase: EvaluacionRiesgoProducto
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
clave_primaria: [id]
columnas: 10
fk_salientes: 1
fk_entrantes: 0
append_only: false
---

# `evaluacion_riesgo_producto`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `EvaluacionRiesgoProducto`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `producto` | VARCHAR(60) | — | no | — |
| `version` | SMALLINT | — | no | — |
| `aprobada_por` | UUID | FK | sí | FK, NULL |
| `riesgos_identificados` | JSONB | — | no | — |
| `nivel_riesgo_lft` | VARCHAR(6) | — | no | CK |
| `controles_definidos` | JSONB | — | no | — |
| `requiere_no_objecion` | BOOLEAN | — | no | — |
| `estado` | VARCHAR(15) | — | no | CK |
| `fecha_aprobacion` | DATE | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_evaluacion_no_objecion` | CHECK | `aprobada_por`, `estado`, `fecha_aprobacion`, `requiere_no_objecion` |
| `ck_evaluacion_vigente_aprobada` | CHECK | `estado`, `fecha_aprobacion` |
| `uq_evaluacion_producto_version` | UNIQUE | `producto`, `version` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `aprobada_por` | [[usuario]] | ↗ 01 | sí | [[evaluacion_riesgo_producto.aprobada_por → usuario]] |

## Entidades vecinas

[[usuario]]

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
