---
tags:
  - entidad
  - modulo/09-auditoria-reportes-y-cumplimiento
tabla: exportacion_reporte
clase: ExportacionReporte
modulo: "09 — Auditoría, Reportes y Cumplimiento"
clave_primaria: [id]
columnas: 11
fk_salientes: 1
fk_entrantes: 0
append_only: false
---

# `exportacion_reporte`

> Módulo [[09_auditoria_reportes|09 — Auditoría, Reportes y Cumplimiento]] · clase `ExportacionReporte`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `ejecucion_id` | UUID | FK IDX | no | FK, IDX |
| `formato` | VARCHAR(10) | — | no | CK |
| `url_archivo` | VARCHAR(255) | — | no | — |
| `hash_archivo` | VARCHAR(64) | — | no | — |
| `tamano_bytes` | BIGINT | — | no | — |
| `esta_cifrado` | BOOLEAN | — | no | — |
| `version_llave` | SMALLINT | — | no | — |
| `descargas` | SMALLINT | — | no | — |
| `expira_en` | TIMESTAMPTZ | IDX | no | IDX |
| `generada_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_exportacion_version_llave` | CHECK | `version_llave` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `ejecucion_id` | [[ejecucion_reporte]] | 09 | no | [[exportacion_reporte.ejecucion_id → ejecucion_reporte]] |

## Entidades vecinas

[[ejecucion_reporte]]

## Ver también

- Justificación de negocio: [[09_auditoria_reportes]]
- Diagramas: `docs/entidades/09_auditoria_reportes.puml`
- Índice: [[_Entidades]] · [[Index]]
