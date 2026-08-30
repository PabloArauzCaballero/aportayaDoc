---
tags:
  - entidad
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
  - append-only
tabla: devengo_comision
clase: DevengoComision
modulo: "11 — Tarifas, Comisiones, Impuestos y Facturación"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 20
fk_salientes: 7
fk_entrantes: 6
append_only: true
---

# `devengo_comision`

> Módulo [[11_tarifas_comisiones|11 — Tarifas, Comisiones, Impuestos y Facturación]] · clase `DevengoComision` · Raíz de agregado · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `concepto_tarifa_id` | UUID | FK IDX | no | FK, IDX |
| `tarifario_id` | UUID | FK IDX | no | FK, IDX |
| `cotizacion_id` | UUID | FK UQ | sí | FK, NULL, UQ |
| `grupo_id` | UUID | FK IDX | sí | FK, NULL, IDX, M2 |
| `participante_id` | UUID | FK | sí | FK, NULL, M2 |
| `usuario_obligado_id` | UUID | FK IDX | no | FK, IDX, M1 |
| `asiento_contable_id` | UUID | FK | sí | FK, NULL, M3 |
| `referencia_tipo` | VARCHAR(30) | — | no | CK |
| `referencia_id` | UUID | IDX | no | IDX, polimorfica |
| `monto_base` | DECIMAL(14,2) | — | no | — |
| `monto_comision` | DECIMAL(12,2) | — | no | CK: >= 0 |
| `monto_descuento` | DECIMAL(12,2) | — | no | — |
| `monto_impuesto` | DECIMAL(12,2) | — | no | — |
| `monto_total` | DECIMAL(12,2) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `estado` | VARCHAR(20) | IDX | no | CK, IDX |
| `fecha_devengo` | TIMESTAMPTZ | IDX | no | IDX |
| `periodo_contable` | CHAR(7) | IDX | no | IDX |
| `clave_idempotencia` | VARCHAR(100) | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_devengo_hecho` | UNIQUE | `referencia_tipo`, `referencia_id`, `concepto_tarifa_id` |
| `uq_devengo_idem` | UNIQUE | expresión |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `asiento_contable_id` | [[asiento_contable]] | ↗ 03 | sí | [[devengo_comision.asiento_contable_id → asiento_contable]] |
| `concepto_tarifa_id` | [[concepto_tarifa]] | 11 | no | [[devengo_comision.concepto_tarifa_id → concepto_tarifa]] |
| `cotizacion_id` | [[cotizacion_comision]] | 11 | sí | [[devengo_comision.cotizacion_id → cotizacion_comision]] |
| `grupo_id` | [[grupo]] | ↗ 02 | sí | [[devengo_comision.grupo_id → grupo]] |
| `participante_id` | [[participante]] | ↗ 02 | sí | [[devengo_comision.participante_id → participante]] |
| `tarifario_id` | [[tarifario]] | 11 | no | [[devengo_comision.tarifario_id → tarifario]] |
| `usuario_obligado_id` | [[usuario]] | ↗ 01 | no | [[devengo_comision.usuario_obligado_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[aplicacion_promocion]] | `devengo_id` | 11 | [[aplicacion_promocion.devengo_id → devengo_comision]] |
| [[calculo_impuesto]] | `devengo_id` | 11 | [[calculo_impuesto.devengo_id → devengo_comision]] |
| [[cargo_comision]] | `devengo_id` | 11 | [[cargo_comision.devengo_id → devengo_comision]] |
| [[cuenta_por_cobrar_comision]] | `devengo_id` | 11 | [[cuenta_por_cobrar_comision.devengo_id → devengo_comision]] |
| [[devolucion_comision]] | `devengo_id` | 11 | [[devolucion_comision.devengo_id → devengo_comision]] |
| [[factura_electronica]] | `devengo_id` | 11 | [[factura_electronica.devengo_id → devengo_comision]] |

## Entidades vecinas

[[aplicacion_promocion]] · [[asiento_contable]] · [[calculo_impuesto]] · [[cargo_comision]] · [[concepto_tarifa]] · [[cotizacion_comision]] · [[cuenta_por_cobrar_comision]] · [[devolucion_comision]] · [[factura_electronica]] · [[grupo]] · [[participante]] · [[tarifario]] · [[usuario]]

## Notas del modelo

> **Append-only**
> UNIQUE (clave_idempotencia) y UNIQUE
> (referencia_tipo, referencia_id, concepto_tarifa_id):
> la misma entrega no puede devengar dos veces la
> misma comision. El monto no se corrige con UPDATE:
> se emite devolucion_comision o se reversa.
> referencia_id es polimorfica: entrega_fondo.id (M4),
> pago.id (M3), orden_retiro.id (M10), periodo.id (M2).

## Ver también

- Justificación de negocio: [[11_tarifas_comisiones]]
- Diagramas: `docs/entidades/11_tarifas_comisiones.puml`
- Índice: [[_Entidades]] · [[Index]]
