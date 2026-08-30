---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: debida_diligencia
clase: DebidaDiligencia
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 14
fk_salientes: 5
fk_entrantes: 0
append_only: false
---

# `debida_diligencia`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `DebidaDiligencia` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX, M1 |
| `verificacion_kyc_id` | UUID | FK | sí | FK, NULL, M1 |
| `calificacion_riesgo_id` | UUID | FK | sí | FK, NULL |
| `aprobada_por` | UUID | FK | sí | FK, NULL |
| `segunda_revision_por` | UUID | FK | sí | FK, NULL |
| `tipo` | VARCHAR(15) | IDX | no | CK, IDX |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `documentos_requeridos` | JSONB | — | no | — |
| `documentos_recibidos` | JSONB | — | no | — |
| `observaciones` | VARCHAR(500) | — | sí | NULL |
| `iniciada_en` | TIMESTAMPTZ | — | no | — |
| `completada_en` | TIMESTAMPTZ | — | sí | NULL |
| `vence_en` | TIMESTAMPTZ | IDX | sí | NULL, IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ix_ddd_por_vencer` | INDEX parcial | `vence_en` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `aprobada_por` | [[usuario]] | ↗ 01 | sí | [[debida_diligencia.aprobada_por → usuario]] |
| `calificacion_riesgo_id` | [[calificacion_riesgo_cliente]] | 12 | sí | [[debida_diligencia.calificacion_riesgo_id → calificacion_riesgo_cliente]] |
| `segunda_revision_por` | [[usuario]] | ↗ 01 | sí | [[debida_diligencia.segunda_revision_por → usuario]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[debida_diligencia.usuario_id → usuario]] |
| `verificacion_kyc_id` | [[verificacion_kyc]] | ↗ 01 | sí | [[debida_diligencia.verificacion_kyc_id → verificacion_kyc]] |

## Entidades vecinas

[[calificacion_riesgo_cliente]] · [[usuario]] · [[verificacion_kyc]]

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
