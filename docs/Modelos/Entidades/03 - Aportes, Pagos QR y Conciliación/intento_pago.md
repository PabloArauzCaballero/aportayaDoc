---
tags:
  - entidad
  - modulo/03-aportes-pagos-qr-y-conciliacion
tabla: intento_pago
clase: IntentoPago
modulo: "03 — Aportes, Pagos QR y Conciliación"
clave_primaria: [id]
columnas: 10
fk_salientes: 1
fk_entrantes: 1
append_only: false
---

# `intento_pago`

> Módulo [[03_aportes_pagos_qr|03 — Aportes, Pagos QR y Conciliación]] · clase `IntentoPago`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `orden_cobro_id` | UUID | FK IDX | no | FK, IDX |
| `numero_intento` | SMALLINT | — | no | — |
| `canal` | VARCHAR(30) | — | no | CK |
| `iniciado_en` | TIMESTAMPTZ | — | no | — |
| `finalizado_en` | TIMESTAMPTZ | — | sí | NULL |
| `estado` | VARCHAR(15) | — | no | CK |
| `codigo_error` | VARCHAR(40) | — | sí | NULL |
| `mensaje_proveedor` | VARCHAR(255) | — | sí | NULL |
| `clave_idempotencia` | VARCHAR(80) | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_intento_pago_idem` | UNIQUE | `orden_cobro_id`, `clave_idempotencia` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `orden_cobro_id` | [[orden_cobro]] | 03 | no | [[intento_pago.orden_cobro_id → orden_cobro]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[pago]] | `intento_pago_id` | 03 | [[pago.intento_pago_id → intento_pago]] |

## Entidades vecinas

[[orden_cobro]] · [[pago]]

## Ver también

- Justificación de negocio: [[03_aportes_pagos_qr]]
- Diagramas: `docs/entidades/03_aportes_pagos_qr.puml`
- Índice: [[_Entidades]] · [[Index]]
