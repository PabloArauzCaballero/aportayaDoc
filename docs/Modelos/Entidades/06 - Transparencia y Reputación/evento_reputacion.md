---
tags:
  - entidad
  - modulo/06-transparencia-y-reputacion
  - append-only
tabla: evento_reputacion
clase: EventoReputacion
modulo: "06 — Transparencia y Reputación"
clave_primaria: [id]
columnas: 15
fk_salientes: 4
fk_entrantes: 1
append_only: true
---

# `evento_reputacion`

> Módulo [[06_transparencia_reputacion|06 — Transparencia y Reputación]] · clase `EventoReputacion` · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `grupo_id` | UUID | FK IDX | sí | FK, NULL, IDX |
| `participante_id` | UUID | FK | sí | FK, NULL |
| `tipo` | VARCHAR(40) | IDX | no | CK, IDX |
| `referencia_tipo` | VARCHAR(30) | — | no | CK |
| `referencia_origen_id` | UUID | — | sí | NULL, polimorfica |
| `impacto` | DECIMAL(6,2) | — | no | — |
| `factor_afectado` | VARCHAR(40) | — | no | — |
| `descripcion` | VARCHAR(200) | — | no | — |
| `modelo_version` | VARCHAR(20) | — | no | — |
| `es_reversible` | BOOLEAN | — | no | — |
| `revertido_por_id` | UUID | FK | sí | FK, NULL |
| `ocurrido_en` | TIMESTAMPTZ | IDX | no | IDX |
| `registrado_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_evento_reputacion_hecho` | UNIQUE | `usuario_id`, `referencia_tipo`, `referencia_origen_id`, `tipo` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `grupo_id` | [[grupo]] | ↗ 02 | sí | [[evento_reputacion.grupo_id → grupo]] |
| `participante_id` | [[participante]] | ↗ 02 | sí | [[evento_reputacion.participante_id → participante]] |
| `revertido_por_id` | [[evento_reputacion]] | 06 | sí | [[evento_reputacion.revertido_por_id → evento_reputacion]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[evento_reputacion.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[evento_reputacion]] | `revertido_por_id` | 06 | [[evento_reputacion.revertido_por_id → evento_reputacion]] |

## Entidades vecinas

[[evento_reputacion]] · [[grupo]] · [[participante]] · [[usuario]]

## Notas del modelo

> **Append-only y trazable**
> referencia_origen_id apunta segun tipo a:
> obligacion_aporte.id (M3),
> entrega_fondo.id (M4),
> registro_incumplimiento.id / sancion.id (M8),
> acuerdo.id (M2).
> Indice compuesto (usuario_id, ocurrido_en DESC)
> para reconstruir el score en una sola pasada.

## Ver también

- Justificación de negocio: [[06_transparencia_reputacion]]
- Diagramas: `docs/entidades/06_transparencia_reputacion.puml`
- Índice: [[_Entidades]] · [[Index]]
