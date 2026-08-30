---
tags:
  - entidad
  - modulo/02-grupos-cupos-turnos-y-gobernanza
tabla: dia_no_habil
clase: DiaNoHabil
modulo: "02 — Grupos, Cupos, Turnos y Gobernanza"
clave_primaria: [id]
columnas: 5
fk_salientes: 1
fk_entrantes: 0
append_only: false
---

# `dia_no_habil`

> Módulo [[02_grupos_turnos|02 — Grupos, Cupos, Turnos y Gobernanza]] · clase `DiaNoHabil`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `fecha` | DATE | UQ | no | UQ+alcance+grupo_id |
| `descripcion` | VARCHAR(120) | — | no | — |
| `alcance` | VARCHAR(15) | — | no | CK |
| `grupo_id` | UUID | FK | sí | FK, NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_dia_no_habil_ambito` | CHECK | `alcance`, `grupo_id` |
| `uq_dia_no_habil` | UNIQUE | expresión |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `grupo_id` | [[grupo]] | 02 | sí | [[dia_no_habil.grupo_id → grupo]] |

## Entidades vecinas

[[grupo]]

## Ver también

- Justificación de negocio: [[02_grupos_turnos]]
- Diagramas: `docs/entidades/02_grupos_turnos.puml`
- Índice: [[_Entidades]] · [[Index]]
