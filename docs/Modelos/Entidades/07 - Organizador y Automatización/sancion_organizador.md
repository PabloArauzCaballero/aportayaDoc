---
tags:
  - entidad
  - modulo/07-organizador-y-automatizacion
tabla: sancion_organizador
clase: SancionOrganizador
modulo: "07 — Organizador y Automatización"
clave_primaria: [id]
columnas: 9
fk_salientes: 3
fk_entrantes: 1
append_only: false
---

# `sancion_organizador`

> Módulo [[07_organizador_automatizacion|07 — Organizador y Automatización]] · clase `SancionOrganizador`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `organizador_id` | UUID | FK IDX | no | FK, IDX |
| `evaluacion_id` | UUID | FK | sí | FK, NULL |
| `tipo` | VARCHAR(25) | — | no | CK |
| `motivo` | VARCHAR(300) | — | no | — |
| `vigente_desde` | TIMESTAMPTZ | — | no | — |
| `vigente_hasta` | TIMESTAMPTZ | — | sí | NULL |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `aplicada_por` | UUID | FK | no | FK |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_sancion_org_vigencia` | CHECK | `vigente_desde`, `vigente_hasta` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `aplicada_por` | [[usuario]] | ↗ 01 | no | [[sancion_organizador.aplicada_por → usuario]] |
| `evaluacion_id` | [[evaluacion_desempeno]] | 07 | sí | [[sancion_organizador.evaluacion_id → evaluacion_desempeno]] |
| `organizador_id` | [[organizador]] | 07 | no | [[sancion_organizador.organizador_id → organizador]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[apelacion_sancion_org]] | `sancion_organizador_id` | 07 | [[apelacion_sancion_org.sancion_organizador_id → sancion_organizador]] |

## Entidades vecinas

[[apelacion_sancion_org]] · [[evaluacion_desempeno]] · [[organizador]] · [[usuario]]

## Ver también

- Justificación de negocio: [[07_organizador_automatizacion]]
- Diagramas: `docs/entidades/07_organizador_automatizacion.puml`
- Índice: [[_Entidades]] · [[Index]]
