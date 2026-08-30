---
tags:
  - entidad
  - modulo/03-aportes-pagos-qr-y-conciliacion
tabla: cuenta_contable
clase: CuentaContable
modulo: "03 — Aportes, Pagos QR y Conciliación"
clave_primaria: [id]
columnas: 11
fk_salientes: 3
fk_entrantes: 12
append_only: false
---

# `cuenta_contable`

> Módulo [[03_aportes_pagos_qr|03 — Aportes, Pagos QR y Conciliación]] · clase `CuentaContable`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `codigo` | VARCHAR(20) | UQ | no | UQ |
| `nombre` | VARCHAR(80) | — | no | — |
| `tipo` | VARCHAR(15) | — | no | CK |
| `naturaleza` | VARCHAR(12) | — | no | CK |
| `cuenta_padre_id` | UUID | FK IDX | sí | FK, NULL, IDX |
| `nivel` | SMALLINT | — | no | — |
| `es_cuenta_de_movimiento` | BOOLEAN | — | no | — |
| `grupo_id` | UUID | FK | sí | FK, NULL |
| `participante_id` | UUID | FK | sí | FK, NULL |
| `saldo` | DECIMAL(16,2) | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_cuenta_contable_nivel` | CHECK | `nivel` |
| `ck_cuenta_contable_padre_distinto` | CHECK | `cuenta_padre_id`, `id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_padre_id` | [[cuenta_contable]] | 03 | sí | [[cuenta_contable.cuenta_padre_id → cuenta_contable]] |
| `grupo_id` | [[grupo]] | ↗ 02 | sí | [[cuenta_contable.grupo_id → grupo]] |
| `participante_id` | [[participante]] | ↗ 02 | sí | [[cuenta_contable.participante_id → participante]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[categoria_activo_fijo]] | `cuenta_activo_id` | ↗ 13 | [[categoria_activo_fijo.cuenta_activo_id → cuenta_contable]] |
| [[categoria_activo_fijo]] | `cuenta_depreciacion_id` | ↗ 13 | [[categoria_activo_fijo.cuenta_depreciacion_id → cuenta_contable]] |
| [[categoria_activo_fijo]] | `cuenta_gasto_depreciacion_id` | ↗ 13 | [[categoria_activo_fijo.cuenta_gasto_depreciacion_id → cuenta_contable]] |
| [[concepto_tarifa]] | `cuenta_ingreso_id` | ↗ 11 | [[concepto_tarifa.cuenta_ingreso_id → cuenta_contable]] |
| [[cuenta_billetera]] | `cuenta_contable_id` | ↗ 10 | [[cuenta_billetera.cuenta_contable_id → cuenta_contable]] |
| [[cuenta_contable]] | `cuenta_padre_id` | 03 | [[cuenta_contable.cuenta_padre_id → cuenta_contable]] |
| [[fondo_garantia]] | `cuenta_contable_id` | ↗ 08 | [[fondo_garantia.cuenta_contable_id → cuenta_contable]] |
| [[impuesto]] | `cuenta_contable_id` | ↗ 11 | [[impuesto.cuenta_contable_id → cuenta_contable]] |
| [[linea_plantilla_asiento]] | `cuenta_contable_id` | ↗ 13 | [[linea_plantilla_asiento.cuenta_contable_id → cuenta_contable]] |
| [[movimiento_contable]] | `cuenta_id` | 03 | [[movimiento_contable.cuenta_id → cuenta_contable]] |
| [[partida_presupuestaria]] | `cuenta_contable_id` | ↗ 13 | [[partida_presupuestaria.cuenta_contable_id → cuenta_contable]] |
| [[tercero_comercial]] | `cuenta_contable_id` | ↗ 13 | [[tercero_comercial.cuenta_contable_id → cuenta_contable]] |

## Entidades vecinas

[[categoria_activo_fijo]] · [[concepto_tarifa]] · [[cuenta_billetera]] · [[cuenta_contable]] · [[fondo_garantia]] · [[grupo]] · [[impuesto]] · [[linea_plantilla_asiento]] · [[movimiento_contable]] · [[participante]] · [[partida_presupuestaria]] · [[tercero_comercial]]

## Notas del modelo

> **Plan de cuentas jerarquico (M13)**
> cuenta_padre_id arma el arbol contable; nivel es
> la profundidad (1 = cuenta mayor). Solo las cuentas
> con es_cuenta_de_movimiento = true reciben lineas
> en movimiento_contable: una cuenta sumarizadora
> (es_cuenta_de_movimiento = false) es un total, no
> un destino de asiento. Ese CHECK vive en M13.

## Ver también

- Justificación de negocio: [[03_aportes_pagos_qr]]
- Diagramas: `docs/entidades/03_aportes_pagos_qr.puml`
- Índice: [[_Entidades]] · [[Index]]
