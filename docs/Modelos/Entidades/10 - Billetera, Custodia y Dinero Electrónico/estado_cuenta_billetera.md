---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
tabla: estado_cuenta_billetera
clase: EstadoCuentaBilletera
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
clave_primaria: [id]
columnas: 13
fk_salientes: 1
fk_entrantes: 0
append_only: false
---

# `estado_cuenta_billetera`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `EstadoCuentaBilletera`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `cuenta_billetera_id` | UUID | FK IDX | no | FK, IDX |
| `periodo_desde` | DATE | UQ | no | UQ+cuenta_billetera_id+periodo_hasta |
| `periodo_hasta` | DATE | — | no | — |
| `saldo_inicial` | DECIMAL(16,2) | — | no | — |
| `total_creditos` | DECIMAL(16,2) | — | no | — |
| `total_debitos` | DECIMAL(16,2) | — | no | — |
| `saldo_final` | DECIMAL(16,2) | — | no | — |
| `cantidad_movimientos` | INTEGER | — | no | — |
| `url_archivo` | VARCHAR(255) | — | no | — |
| `hash_archivo` | VARCHAR(64) | — | no | — |
| `emitido_en` | TIMESTAMPTZ | — | no | — |
| `entregado_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_extracto_cuadra` | CHECK | `saldo_final`, `saldo_inicial`, `total_creditos`, `total_debitos` |
| `ck_extracto_hash` | CHECK | `hash_archivo` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_billetera_id` | [[cuenta_billetera]] | 10 | no | [[estado_cuenta_billetera.cuenta_billetera_id → cuenta_billetera]] |

## Entidades vecinas

[[cuenta_billetera]]

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
