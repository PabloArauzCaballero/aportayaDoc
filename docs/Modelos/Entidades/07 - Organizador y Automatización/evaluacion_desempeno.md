---
tags:
  - entidad
  - modulo/07-organizador-y-automatizacion
tabla: evaluacion_desempeno
clase: EvaluacionDesempeno
modulo: "07 — Organizador y Automatización"
clave_primaria: [id]
columnas: 13
fk_salientes: 1
fk_entrantes: 2
append_only: false
---

# `evaluacion_desempeno`

> Módulo [[07_organizador_automatizacion|07 — Organizador y Automatización]] · clase `EvaluacionDesempeno`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `organizador_id` | UUID | FK IDX | no | FK, IDX |
| `periodo_evaluado` | VARCHAR(10) | — | no | — |
| `indice_morosidad_cartera` | DECIMAL(5,2) | — | no | — |
| `tasa_finalizacion_grupos` | DECIMAL(5,2) | — | no | — |
| `satisfaccion_participantes` | DECIMAL(3,2) | — | no | — |
| `tiempo_respuesta_promedio_horas` | DECIMAL(6,2) | — | no | — |
| `incidencias_abiertas` | SMALLINT | — | no | — |
| `coberturas_consumidas` | SMALLINT | — | no | — |
| `puntaje_global` | DECIMAL(5,2) | — | no | — |
| `nivel_sugerido` | VARCHAR(15) | — | no | CK |
| `accion_recomendada` | VARCHAR(120) | — | no | — |
| `evaluado_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_evaluacion_org_periodo` | UNIQUE | `organizador_id`, `periodo_evaluado` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `organizador_id` | [[organizador]] | 07 | no | [[evaluacion_desempeno.organizador_id → organizador]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[metrica_organizador]] | `evaluacion_id` | 07 | [[metrica_organizador.evaluacion_id → evaluacion_desempeno]] |
| [[sancion_organizador]] | `evaluacion_id` | 07 | [[sancion_organizador.evaluacion_id → evaluacion_desempeno]] |

## Entidades vecinas

[[metrica_organizador]] · [[organizador]] · [[sancion_organizador]]

## Ver también

- Justificación de negocio: [[07_organizador_automatizacion]]
- Diagramas: `docs/entidades/07_organizador_automatizacion.puml`
- Índice: [[_Entidades]] · [[Index]]
