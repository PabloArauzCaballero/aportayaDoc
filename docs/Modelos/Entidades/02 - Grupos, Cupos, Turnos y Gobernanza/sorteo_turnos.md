---
tags:
  - entidad
  - modulo/02-grupos-cupos-turnos-y-gobernanza
tabla: sorteo_turnos
clase: SorteoTurnos
modulo: "02 — Grupos, Cupos, Turnos y Gobernanza"
clave_primaria: [id]
columnas: 14
fk_salientes: 2
fk_entrantes: 0
append_only: false
---

# `sorteo_turnos`

> Módulo [[02_grupos_turnos|02 — Grupos, Cupos, Turnos y Gobernanza]] · clase `SorteoTurnos`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `grupo_id` | UUID | FK UQ | no | FK parcial |
| `algoritmo` | VARCHAR(30) | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `hash_semilla_previo` | VARCHAR(64) | — | no | — |
| `aportes_entropia` | JSONB | — | sí | NULL |
| `fecha_compromiso` | TIMESTAMPTZ | — | no | — |
| `fecha_revelado_prevista` | TIMESTAMPTZ | — | sí | NULL |
| `semilla_servidor` | VARCHAR(128) | — | sí | NULL hasta revelar |
| `semilla_publica` | VARCHAR(128) | — | no | — |
| `resultado` | JSONB | — | no | — |
| `ejecutado_por` | UUID | FK | no | FK |
| `fecha_ejecucion` | TIMESTAMPTZ | — | no | — |
| `anulado_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_sorteo_compromiso` | CHECK | `fecha_compromiso`, `hash_semilla_previo` |
| `ck_sorteo_revelado` | CHECK | `estado`, `fecha_ejecucion`, `semilla_servidor` |
| `uq_sorteo_grupo` | UNIQUE | `grupo_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `ejecutado_por` | [[usuario]] | ↗ 01 | no | [[sorteo_turnos.ejecutado_por → usuario]] |
| `grupo_id` | [[grupo]] | 02 | no | [[sorteo_turnos.grupo_id → grupo]] |

## Entidades vecinas

[[grupo]] · [[usuario]]

## Ver también

- Justificación de negocio: [[02_grupos_turnos]]
- Diagramas: `docs/entidades/02_grupos_turnos.puml`
- Índice: [[_Entidades]] · [[Index]]
