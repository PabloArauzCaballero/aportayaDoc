---
tags:
  - entidad
  - modulo/07-organizador-y-automatizacion
tabla: regla_automatizacion
clase: ReglaAutomatizacion
modulo: "07 — Organizador y Automatización"
estereotipo: Política configurable
clave_primaria: [id]
columnas: 10
fk_salientes: 0
fk_entrantes: 1
append_only: false
---

# `regla_automatizacion`

> Módulo [[07_organizador_automatizacion|07 — Organizador y Automatización]] · clase `ReglaAutomatizacion` · Política configurable

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `codigo` | VARCHAR(40) | UQ | no | UQ |
| `descripcion` | VARCHAR(200) | — | no | — |
| `disparador` | VARCHAR(10) | — | no | CK |
| `expresion_disparo` | VARCHAR(80) | — | no | — |
| `condicion` | VARCHAR(300) | — | no | — |
| `accion` | VARCHAR(30) | — | no | CK |
| `requiere_confirmacion_humana` | BOOLEAN | — | no | — |
| `prioridad` | SMALLINT | — | no | — |
| `activa` | BOOLEAN | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_regla_confirmacion_humana` | CHECK | `accion`, `requiere_confirmacion_humana` |
| `ck_regla_prioridad` | CHECK | `prioridad` |
| `uq_regla_automatizacion_prioridad` | UNIQUE parcial | `disparador`, `prioridad` |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[tarea_automatizada]] | `regla_id` | 07 | [[tarea_automatizada.regla_id → regla_automatizacion]] |

## Entidades vecinas

[[tarea_automatizada]]

## Ver también

- Justificación de negocio: [[07_organizador_automatizacion]]
- Diagramas: `docs/entidades/07_organizador_automatizacion.puml`
- Índice: [[_Entidades]] · [[Index]]
