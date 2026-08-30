---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
  - append-only
tabla: movimiento_billetera
clase: MovimientoBilletera
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
estereotipo: Objeto de valor
clave_primaria: [id]
columnas: 10
fk_salientes: 2
fk_entrantes: 0
append_only: true
---

# `movimiento_billetera`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `MovimientoBilletera` · Objeto de valor · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `transaccion_id` | UUID | FK IDX | no | FK, IDX |
| `cuenta_billetera_id` | UUID | FK IDX | no | FK, IDX |
| `orden` | SMALLINT | — | no | — |
| `sentido` | VARCHAR(7) | — | no | CK: DEBITO|CREDITO |
| `monto` | DECIMAL(16,2) | — | no | CK: > 0 |
| `saldo_disponible_posterior` | DECIMAL(16,2) | — | no | — |
| `saldo_retenido_posterior` | DECIMAL(16,2) | — | no | — |
| `glosa` | VARCHAR(160) | — | no | — |
| `registrado_en` | TIMESTAMPTZ | IDX | no | IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ix_movimiento_cuenta_fecha` | INDEX | expresión |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_billetera_id` | [[cuenta_billetera]] | 10 | no | [[movimiento_billetera.cuenta_billetera_id → cuenta_billetera]] |
| `transaccion_id` | [[transaccion_billetera]] | 10 | no | [[movimiento_billetera.transaccion_id → transaccion_billetera]] |

## Entidades vecinas

[[cuenta_billetera]] · [[transaccion_billetera]]

## Notas del modelo

> **Partida doble interna**
> Trigger AFTER por transaccion:
> SUM(monto WHERE sentido='DEBITO')
> = SUM(monto WHERE sentido='CREDITO')
> Un aporte de Bs 500 no es "restar 500": es
> DEBITO 500 a la billetera del participante y
> CREDITO 500 a la billetera del grupo, en la
> misma transaccion. El dinero nunca desaparece
> ni aparece; siempre cambia de cuenta.
> REVOKE UPDATE, DELETE ON movimiento_billetera.

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
