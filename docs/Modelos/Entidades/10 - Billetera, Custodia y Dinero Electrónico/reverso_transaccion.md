---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
tabla: reverso_transaccion
clase: ReversoTransaccion
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
clave_primaria: [id]
columnas: 10
fk_salientes: 3
fk_entrantes: 0
append_only: false
---

# `reverso_transaccion`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `ReversoTransaccion`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `transaccion_original_id` | UUID | FK IDX | no | FK, IDX |
| `transaccion_reverso_id` | UUID | FK UQ | sí | FK, NULL, UQ |
| `autorizada_por` | UUID | FK | no | FK |
| `tipo` | VARCHAR(25) | — | no | CK |
| `motivo` | VARCHAR(300) | — | no | — |
| `monto_reversado` | DECIMAL(16,2) | — | no | — |
| `estado` | VARCHAR(15) | — | no | CK |
| `solicitada_en` | TIMESTAMPTZ | — | no | — |
| `ejecutada_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_reverso_segregacion` | CHECK | `autorizada_por` |
| `uq_reverso_original` | UNIQUE parcial | `transaccion_original_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `autorizada_por` | [[usuario]] | ↗ 01 | no | [[reverso_transaccion.autorizada_por → usuario]] |
| `transaccion_original_id` | [[transaccion_billetera]] | 10 | no | [[reverso_transaccion.transaccion_original_id → transaccion_billetera]] |
| `transaccion_reverso_id` | [[transaccion_billetera]] | 10 | sí | [[reverso_transaccion.transaccion_reverso_id → transaccion_billetera]] |

## Entidades vecinas

[[transaccion_billetera]] · [[usuario]]

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
