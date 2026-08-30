---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
tabla: retencion_saldo
clase: RetencionSaldo
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
clave_primaria: [id]
columnas: 12
fk_salientes: 3
fk_entrantes: 2
append_only: false
---

# `retencion_saldo`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `RetencionSaldo`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `cuenta_billetera_id` | UUID | FK IDX | no | FK, IDX |
| `transaccion_origen_id` | UUID | FK | sí | FK, NULL |
| `liberada_por` | UUID | FK | sí | FK, NULL |
| `motivo` | VARCHAR(30) | IDX | no | CK, IDX |
| `referencia_tipo` | VARCHAR(30) | — | sí | NULL |
| `referencia_id` | UUID | — | sí | NULL, polimorfica |
| `monto` | DECIMAL(16,2) | — | no | CK: > 0 |
| `estado` | VARCHAR(12) | IDX | no | CK, IDX |
| `expira_en` | TIMESTAMPTZ | IDX | sí | NULL, IDX |
| `creada_en` | TIMESTAMPTZ | — | no | — |
| `liberada_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_retencion_expira` | CHECK | `expira_en`, `motivo` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_billetera_id` | [[cuenta_billetera]] | 10 | no | [[retencion_saldo.cuenta_billetera_id → cuenta_billetera]] |
| `liberada_por` | [[usuario]] | ↗ 01 | sí | [[retencion_saldo.liberada_por → usuario]] |
| `transaccion_origen_id` | [[transaccion_billetera]] | 10 | sí | [[retencion_saldo.transaccion_origen_id → transaccion_billetera]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[bloqueo_saldo]] | `retencion_id` | 10 | [[bloqueo_saldo.retencion_id → retencion_saldo]] |
| [[orden_retiro]] | `retencion_id` | 10 | [[orden_retiro.retencion_id → retencion_saldo]] |

## Entidades vecinas

[[bloqueo_saldo]] · [[cuenta_billetera]] · [[orden_retiro]] · [[transaccion_billetera]] · [[usuario]]

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
