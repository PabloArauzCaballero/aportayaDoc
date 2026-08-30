---
tags:
  - entidad
  - modulo/03-aportes-pagos-qr-y-conciliacion
tabla: orden_cobro
clase: OrdenCobro
modulo: "03 — Aportes, Pagos QR y Conciliación"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 11
fk_salientes: 2
fk_entrantes: 4
append_only: false
---

# `orden_cobro`

> Módulo [[03_aportes_pagos_qr|03 — Aportes, Pagos QR y Conciliación]] · clase `OrdenCobro` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `obligacion_id` | UUID | FK IDX | no | FK, IDX |
| `proveedor_id` | UUID | FK | no | FK |
| `monto_exacto` | DECIMAL(14,2) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `permite_monto_abierto` | BOOLEAN | — | no | — |
| `referencia_unica` | VARCHAR(60) | UQ | no | UQ |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `emitida_en` | TIMESTAMPTZ | — | no | — |
| `expira_en` | TIMESTAMPTZ | IDX | no | IDX |
| `clave_idempotencia` | VARCHAR(80) | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_orden_cobro_idem` | UNIQUE | `obligacion_id`, `clave_idempotencia` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `obligacion_id` | [[obligacion_aporte]] | 03 | no | [[orden_cobro.obligacion_id → obligacion_aporte]] |
| `proveedor_id` | [[proveedor_pago]] | 03 | no | [[orden_cobro.proveedor_id → proveedor_pago]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[enlace_pago_notificado]] | `orden_cobro_id` | ↗ 05 | [[enlace_pago_notificado.orden_cobro_id → orden_cobro]] |
| [[enlace_pago_rapido]] | `orden_cobro_id` | 03 | [[enlace_pago_rapido.orden_cobro_id → orden_cobro]] |
| [[intento_pago]] | `orden_cobro_id` | 03 | [[intento_pago.orden_cobro_id → orden_cobro]] |
| [[qr_cobro]] | `orden_cobro_id` | 03 | [[qr_cobro.orden_cobro_id → orden_cobro]] |

## Entidades vecinas

[[enlace_pago_notificado]] · [[enlace_pago_rapido]] · [[intento_pago]] · [[obligacion_aporte]] · [[proveedor_pago]] · [[qr_cobro]]

## Ver también

- Justificación de negocio: [[03_aportes_pagos_qr]]
- Diagramas: `docs/entidades/03_aportes_pagos_qr.puml`
- Índice: [[_Entidades]] · [[Index]]
