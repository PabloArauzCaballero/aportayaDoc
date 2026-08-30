---
tags:
  - entidad
  - modulo/07-organizador-y-automatizacion
tabla: tarea_automatizada
clase: TareaAutomatizada
modulo: "07 — Organizador y Automatización"
clave_primaria: [id]
columnas: 8
fk_salientes: 2
fk_entrantes: 1
append_only: false
---

# `tarea_automatizada`

> Módulo [[07_organizador_automatizacion|07 — Organizador y Automatización]] · clase `TareaAutomatizada`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `regla_id` | UUID | FK IDX | no | FK, IDX |
| `grupo_id` | UUID | FK IDX | no | FK, IDX |
| `tipo` | VARCHAR(40) | — | no | — |
| `programada_para` | TIMESTAMPTZ | IDX | no | IDX |
| `estado` | VARCHAR(25) | IDX | no | CK, IDX |
| `intentos` | SMALLINT | — | no | — |
| `clave_idempotencia` | VARCHAR(80) | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_tarea_intentos` | CHECK | `intentos` |
| `uq_tarea_automatizada_clave` | UNIQUE | `regla_id`, `grupo_id`, `clave_idempotencia` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `grupo_id` | [[grupo]] | ↗ 02 | no | [[tarea_automatizada.grupo_id → grupo]] |
| `regla_id` | [[regla_automatizacion]] | 07 | no | [[tarea_automatizada.regla_id → regla_automatizacion]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[ejecucion_tarea]] | `tarea_id` | 07 | [[ejecucion_tarea.tarea_id → tarea_automatizada]] |

## Entidades vecinas

[[ejecucion_tarea]] · [[grupo]] · [[regla_automatizacion]]

## Notas del modelo

> El organizador digital corre sobre estas dos
> tablas: clave_idempotencia impide que un
> reintento del planificador genere cobros o
> recordatorios duplicados.

## Ver también

- Justificación de negocio: [[07_organizador_automatizacion]]
- Diagramas: `docs/entidades/07_organizador_automatizacion.puml`
- Índice: [[_Entidades]] · [[Index]]
