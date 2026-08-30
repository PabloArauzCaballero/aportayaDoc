---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
tabla: orden_retiro
clase: OrdenRetiro
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 20
fk_salientes: 7
fk_entrantes: 1
append_only: false
---

# `orden_retiro`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `OrdenRetiro` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `cuenta_billetera_id` | UUID | FK IDX | no | FK, IDX |
| `instrumento_destino_id` | UUID | FK | no | FK |
| `retencion_id` | UUID | FK UQ | sí | FK, NULL, UQ |
| `transaccion_id` | UUID | FK | sí | FK, NULL |
| `solicitada_por` | UUID | FK IDX | no | FK, IDX |
| `aprobada_por` | UUID | FK | sí | FK, NULL |
| `proveedor_id` | UUID | FK | sí | FK, NULL, M3 |
| `monto_solicitado` | DECIMAL(16,2) | — | no | CK: > 0 |
| `costo_retiro` | DECIMAL(10,2) | — | no | — |
| `monto_neto` | DECIMAL(16,2) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `estado` | VARCHAR(20) | IDX | no | CK, IDX |
| `mfa_verificado` | BOOLEAN | — | no | — |
| `requiere_doble_aprobacion` | BOOLEAN | — | no | — |
| `ventana_enfriamiento_hasta` | TIMESTAMPTZ | — | sí | NULL |
| `referencia_proveedor` | VARCHAR(80) | UQ | sí | UQ, NULL |
| `clave_idempotencia` | VARCHAR(100) | — | no | — |
| `solicitada_en` | TIMESTAMPTZ | — | no | — |
| `pagada_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_retiro_doble_aprobacion` | CHECK | `aprobada_por`, `estado`, `requiere_doble_aprobacion`, `solicitada_por` |
| `ck_retiro_mfa` | CHECK | `estado`, `mfa_verificado` |
| `uq_retiro_idem` | UNIQUE | `cuenta_billetera_id`, `clave_idempotencia` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `aprobada_por` | [[usuario]] | ↗ 01 | sí | [[orden_retiro.aprobada_por → usuario]] |
| `cuenta_billetera_id` | [[cuenta_billetera]] | 10 | no | [[orden_retiro.cuenta_billetera_id → cuenta_billetera]] |
| `instrumento_destino_id` | [[instrumento_fondeo]] | 10 | no | [[orden_retiro.instrumento_destino_id → instrumento_fondeo]] |
| `proveedor_id` | [[proveedor_pago]] | ↗ 03 | sí | [[orden_retiro.proveedor_id → proveedor_pago]] |
| `retencion_id` | [[retencion_saldo]] | 10 | sí | [[orden_retiro.retencion_id → retencion_saldo]] |
| `solicitada_por` | [[usuario]] | ↗ 01 | no | [[orden_retiro.solicitada_por → usuario]] |
| `transaccion_id` | [[transaccion_billetera]] | 10 | sí | [[orden_retiro.transaccion_id → transaccion_billetera]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[solicitud_cierre_billetera]] | `orden_retiro_id` | 10 | [[solicitud_cierre_billetera.orden_retiro_id → orden_retiro]] |

## Entidades vecinas

[[cuenta_billetera]] · [[instrumento_fondeo]] · [[proveedor_pago]] · [[retencion_saldo]] · [[solicitud_cierre_billetera]] · [[transaccion_billetera]] · [[usuario]]

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
