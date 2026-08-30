---
tags:
  - entidad
  - modulo/07-organizador-y-automatizacion
tabla: metrica_organizador
clase: MetricaOrganizador
modulo: "07 — Organizador y Automatización"
clave_primaria: [id]
columnas: 7
fk_salientes: 1
fk_entrantes: 0
append_only: false
---

# `metrica_organizador`

> Módulo [[07_organizador_automatizacion|07 — Organizador y Automatización]] · clase `MetricaOrganizador`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `evaluacion_id` | UUID | FK IDX | no | FK, IDX |
| `codigo` | VARCHAR(40) | — | no | — |
| `valor` | DECIMAL(12,4) | — | no | — |
| `meta` | DECIMAL(12,4) | — | no | — |
| `cumple` | BOOLEAN | — | no | — |
| `peso` | DECIMAL(4,3) | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_metrica_org_peso` | CHECK | `peso` |
| `uq_metrica_org_codigo` | UNIQUE | `evaluacion_id`, `codigo` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `evaluacion_id` | [[evaluacion_desempeno]] | 07 | no | [[metrica_organizador.evaluacion_id → evaluacion_desempeno]] |

## Entidades vecinas

[[evaluacion_desempeno]]

## Ver también

- Justificación de negocio: [[07_organizador_automatizacion]]
- Diagramas: `docs/entidades/07_organizador_automatizacion.puml`
- Índice: [[_Entidades]] · [[Index]]
