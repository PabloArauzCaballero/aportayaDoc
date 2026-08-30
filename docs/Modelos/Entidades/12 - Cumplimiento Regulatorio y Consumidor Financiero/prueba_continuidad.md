---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: prueba_continuidad
clase: PruebaContinuidad
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
clave_primaria: [id]
columnas: 11
fk_salientes: 3
fk_entrantes: 0
append_only: false
---

# `prueba_continuidad`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `PruebaContinuidad`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `plan_continuidad_id` | UUID | FK IDX | no | FK, IDX |
| `acta_comite_id` | UUID | FK | sí | FK, NULL |
| `ejecutada_por` | UUID | FK | no | FK |
| `tipo` | VARCHAR(25) | — | no | CK |
| `fecha` | DATE | IDX | no | IDX |
| `rto_obtenido_minutos` | INTEGER | — | no | — |
| `rpo_obtenido_minutos` | INTEGER | — | no | — |
| `resultado` | VARCHAR(10) | IDX | no | CK, IDX |
| `hallazgos` | TEXT | — | sí | NULL |
| `evidencia_url` | VARCHAR(255) | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_prueba_resultado` | CHECK | `acta_comite_id`, `resultado`, `rto_obtenido_minutos` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `acta_comite_id` | [[acta_comite]] | 12 | sí | [[prueba_continuidad.acta_comite_id → acta_comite]] |
| `ejecutada_por` | [[usuario]] | ↗ 01 | no | [[prueba_continuidad.ejecutada_por → usuario]] |
| `plan_continuidad_id` | [[plan_continuidad]] | 12 | no | [[prueba_continuidad.plan_continuidad_id → plan_continuidad]] |

## Entidades vecinas

[[acta_comite]] · [[plan_continuidad]] · [[usuario]]

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
