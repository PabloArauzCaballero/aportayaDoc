---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
tabla: respuesta_idempotente
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
clave_primaria: [id]
columnas: 9
fk_salientes: 1
fk_entrantes: 0
append_only: false
---

# `respuesta_idempotente`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]]

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `operacion` | VARCHAR(40) | UQ | no | UQ+usuario_id+clave_idempotencia |
| `clave_idempotencia` | VARCHAR(100) | — | no | — |
| `hash_solicitud` | VARCHAR(64) | — | no | — |
| `codigo_http` | SMALLINT | — | no | — |
| `cuerpo_respuesta` | JSONB | — | no | — |
| `registrada_en` | TIMESTAMPTZ | — | no | — |
| `expira_en` | TIMESTAMPTZ | IDX | no | IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_respuesta_idem_expira` | CHECK | `expira_en`, `registrada_en` |
| `ck_respuesta_idem_hash` | CHECK | `hash_solicitud` |
| `ck_respuesta_idem_http` | CHECK | `codigo_http` |
| `ix_respuesta_idem_expiradas` | INDEX | `expira_en` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[respuesta_idempotente.usuario_id → usuario]] |

## Entidades vecinas

[[usuario]]

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
