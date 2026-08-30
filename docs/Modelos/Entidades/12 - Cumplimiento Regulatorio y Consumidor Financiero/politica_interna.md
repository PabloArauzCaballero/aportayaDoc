---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: politica_interna
clase: PoliticaInterna
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
estereotipo: Política configurable
clave_primaria: [id]
columnas: 13
fk_salientes: 2
fk_entrantes: 1
append_only: false
---

# `politica_interna`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `PoliticaInterna` · Política configurable

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `acta_comite_id` | UUID | FK | sí | FK, NULL |
| `responsable_id` | UUID | FK | sí | FK, NULL |
| `codigo` | VARCHAR(30) | UQ | no | UQ+version |
| `tipo` | VARCHAR(15) | — | no | CK |
| `materia` | VARCHAR(30) | IDX | no | CK, IDX |
| `version` | SMALLINT | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `url_documento` | VARCHAR(255) | — | no | — |
| `hash_documento` | VARCHAR(64) | — | no | — |
| `aprobada_por_directorio` | BOOLEAN | — | no | — |
| `vigente_desde` | DATE | — | no | — |
| `proxima_revision` | DATE | IDX | no | IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_politica_acta` | CHECK | `acta_comite_id`, `aprobada_por_directorio`, `estado` |
| `ck_politica_revision` | CHECK | `proxima_revision`, `vigente_desde` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `acta_comite_id` | [[acta_comite]] | 12 | sí | [[politica_interna.acta_comite_id → acta_comite]] |
| `responsable_id` | [[usuario]] | ↗ 01 | sí | [[politica_interna.responsable_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[plan_continuidad]] | `politica_interna_id` | 12 | [[plan_continuidad.politica_interna_id → politica_interna]] |

## Entidades vecinas

[[acta_comite]] · [[plan_continuidad]] · [[usuario]]

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
