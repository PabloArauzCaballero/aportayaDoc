---
tags:
  - entidad
  - modulo/03-aportes-pagos-qr-y-conciliacion
tabla: pago
clase: Pago
modulo: "03 — Aportes, Pagos QR y Conciliación"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 19
fk_salientes: 4
fk_entrantes: 9
append_only: false
---

# `pago`

> Módulo [[03_aportes_pagos_qr|03 — Aportes, Pagos QR y Conciliación]] · clase `Pago` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `obligacion_id` | UUID | FK IDX | no | FK, IDX |
| `intento_pago_id` | UUID | FK UQ | sí | FK, NULL, UQ |
| `proveedor_id` | UUID | FK IDX | sí | FK, NULL, IDX |
| `monto` | DECIMAL(14,2) | — | no | CK: > 0 |
| `moneda` | CHAR(3) | — | no | — |
| `monto_comision_proveedor` | DECIMAL(10,2) | — | no | — |
| `monto_neto_acreditado` | DECIMAL(14,2) | — | no | — |
| `canal` | VARCHAR(30) | — | no | CK |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `fecha_hora_pago` | TIMESTAMPTZ | IDX | no | IDX |
| `fecha_hora_acreditacion` | TIMESTAMPTZ | — | sí | NULL |
| `referencia_proveedor` | VARCHAR(80) | UQ | no | UQ+proveedor_id |
| `pagador_nombre` | VARCHAR(120) | — | sí | NULL |
| `pagador_documento` | VARCHAR(30) | — | sí | NULL |
| `cuenta_origen_enmascarada` | VARCHAR(40) | — | sí | NULL |
| `registrado_por` | UUID | FK | sí | FK, NULL |
| `es_manual` | BOOLEAN | — | no | — |
| `clave_idempotencia` | VARCHAR(80) | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_pago_idem` | UNIQUE | `obligacion_id`, `clave_idempotencia` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `intento_pago_id` | [[intento_pago]] | 03 | sí | [[pago.intento_pago_id → intento_pago]] |
| `obligacion_id` | [[obligacion_aporte]] | 03 | no | [[pago.obligacion_id → obligacion_aporte]] |
| `proveedor_id` | [[proveedor_pago]] | 03 | sí | [[pago.proveedor_id → proveedor_pago]] |
| `registrado_por` | [[usuario]] | ↗ 01 | sí | [[pago.registrado_por → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[abono_recuperacion]] | `pago_id` | ↗ 08 | [[abono_recuperacion.pago_id → pago]] |
| [[comprobante_manual]] | `pago_id` | 03 | [[comprobante_manual.pago_id → pago]] |
| [[conciliacion]] | `pago_id` | 03 | [[conciliacion.pago_id → pago]] |
| [[constancia_pago]] | `pago_id` | 03 | [[constancia_pago.pago_id → pago]] |
| [[disputa_pago]] | `pago_id` | 03 | [[disputa_pago.pago_id → pago]] |
| [[ejecucion_aval]] | `pago_id` | ↗ 08 | [[ejecucion_aval.pago_id → pago]] |
| [[orden_recarga]] | `pago_id` | ↗ 10 | [[orden_recarga.pago_id → pago]] |
| [[reembolso]] | `pago_id` | 03 | [[reembolso.pago_id → pago]] |
| [[webhook_pasarela]] | `pago_id` | 03 | [[webhook_pasarela.pago_id → pago]] |

## Entidades vecinas

[[abono_recuperacion]] · [[comprobante_manual]] · [[conciliacion]] · [[constancia_pago]] · [[disputa_pago]] · [[ejecucion_aval]] · [[intento_pago]] · [[obligacion_aporte]] · [[orden_recarga]] · [[proveedor_pago]] · [[reembolso]] · [[usuario]] · [[webhook_pasarela]]

## Notas del modelo

> **Reglas duras**
> - UNIQUE (proveedor, referencia_proveedor):
> el mismo cobro no entra dos veces.
> - UNIQUE (clave_idempotencia).
> - Trigger: SUM(pago.monto ACREDITADO) por
> obligacion no puede superar
> monto_esperado + monto_recargo salvo que
> exista un reembolso compensatorio.

## Ver también

- Justificación de negocio: [[03_aportes_pagos_qr]]
- Diagramas: `docs/entidades/03_aportes_pagos_qr.puml`
- Índice: [[_Entidades]] · [[Index]]
