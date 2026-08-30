---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: reporte_regulatorio
clase: ReporteRegulatorio
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 15
fk_salientes: 4
fk_entrantes: 2
append_only: false
---

# `reporte_regulatorio`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `ReporteRegulatorio` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `catalogo_reporte_id` | UUID | FK IDX | no | FK, IDX |
| `generado_por` | UUID | FK | sí | FK, NULL |
| `revisado_por` | UUID | FK | sí | FK, NULL |
| `aprobado_por` | UUID | FK | sí | FK, NULL |
| `periodo` | VARCHAR(10) | — | no | — |
| `fecha_corte` | DATE | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `cantidad_registros` | INTEGER | — | no | — |
| `reporte_en_cero` | BOOLEAN | — | no | — |
| `monto_total` | DECIMAL(18,2) | — | no | — |
| `url_archivo` | VARCHAR(255) | — | sí | NULL |
| `hash_archivo` | VARCHAR(64) | — | sí | NULL |
| `fecha_limite` | DATE | IDX | no | IDX |
| `generado_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_reporte_en_cero` | CHECK | `cantidad_registros`, `reporte_en_cero` |
| `ck_reporte_segregacion` | CHECK | `aprobado_por`, `estado`, `generado_por` |
| `ix_reporte_vencidos` | INDEX parcial | `fecha_limite` |
| `uq_reporte_catalogo_periodo` | UNIQUE | `catalogo_reporte_id`, `periodo` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `aprobado_por` | [[usuario]] | ↗ 01 | sí | [[reporte_regulatorio.aprobado_por → usuario]] |
| `catalogo_reporte_id` | [[catalogo_reporte_regulatorio]] | 12 | no | [[reporte_regulatorio.catalogo_reporte_id → catalogo_reporte_regulatorio]] |
| `generado_por` | [[usuario]] | ↗ 01 | sí | [[reporte_regulatorio.generado_por → usuario]] |
| `revisado_por` | [[usuario]] | ↗ 01 | sí | [[reporte_regulatorio.revisado_por → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[envio_regulatorio]] | `reporte_regulatorio_id` | 12 | [[envio_regulatorio.reporte_regulatorio_id → reporte_regulatorio]] |
| [[registro_operacion_relevante]] | `reporte_regulatorio_id` | 12 | [[registro_operacion_relevante.reporte_regulatorio_id → reporte_regulatorio]] |

## Entidades vecinas

[[catalogo_reporte_regulatorio]] · [[envio_regulatorio]] · [[registro_operacion_relevante]] · [[usuario]]

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
