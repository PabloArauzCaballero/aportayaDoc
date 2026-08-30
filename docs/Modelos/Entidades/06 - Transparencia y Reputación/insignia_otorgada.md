---
tags:
  - entidad
  - modulo/06-transparencia-y-reputacion
tabla: insignia_otorgada
clase: InsigniaOtorgada
modulo: "06 — Transparencia y Reputación"
clave_primaria: [id]
columnas: 6
fk_salientes: 2
fk_entrantes: 0
append_only: false
---

# `insignia_otorgada`

> Módulo [[06_transparencia_reputacion|06 — Transparencia y Reputación]] · clase `InsigniaOtorgada`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `insignia_id` | UUID | FK | no | FK |
| `otorgada_en` | TIMESTAMPTZ | — | no | — |
| `revocada_en` | TIMESTAMPTZ | — | sí | NULL |
| `motivo_revocacion` | VARCHAR(160) | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_insignia_revocacion_motivada` | CHECK | `motivo_revocacion`, `revocada_en` |
| `uq_insignia_usuario` | UNIQUE | `usuario_id`, `insignia_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `insignia_id` | [[insignia_logro]] | 06 | no | [[insignia_otorgada.insignia_id → insignia_logro]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[insignia_otorgada.usuario_id → usuario]] |

## Entidades vecinas

[[insignia_logro]] · [[usuario]]

## Ver también

- Justificación de negocio: [[06_transparencia_reputacion]]
- Diagramas: `docs/entidades/06_transparencia_reputacion.puml`
- Índice: [[_Entidades]] · [[Index]]
