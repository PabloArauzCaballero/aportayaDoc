---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: caso_investigacion_lft
clase: CasoInvestigacionLft
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 15
fk_salientes: 4
fk_entrantes: 1
append_only: false
---

# `caso_investigacion_lft`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `CasoInvestigacionLft` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `codigo` | VARCHAR(20) | UQ | no | UQ |
| `usuario_id` | UUID | FK IDX | no | FK, IDX, M1 |
| `analista_id` | UUID | FK | no | FK |
| `revisado_por` | UUID | FK | sí | FK, NULL |
| `reporte_operacion_sospechosa_id` | UUID | FK | sí | FK, NULL, M9 |
| `origen` | VARCHAR(25) | — | no | CK |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `prioridad` | VARCHAR(10) | — | no | CK |
| `resumen` | VARCHAR(500) | — | no | — |
| `hallazgos` | TEXT | — | sí | NULL |
| `decision` | VARCHAR(25) | — | sí | CK, NULL |
| `abierto_en` | TIMESTAMPTZ | — | no | — |
| `plazo_limite` | TIMESTAMPTZ | IDX | no | IDX |
| `cerrado_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_caso_plazo` | CHECK | `abierto_en`, `plazo_limite` |
| `ck_caso_reporte` | CHECK | `decision`, `reporte_operacion_sospechosa_id` |
| `ck_caso_revision` | CHECK | `analista_id`, `revisado_por` |
| `ix_caso_vencidos` | INDEX parcial | `plazo_limite` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `analista_id` | [[usuario]] | ↗ 01 | no | [[caso_investigacion_lft.analista_id → usuario]] |
| `reporte_operacion_sospechosa_id` | [[reporte_operacion_sospechosa]] | ↗ 09 | sí | [[caso_investigacion_lft.reporte_operacion_sospechosa_id → reporte_operacion_sospechosa]] |
| `revisado_por` | [[usuario]] | ↗ 01 | sí | [[caso_investigacion_lft.revisado_por → usuario]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[caso_investigacion_lft.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[alerta_monitoreo_lft]] | `caso_id` | 12 | [[alerta_monitoreo_lft.caso_id → caso_investigacion_lft]] |

## Entidades vecinas

[[alerta_monitoreo_lft]] · [[reporte_operacion_sospechosa]] · [[usuario]]

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
