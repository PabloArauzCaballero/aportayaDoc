---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: expediente_cliente
clase: ExpedienteCliente
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
clave_primaria: [id]
columnas: 9
fk_salientes: 2
fk_entrantes: 0
append_only: false
---

# `expediente_cliente`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `ExpedienteCliente`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK UQ | no | FK, UQ, M1 |
| `responsable_id` | UUID | FK | sí | FK, NULL |
| `completitud_porcentaje` | DECIMAL(5,2) | — | no | — |
| `documentos` | JSONB | — | no | — |
| `ubicacion_fisica` | VARCHAR(120) | — | sí | NULL |
| `retencion_hasta` | DATE | IDX | no | IDX |
| `estado` | VARCHAR(15) | — | no | CK |
| `ultima_actualizacion` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_expediente_retencion_futura` | CHECK | `retencion_hasta`, `ultima_actualizacion` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `responsable_id` | [[usuario]] | ↗ 01 | sí | [[expediente_cliente.responsable_id → usuario]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[expediente_cliente.usuario_id → usuario]] |

## Entidades vecinas

[[usuario]]

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
