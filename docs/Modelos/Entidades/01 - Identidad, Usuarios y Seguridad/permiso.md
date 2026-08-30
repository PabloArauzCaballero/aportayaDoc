---
tags:
  - entidad
  - modulo/01-identidad-usuarios-y-seguridad
tabla: permiso
clase: Permiso
modulo: "01 — Identidad, Usuarios y Seguridad"
clave_primaria: [id]
columnas: 6
fk_salientes: 0
fk_entrantes: 1
append_only: false
---

# `permiso`

> Módulo [[01_identidad_usuarios|01 — Identidad, Usuarios y Seguridad]] · clase `Permiso`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `codigo` | VARCHAR(60) | UQ | no | UQ |
| `descripcion` | VARCHAR(160) | — | no | — |
| `recurso` | VARCHAR(40) | — | no | — |
| `accion` | VARCHAR(30) | — | no | — |
| `requiere_mfa` | BOOLEAN | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_permiso_decision_exige_mfa` | CHECK | `accion`, `requiere_mfa` |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[rol_permiso]] | `permiso_id` | 01 | [[rol_permiso.permiso_id → permiso]] |

## Entidades vecinas

[[rol_permiso]]

## Ver también

- Justificación de negocio: [[01_identidad_usuarios]]
- Diagramas: `docs/entidades/01_identidad_usuarios.puml`
- Índice: [[_Entidades]] · [[Index]]
