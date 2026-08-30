---
tags:
  - entidad
  - modulo/06-transparencia-y-reputacion
tabla: alerta_riesgo
clase: AlertaRiesgo
modulo: "06 — Transparencia y Reputación"
clave_primaria: [id]
columnas: 10
fk_salientes: 0
fk_entrantes: 0
append_only: false
---

# `alerta_riesgo`

> Módulo [[06_transparencia_reputacion|06 — Transparencia y Reputación]] · clase `AlertaRiesgo`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `ambito` | VARCHAR(15) | — | no | CK |
| `ambito_id` | UUID | IDX | no | IDX |
| `codigo` | VARCHAR(40) | — | no | CK |
| `severidad` | VARCHAR(10) | — | no | CK |
| `descripcion` | VARCHAR(300) | — | no | — |
| `evidencia` | JSONB | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `detectada_en` | TIMESTAMPTZ | — | no | — |
| `cerrada_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_alerta_riesgo_cierre` | CHECK | `cerrada_en`, `estado` |

## Ver también

- Justificación de negocio: [[06_transparencia_reputacion]]
- Diagramas: `docs/entidades/06_transparencia_reputacion.puml`
- Índice: [[_Entidades]] · [[Index]]
