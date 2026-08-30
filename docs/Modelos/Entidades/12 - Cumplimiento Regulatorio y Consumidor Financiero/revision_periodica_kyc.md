---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: revision_periodica_kyc
clase: RevisionPeriodicaKyc
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
clave_primaria: [id]
columnas: 8
fk_salientes: 3
fk_entrantes: 0
append_only: false
---

# `revision_periodica_kyc`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `RevisionPeriodicaKyc`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX, M1 |
| `calificacion_riesgo_id` | UUID | FK | sí | FK, NULL |
| `ejecutada_por` | UUID | FK | sí | FK, NULL |
| `fecha_programada` | DATE | IDX | no | IDX |
| `fecha_ejecutada` | DATE | — | sí | NULL |
| `resultado` | VARCHAR(30) | — | sí | NULL |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ix_revision_kyc_vencidas` | INDEX parcial | `fecha_programada` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `calificacion_riesgo_id` | [[calificacion_riesgo_cliente]] | 12 | sí | [[revision_periodica_kyc.calificacion_riesgo_id → calificacion_riesgo_cliente]] |
| `ejecutada_por` | [[usuario]] | ↗ 01 | sí | [[revision_periodica_kyc.ejecutada_por → usuario]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[revision_periodica_kyc.usuario_id → usuario]] |

## Entidades vecinas

[[calificacion_riesgo_cliente]] · [[usuario]]

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
