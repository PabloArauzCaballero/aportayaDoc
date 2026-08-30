---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
  - append-only
tabla: saldo_diario_billetera
clase: SaldoDiarioBilletera
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
estereotipo: Objeto de valor
clave_primaria: [id]
columnas: 9
fk_salientes: 1
fk_entrantes: 0
append_only: true
---

# `saldo_diario_billetera`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `SaldoDiarioBilletera` · Objeto de valor · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `cuenta_billetera_id` | UUID | FK IDX | no | FK, IDX |
| `fecha` | DATE | — | no | — |
| `saldo_disponible` | DECIMAL(16,2) | — | no | — |
| `saldo_retenido` | DECIMAL(16,2) | — | no | — |
| `cantidad_movimientos` | INTEGER | — | no | — |
| `hash_registro` | VARCHAR(64) | — | no | — |
| `hash_anterior` | VARCHAR(64) | — | sí | NULL |
| `cerrado_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_saldo_diario_cuenta_fecha` | UNIQUE | `cuenta_billetera_id`, `fecha` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_billetera_id` | [[cuenta_billetera]] | 10 | no | [[saldo_diario_billetera.cuenta_billetera_id → cuenta_billetera]] |

## Entidades vecinas

[[cuenta_billetera]]

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
