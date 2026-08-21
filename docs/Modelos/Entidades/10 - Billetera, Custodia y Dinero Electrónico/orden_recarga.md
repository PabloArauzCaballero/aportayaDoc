---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
tabla: orden_recarga
clase: OrdenRecarga
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 16
fk_salientes: 5
fk_entrantes: 0
append_only: false
---

# `orden_recarga`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `OrdenRecarga` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `cuenta_billetera_id` | UUID | FK IDX | no | FK, IDX |
| `instrumento_fondeo_id` | UUID | FK | sí | FK, NULL |
| `proveedor_id` | UUID | FK | sí | FK, NULL, M3 |
| `pago_id` | UUID | FK | sí | FK, NULL, M3 |
| `transaccion_id` | UUID | FK | sí | FK, NULL |
| `monto_bruto` | DECIMAL(16,2) | — | no | CK: > 0 |
| `costo_proveedor` | DECIMAL(10,2) | — | no | — |
| `monto_acreditado` | DECIMAL(16,2) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `referencia_externa` | VARCHAR(80) | UQ | sí | UQ, NULL |
| `clave_idempotencia` | VARCHAR(100) | — | no | — |
| `solicitada_en` | TIMESTAMPTZ | — | no | — |
| `acreditada_en` | TIMESTAMPTZ | — | sí | NULL |
| `expira_en` | TIMESTAMPTZ | — | sí | NULL |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_billetera_id` | [[cuenta_billetera]] | 10 | no | [[orden_recarga.cuenta_billetera_id → cuenta_billetera]] |
| `instrumento_fondeo_id` | [[instrumento_fondeo]] | 10 | sí | [[orden_recarga.instrumento_fondeo_id → instrumento_fondeo]] |
| `pago_id` | [[pago]] | ↗ 03 | sí | [[orden_recarga.pago_id → pago]] |
| `proveedor_id` | [[proveedor_pago]] | ↗ 03 | sí | [[orden_recarga.proveedor_id → proveedor_pago]] |
| `transaccion_id` | [[transaccion_billetera]] | 10 | sí | [[orden_recarga.transaccion_id → transaccion_billetera]] |

## Entidades vecinas

[[cuenta_billetera]] · [[instrumento_fondeo]] · [[pago]] · [[proveedor_pago]] · [[transaccion_billetera]]

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
