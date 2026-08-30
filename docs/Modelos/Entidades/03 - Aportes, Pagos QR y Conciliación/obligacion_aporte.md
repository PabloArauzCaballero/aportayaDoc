---
tags:
  - entidad
  - modulo/03-aportes-pagos-qr-y-conciliacion
tabla: obligacion_aporte
clase: ObligacionAporte
modulo: "03 — Aportes, Pagos QR y Conciliación"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 22
fk_salientes: 7
fk_entrantes: 7
append_only: false
---

# `obligacion_aporte`

> Módulo [[03_aportes_pagos_qr|03 — Aportes, Pagos QR y Conciliación]] · clase `ObligacionAporte` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `grupo_id` | UUID | FK IDX | no | FK, IDX |
| `periodo_id` | UUID | FK IDX | no | FK, IDX |
| `cupo_id` | UUID | FK IDX | no | FK, IDX |
| `participante_id` | UUID | FK IDX | no | FK, IDX |
| `politica_mora_id` | UUID | FK | sí | FK, NULL |
| `obligacion_origen_id` | UUID | FK | sí | FK, NULL |
| `plan_regularizacion_id` | UUID | FK | sí | FK, NULL |
| `tipo` | VARCHAR(30) | — | no | CK |
| `monto_esperado` | DECIMAL(14,2) | — | no | CK: > 0 |
| `moneda` | CHAR(3) | — | no | — |
| `monto_pagado` | DECIMAL(14,2) | — | no | — |
| `monto_recargo` | DECIMAL(14,2) | — | no | — |
| `monto_condonado` | DECIMAL(14,2) | — | no | — |
| `monto_cubierto_garantia` | DECIMAL(14,2) | — | no | — |
| `saldo_pendiente` | DECIMAL(14,2) | — | no | GENERATED |
| `estado` | VARCHAR(30) | IDX | no | CK, IDX |
| `fecha_vencimiento` | DATE | IDX | no | IDX |
| `fecha_fin_gracia` | DATE | — | no | — |
| `fecha_pago_efectivo` | TIMESTAMPTZ | — | sí | NULL |
| `dias_mora` | SMALLINT | — | no | — |
| `version` | INTEGER | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_obligacion_periodo_cupo` | UNIQUE parcial | `periodo_id`, `cupo_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cupo_id` | [[cupo]] | ↗ 02 | no | [[obligacion_aporte.cupo_id → cupo]] |
| `grupo_id` | [[grupo]] | ↗ 02 | no | [[obligacion_aporte.grupo_id → grupo]] |
| `obligacion_origen_id` | [[obligacion_aporte]] | 03 | sí | [[obligacion_aporte.obligacion_origen_id → obligacion_aporte]] |
| `participante_id` | [[participante]] | ↗ 02 | no | [[obligacion_aporte.participante_id → participante]] |
| `periodo_id` | [[periodo]] | ↗ 02 | no | [[obligacion_aporte.periodo_id → periodo]] |
| `plan_regularizacion_id` | [[plan_regularizacion]] | 03 | sí | [[obligacion_aporte.plan_regularizacion_id → plan_regularizacion]] |
| `politica_mora_id` | [[politica_mora]] | 03 | sí | [[obligacion_aporte.politica_mora_id → politica_mora]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[cargo_comision]] | `obligacion_id` | ↗ 11 | [[cargo_comision.obligacion_id → obligacion_aporte]] |
| [[cobertura_incumplimiento]] | `obligacion_id` | ↗ 08 | [[cobertura_incumplimiento.obligacion_id → obligacion_aporte]] |
| [[obligacion_aporte]] | `obligacion_origen_id` | 03 | [[obligacion_aporte.obligacion_origen_id → obligacion_aporte]] |
| [[orden_cobro]] | `obligacion_id` | 03 | [[orden_cobro.obligacion_id → obligacion_aporte]] |
| [[pago]] | `obligacion_id` | 03 | [[pago.obligacion_id → obligacion_aporte]] |
| [[registro_incumplimiento]] | `obligacion_id` | ↗ 08 | [[registro_incumplimiento.obligacion_id → obligacion_aporte]] |
| [[transferencia_p2p]] | `obligacion_id` | ↗ 10 | [[transferencia_p2p.obligacion_id → obligacion_aporte]] |

## Entidades vecinas

[[cargo_comision]] · [[cobertura_incumplimiento]] · [[cupo]] · [[grupo]] · [[obligacion_aporte]] · [[orden_cobro]] · [[pago]] · [[participante]] · [[periodo]] · [[plan_regularizacion]] · [[politica_mora]] · [[registro_incumplimiento]] · [[transferencia_p2p]]

## Notas del modelo

> grupo_id, periodo_id, cupo_id y participante_id
> referencian al modulo 2.
> UNIQUE (periodo_id, cupo_id, tipo)
> para tipo = 'APORTE_PERIODICO': un cupo debe
> exactamente un aporte por periodo.
> saldo_pendiente = monto_esperado + monto_recargo
> - monto_pagado - monto_condonado
> - monto_cubierto_garantia  (columna generada).

## Ver también

- Justificación de negocio: [[03_aportes_pagos_qr]]
- Diagramas: `docs/entidades/03_aportes_pagos_qr.puml`
- Índice: [[_Entidades]] · [[Index]]
