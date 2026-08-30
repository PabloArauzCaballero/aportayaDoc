---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
tabla: cuenta_custodia
clase: CuentaCustodia
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 14
fk_salientes: 0
fk_entrantes: 2
append_only: false
---

# `cuenta_custodia`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `CuentaCustodia` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `tipo` | VARCHAR(25) | — | no | CK |
| `entidad_financiera` | VARCHAR(60) | — | no | — |
| `numero_cuenta_cifrado` | VARCHAR(255) | — | no | — |
| `version_llave` | SMALLINT | — | no | — |
| `numero_enmascarado` | VARCHAR(30) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `saldo_segun_banco` | DECIMAL(18,2) | — | no | — |
| `saldo_segun_libro` | DECIMAL(18,2) | — | no | — |
| `fecha_saldo` | TIMESTAMPTZ | — | no | — |
| `contrato_referencia` | VARCHAR(80) | — | no | — |
| `es_principal` | BOOLEAN | — | no | — |
| `estado` | VARCHAR(15) | — | no | CK |
| `abierta_en` | DATE | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_cuenta_custodia_version_llave` | CHECK | `version_llave` |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[conciliacion_custodia]] | `cuenta_custodia_id` | 10 | [[conciliacion_custodia.cuenta_custodia_id → cuenta_custodia]] |
| [[movimiento_custodia]] | `cuenta_custodia_id` | 10 | [[movimiento_custodia.cuenta_custodia_id → cuenta_custodia]] |

## Entidades vecinas

[[conciliacion_custodia]] · [[movimiento_custodia]]

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
