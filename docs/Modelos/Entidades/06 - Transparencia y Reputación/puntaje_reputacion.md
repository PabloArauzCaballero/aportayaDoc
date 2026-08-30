---
tags:
  - entidad
  - modulo/06-transparencia-y-reputacion
tabla: puntaje_reputacion
clase: PuntajeReputacion
modulo: "06 — Transparencia y Reputación"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 18
fk_salientes: 2
fk_entrantes: 1
append_only: false
---

# `puntaje_reputacion`

> Módulo [[06_transparencia_reputacion|06 — Transparencia y Reputación]] · clase `PuntajeReputacion` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK UQ | no | FK, UQ |
| `modelo_id` | UUID | FK | no | FK |
| `puntaje` | DECIMAL(6,2) | IDX | no | IDX |
| `nivel_confianza` | VARCHAR(20) | IDX | no | CK, IDX |
| `indice_puntualidad` | DECIMAL(5,2) | — | no | — |
| `tasa_incumplimiento` | DECIMAL(5,2) | — | no | — |
| `monto_total_aportado` | DECIMAL(16,2) | — | no | — |
| `grupos_completados` | SMALLINT | — | no | — |
| `grupos_abandonados` | SMALLINT | — | no | — |
| `incumplimientos_abiertos` | SMALLINT | — | no | — |
| `antiguedad_meses` | SMALLINT | — | no | — |
| `eventos_considerados` | INTEGER | — | no | — |
| `modelo_version` | VARCHAR(20) | — | no | — |
| `vigente_desde` | TIMESTAMPTZ | — | no | — |
| `vigente_hasta` | TIMESTAMPTZ | — | sí | NULL |
| `calculado_en` | TIMESTAMPTZ | — | no | — |
| `proximo_recalculo_en` | TIMESTAMPTZ | IDX | no | IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ex_puntaje_vigente` | EXCLUDE | `usuario_id`, `vigente_desde`, `vigente_hasta` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `modelo_id` | [[modelo_scoring]] | 06 | no | [[puntaje_reputacion.modelo_id → modelo_scoring]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[puntaje_reputacion.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[componente_score]] | `puntaje_id` | 06 | [[componente_score.puntaje_id → puntaje_reputacion]] |

## Entidades vecinas

[[componente_score]] · [[modelo_scoring]] · [[usuario]]

## Notas del modelo

> usuario_id -> usuario.id (M1). Esta tabla es la
> proyeccion materializada; reputacion_usuario del
> modulo 1 se mantiene sincronizada como cache de
> lectura rapida para el login y el perfil.

## Ver también

- Justificación de negocio: [[06_transparencia_reputacion]]
- Diagramas: `docs/entidades/06_transparencia_reputacion.puml`
- Índice: [[_Entidades]] · [[Index]]
