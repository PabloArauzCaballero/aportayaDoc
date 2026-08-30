---
tags:
  - entidad
  - modulo/01-identidad-usuarios-y-seguridad
tabla: documento_identidad
clase: DocumentoIdentidad
modulo: "01 — Identidad, Usuarios y Seguridad"
clave_primaria: [id]
columnas: 14
fk_salientes: 1
fk_entrantes: 1
append_only: false
---

# `documento_identidad`

> Módulo [[01_identidad_usuarios|01 — Identidad, Usuarios y Seguridad]] · clase `DocumentoIdentidad`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `tipo` | VARCHAR(25) | — | no | CK |
| `numero_cifrado` | VARCHAR(255) | — | no | — |
| `version_llave` | SMALLINT | — | no | — |
| `hash_numero` | VARCHAR(64) | UQ | no | UQ, busqueda sin descifrar |
| `complemento` | VARCHAR(10) | — | sí | NULL |
| `pais_emision` | CHAR(2) | — | no | — |
| `fecha_emision` | DATE | — | sí | NULL |
| `fecha_expiracion` | DATE | — | sí | NULL |
| `url_anverso` | VARCHAR(255) | — | no | — |
| `url_reverso` | VARCHAR(255) | — | sí | NULL |
| `hash_archivo` | VARCHAR(64) | — | no | — |
| `estado` | VARCHAR(20) | — | no | CK |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_documento_hash_completo` | CHECK | `hash_numero` |
| `ck_documento_version_llave` | CHECK | `version_llave` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `usuario_id` | [[usuario]] | 01 | no | [[documento_identidad.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[verificacion_kyc]] | `documento_id` | 01 | [[verificacion_kyc.documento_id → documento_identidad]] |

## Entidades vecinas

[[usuario]] · [[verificacion_kyc]]

## Notas del modelo

> numero_cifrado usa cifrado a nivel de columna
> (pgcrypto / KMS). hash_numero permite buscar
> duplicados sin descifrar: evita que la misma
> cedula abra dos cuentas.

## Ver también

- Justificación de negocio: [[01_identidad_usuarios]]
- Diagramas: `docs/entidades/01_identidad_usuarios.puml`
- Índice: [[_Entidades]] · [[Index]]
