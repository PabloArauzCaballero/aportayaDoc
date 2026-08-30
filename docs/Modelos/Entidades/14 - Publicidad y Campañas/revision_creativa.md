---
tags:
  - entidad
  - modulo/14-publicidad-y-campanas
tabla: revision_creativa
clase: RevisionCreativa
modulo: "14 — Publicidad y Campañas"
clave_primaria: [id]
columnas: 6
fk_salientes: 2
fk_entrantes: 0
append_only: false
---

# `revision_creativa`

> Módulo [[14_publicidad_campanas|14 — Publicidad y Campañas]] · clase `RevisionCreativa`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `pieza_creativa_id` | UUID | FK IDX | no | FK, IDX |
| `revisada_por` | UUID | FK | no | FK |
| `decision` | VARCHAR(10) | — | no | CK |
| `motivo` | VARCHAR(300) | — | sí | NULL |
| `revisada_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_revision_creativa_motivo` | CHECK | `decision`, `motivo` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `pieza_creativa_id` | [[pieza_creativa]] | 14 | no | [[revision_creativa.pieza_creativa_id → pieza_creativa]] |
| `revisada_por` | [[usuario]] | ↗ 01 | no | [[revision_creativa.revisada_por → usuario]] |

## Entidades vecinas

[[pieza_creativa]] · [[usuario]]

## Ver también

- Justificación de negocio: [[14_publicidad_campanas]]
- Diagramas: `docs/entidades/14_publicidad_campanas.puml`
- Índice: [[_Entidades]] · [[Index]]
