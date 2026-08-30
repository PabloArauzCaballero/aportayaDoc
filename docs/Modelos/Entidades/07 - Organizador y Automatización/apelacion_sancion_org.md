---
tags:
  - entidad
  - modulo/07-organizador-y-automatizacion
tabla: apelacion_sancion_org
modulo: "07 — Organizador y Automatización"
clave_primaria: [id]
columnas: 9
fk_salientes: 2
fk_entrantes: 0
append_only: false
---

# `apelacion_sancion_org`

> Módulo [[07_organizador_automatizacion|07 — Organizador y Automatización]]

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `sancion_organizador_id` | UUID | FK UQ | no | FK |
| `argumento` | TEXT | — | no | — |
| `evidencias` | JSONB | — | no | — |
| `estado` | VARCHAR(15) | — | no | CK |
| `resuelta_por` | UUID | FK | sí | FK, NULL |
| `resolucion` | VARCHAR(400) | — | sí | NULL |
| `presentada_en` | TIMESTAMPTZ | — | no | — |
| `resuelta_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_apelacion_org_resuelta` | CHECK | `estado`, `resolucion`, `resuelta_en`, `resuelta_por` |
| `uq_apelacion_por_sancion` | UNIQUE | `sancion_organizador_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `resuelta_por` | [[usuario]] | ↗ 01 | sí | [[apelacion_sancion_org.resuelta_por → usuario]] |
| `sancion_organizador_id` | [[sancion_organizador]] | 07 | no | [[apelacion_sancion_org.sancion_organizador_id → sancion_organizador]] |

## Entidades vecinas

[[sancion_organizador]] · [[usuario]]

## Ver también

- Justificación de negocio: [[07_organizador_automatizacion]]
- Diagramas: `docs/entidades/07_organizador_automatizacion.puml`
- Índice: [[_Entidades]] · [[Index]]
