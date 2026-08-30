---
tags:
  - entidad
  - modulo/07-organizador-y-automatizacion
tabla: ejecucion_tarea
clase: EjecucionTarea
modulo: "07 — Organizador y Automatización"
clave_primaria: [id]
columnas: 8
fk_salientes: 1
fk_entrantes: 0
append_only: false
---

# `ejecucion_tarea`

> Módulo [[07_organizador_automatizacion|07 — Organizador y Automatización]] · clase `EjecucionTarea`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `tarea_id` | UUID | FK IDX | no | FK, IDX |
| `iniciada_en` | TIMESTAMPTZ | — | no | — |
| `finalizada_en` | TIMESTAMPTZ | — | sí | NULL |
| `resultado` | VARCHAR(10) | — | no | CK |
| `registros_afectados` | INTEGER | — | no | — |
| `detalle` | JSONB | — | no | — |
| `mensaje_error` | TEXT | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_ejecucion_tarea_error` | CHECK | `mensaje_error`, `resultado` |
| `ck_ejecucion_tarea_fin` | CHECK | `finalizada_en`, `iniciada_en` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `tarea_id` | [[tarea_automatizada]] | 07 | no | [[ejecucion_tarea.tarea_id → tarea_automatizada]] |

## Entidades vecinas

[[tarea_automatizada]]

## Ver también

- Justificación de negocio: [[07_organizador_automatizacion]]
- Diagramas: `docs/entidades/07_organizador_automatizacion.puml`
- Índice: [[_Entidades]] · [[Index]]
