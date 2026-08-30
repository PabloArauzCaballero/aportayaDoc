---
tags:
  - entidad
  - modulo/14-publicidad-y-campanas
tabla: espacio_publicitario
clase: EspacioPublicitario
modulo: "14 — Publicidad y Campañas"
estereotipo: Política configurable
clave_primaria: [id]
columnas: 6
fk_salientes: 0
fk_entrantes: 1
append_only: false
---

# `espacio_publicitario`

> Módulo [[14_publicidad_campanas|14 — Publicidad y Campañas]] · clase `EspacioPublicitario` · Política configurable

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `codigo` | VARCHAR(30) | UQ | no | UQ |
| `nombre` | VARCHAR(80) | — | no | — |
| `tipo` | VARCHAR(25) | — | no | CK |
| `capacidad_maxima_simultanea` | SMALLINT | — | no | — |
| `activo` | BOOLEAN | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_espacio_pub_capacidad` | CHECK | `capacidad_maxima_simultanea` |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[conjunto_anuncios]] | `espacio_publicitario_id` | 14 | [[conjunto_anuncios.espacio_publicitario_id → espacio_publicitario]] |

## Entidades vecinas

[[conjunto_anuncios]]

## Ver también

- Justificación de negocio: [[14_publicidad_campanas]]
- Diagramas: `docs/entidades/14_publicidad_campanas.puml`
- Índice: [[_Entidades]] · [[Index]]
