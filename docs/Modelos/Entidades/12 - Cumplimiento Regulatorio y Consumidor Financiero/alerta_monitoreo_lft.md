---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: alerta_monitoreo_lft
clase: AlertaMonitoreoLft
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
clave_primaria: [id]
columnas: 14
fk_salientes: 6
fk_entrantes: 1
append_only: false
---

# `alerta_monitoreo_lft`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `AlertaMonitoreoLft`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `regla_monitoreo_id` | UUID | FK IDX | no | FK, IDX |
| `usuario_id` | UUID | FK IDX | no | FK, IDX, M1 |
| `cuenta_billetera_id` | UUID | FK | sí | FK, NULL, M10 |
| `transaccion_id` | UUID | FK | sí | FK, NULL, M10 |
| `caso_id` | UUID | FK | sí | FK, NULL |
| `asignada_a` | UUID | FK | sí | FK, NULL |
| `monto_involucrado` | DECIMAL(16,2) | — | no | — |
| `detalle` | JSONB | — | no | — |
| `severidad` | VARCHAR(10) | IDX | no | CK, IDX |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `conclusion` | VARCHAR(500) | — | sí | NULL |
| `detectada_en` | TIMESTAMPTZ | IDX | no | IDX |
| `cerrada_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_alerta_conclusion` | CHECK | `conclusion`, `estado` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `asignada_a` | [[usuario]] | ↗ 01 | sí | [[alerta_monitoreo_lft.asignada_a → usuario]] |
| `caso_id` | [[caso_investigacion_lft]] | 12 | sí | [[alerta_monitoreo_lft.caso_id → caso_investigacion_lft]] |
| `cuenta_billetera_id` | [[cuenta_billetera]] | ↗ 10 | sí | [[alerta_monitoreo_lft.cuenta_billetera_id → cuenta_billetera]] |
| `regla_monitoreo_id` | [[regla_monitoreo_lft]] | 12 | no | [[alerta_monitoreo_lft.regla_monitoreo_id → regla_monitoreo_lft]] |
| `transaccion_id` | [[transaccion_billetera]] | ↗ 10 | sí | [[alerta_monitoreo_lft.transaccion_id → transaccion_billetera]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[alerta_monitoreo_lft.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[desvio_perfil]] | `alerta_monitoreo_id` | 12 | [[desvio_perfil.alerta_monitoreo_id → alerta_monitoreo_lft]] |

## Entidades vecinas

[[caso_investigacion_lft]] · [[cuenta_billetera]] · [[desvio_perfil]] · [[regla_monitoreo_lft]] · [[transaccion_billetera]] · [[usuario]]

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
