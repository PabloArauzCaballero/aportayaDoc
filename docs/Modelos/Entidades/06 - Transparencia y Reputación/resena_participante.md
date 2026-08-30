---
tags:
  - entidad
  - modulo/06-transparencia-y-reputacion
tabla: resena_participante
clase: ResenaParticipante
modulo: "06 — Transparencia y Reputación"
clave_primaria: [id]
columnas: 10
fk_salientes: 4
fk_entrantes: 0
append_only: false
---

# `resena_participante`

> Módulo [[06_transparencia_reputacion|06 — Transparencia y Reputación]] · clase `ResenaParticipante`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `grupo_id` | UUID | FK IDX | no | FK, IDX |
| `autor_participante_id` | UUID | FK | no | FK |
| `evaluado_usuario_id` | UUID | FK IDX | no | FK, IDX |
| `calificacion` | SMALLINT | — | no | CK: 1..5 |
| `comentario` | VARCHAR(500) | — | sí | NULL |
| `dimension` | VARCHAR(20) | — | no | CK |
| `estado_moderacion` | VARCHAR(15) | — | no | CK |
| `moderada_por` | UUID | FK | sí | FK, NULL |
| `creada_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_resena_moderada` | CHECK | `estado_moderacion`, `moderada_por` |
| `uq_resena_autor_evaluado` | UNIQUE | `grupo_id`, `autor_participante_id`, `evaluado_usuario_id`, `dimension` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `autor_participante_id` | [[participante]] | ↗ 02 | no | [[resena_participante.autor_participante_id → participante]] |
| `evaluado_usuario_id` | [[usuario]] | ↗ 01 | no | [[resena_participante.evaluado_usuario_id → usuario]] |
| `grupo_id` | [[grupo]] | ↗ 02 | no | [[resena_participante.grupo_id → grupo]] |
| `moderada_por` | [[usuario]] | ↗ 01 | sí | [[resena_participante.moderada_por → usuario]] |

## Entidades vecinas

[[grupo]] · [[participante]] · [[usuario]]

## Ver también

- Justificación de negocio: [[06_transparencia_reputacion]]
- Diagramas: `docs/entidades/06_transparencia_reputacion.puml`
- Índice: [[_Entidades]] · [[Index]]
