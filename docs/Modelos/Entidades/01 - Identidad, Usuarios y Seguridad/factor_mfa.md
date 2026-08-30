---
tags:
  - entidad
  - modulo/01-identidad-usuarios-y-seguridad
tabla: factor_mfa
clase: FactorMFA
modulo: "01 — Identidad, Usuarios y Seguridad"
clave_primaria: [id]
columnas: 9
fk_salientes: 1
fk_entrantes: 0
append_only: false
---

# `factor_mfa`

> Módulo [[01_identidad_usuarios|01 — Identidad, Usuarios y Seguridad]] · clase `FactorMFA`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `tipo` | VARCHAR(20) | — | no | CK |
| `secreto_cifrado` | VARCHAR(255) | — | no | — |
| `version_llave` | SMALLINT | — | no | — |
| `activo` | BOOLEAN | — | no | — |
| `es_principal` | BOOLEAN | — | no | — |
| `confirmado_en` | TIMESTAMPTZ | — | sí | NULL |
| `ultimo_uso_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_factor_mfa_version_llave` | CHECK | `version_llave` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `usuario_id` | [[usuario]] | 01 | no | [[factor_mfa.usuario_id → usuario]] |

## Entidades vecinas

[[usuario]]

## Ver también

- Justificación de negocio: [[01_identidad_usuarios]]
- Diagramas: `docs/entidades/01_identidad_usuarios.puml`
- Índice: [[_Entidades]] · [[Index]]
