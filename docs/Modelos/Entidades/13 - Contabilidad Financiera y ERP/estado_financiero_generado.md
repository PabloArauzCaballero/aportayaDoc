---
tags:
  - entidad
  - modulo/13-contabilidad-financiera-y-erp
  - append-only
tabla: estado_financiero_generado
clase: EstadoFinancieroGenerado
modulo: "13 — Contabilidad Financiera y ERP"
clave_primaria: [id]
columnas: 7
fk_salientes: 2
fk_entrantes: 0
append_only: true
---

# `estado_financiero_generado`

> Módulo [[13_contabilidad_erp|13 — Contabilidad Financiera y ERP]] · clase `EstadoFinancieroGenerado` · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `periodo_contable_id` | UUID | FK IDX | no | FK, IDX |
| `tipo` | VARCHAR(20) | — | no | CK |
| `generado_en` | TIMESTAMPTZ | — | no | — |
| `generado_por` | UUID | FK | no | FK |
| `datos` | JSONB | — | no | — |
| `hash_contenido` | VARCHAR(64) | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_estado_financiero_periodo_tipo` | UNIQUE | `periodo_contable_id`, `tipo` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `generado_por` | [[usuario]] | ↗ 01 | no | [[estado_financiero_generado.generado_por → usuario]] |
| `periodo_contable_id` | [[periodo_contable]] | 13 | no | [[estado_financiero_generado.periodo_contable_id → periodo_contable]] |

## Entidades vecinas

[[periodo_contable]] · [[usuario]]

## Ver también

- Justificación de negocio: [[13_contabilidad_erp]]
- Diagramas: `docs/entidades/13_contabilidad_erp.puml`
- Índice: [[_Entidades]] · [[Index]]
