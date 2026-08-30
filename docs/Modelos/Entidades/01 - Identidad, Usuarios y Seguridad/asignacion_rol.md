---
tags:
  - entidad
  - modulo/01-identidad-usuarios-y-seguridad
tabla: asignacion_rol
clase: AsignacionRol
modulo: "01 — Identidad, Usuarios y Seguridad"
clave_primaria: [id]
columnas: 10
fk_salientes: 3
fk_entrantes: 0
append_only: false
---

# `asignacion_rol`

> Módulo [[01_identidad_usuarios|01 — Identidad, Usuarios y Seguridad]] · clase `AsignacionRol`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `rol_id` | UUID | FK | no | FK |
| `ambito` | VARCHAR(15) | — | no | CK |
| `ambito_id` | UUID | — | sí | NULL, grupo_id (M2) |
| `otorgada_por` | UUID | FK | no | FK |
| `otorgada_en` | TIMESTAMPTZ | — | no | — |
| `vigente_hasta` | TIMESTAMPTZ | — | sí | NULL |
| `revocada_en` | TIMESTAMPTZ | — | sí | NULL |
| `motivo_revocacion` | VARCHAR(120) | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_asignacion_ambito_completo` | CHECK | `ambito`, `ambito_id` |
| `ck_asignacion_no_autoasignada` | CHECK | `otorgada_por`, `usuario_id` |
| `ck_asignacion_revocacion_motivada` | CHECK | `motivo_revocacion`, `revocada_en` |
| `ix_asignacion_por_vencer` | INDEX parcial | `vigente_hasta` |
| `uq_asignacion_vigente` | UNIQUE parcial | expresión |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `otorgada_por` | [[usuario]] | 01 | no | [[asignacion_rol.otorgada_por → usuario]] |
| `rol_id` | [[rol]] | 01 | no | [[asignacion_rol.rol_id → rol]] |
| `usuario_id` | [[usuario]] | 01 | no | [[asignacion_rol.usuario_id → usuario]] |

## Entidades vecinas

[[rol]] · [[usuario]]

## Notas del modelo

> ambito_id apunta a grupo.id (modulo 2)
> cuando ambito = 'GRUPO'. Es referencia
> polimorfica: se valida por trigger, no
> por FK fisica.

## Ver también

- Justificación de negocio: [[01_identidad_usuarios]]
- Diagramas: `docs/entidades/01_identidad_usuarios.puml`
- Índice: [[_Entidades]] · [[Index]]
