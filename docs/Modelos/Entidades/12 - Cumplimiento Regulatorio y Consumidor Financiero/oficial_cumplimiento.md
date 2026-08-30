---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: oficial_cumplimiento
clase: OficialCumplimiento
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
clave_primaria: [id]
columnas: 8
fk_salientes: 1
fk_entrantes: 0
append_only: false
---

# `oficial_cumplimiento`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `OficialCumplimiento`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX, M1 |
| `tipo` | VARCHAR(10) | — | no | CK |
| `fecha_designacion` | DATE | — | no | — |
| `acta_designacion` | VARCHAR(80) | — | no | — |
| `comunicada_al_regulador_en` | DATE | — | sí | NULL |
| `fecha_baja` | DATE | — | sí | NULL |
| `activo` | BOOLEAN | IDX | no | IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_oficial_baja_coherente` | CHECK | `activo`, `fecha_baja` |
| `ck_oficial_baja_posterior` | CHECK | `fecha_baja`, `fecha_designacion` |
| `uq_oficial_titular_activo` | UNIQUE parcial | expresión |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[oficial_cumplimiento.usuario_id → usuario]] |

## Entidades vecinas

[[usuario]]

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
