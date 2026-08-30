---
tags:
  - entidad
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
tabla: cargo_comision
clase: CargoComision
modulo: "11 — Tarifas, Comisiones, Impuestos y Facturación"
clave_primaria: [id]
columnas: 12
fk_salientes: 4
fk_entrantes: 0
append_only: false
---

# `cargo_comision`

> Módulo [[11_tarifas_comisiones|11 — Tarifas, Comisiones, Impuestos y Facturación]] · clase `CargoComision`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `devengo_id` | UUID | FK IDX | no | FK, IDX |
| `deduccion_entrega_id` | UUID | FK UQ | sí | FK, NULL, M4 |
| `transaccion_id` | UUID | FK | sí | FK, NULL, M10 |
| `obligacion_id` | UUID | FK | sí | FK, NULL, M3 |
| `forma_cobro` | VARCHAR(30) | — | no | CK |
| `monto_cobrado` | DECIMAL(12,2) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `intentos` | SMALLINT | — | no | — |
| `ultimo_error` | VARCHAR(300) | — | sí | NULL |
| `cobrado_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_cargo_deduccion` | UNIQUE | `deduccion_entrega_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `deduccion_entrega_id` | [[deduccion_entrega]] | ↗ 04 | sí | [[cargo_comision.deduccion_entrega_id → deduccion_entrega]] |
| `devengo_id` | [[devengo_comision]] | 11 | no | [[cargo_comision.devengo_id → devengo_comision]] |
| `obligacion_id` | [[obligacion_aporte]] | ↗ 03 | sí | [[cargo_comision.obligacion_id → obligacion_aporte]] |
| `transaccion_id` | [[transaccion_billetera]] | ↗ 10 | sí | [[cargo_comision.transaccion_id → transaccion_billetera]] |

## Entidades vecinas

[[deduccion_entrega]] · [[devengo_comision]] · [[obligacion_aporte]] · [[transaccion_billetera]]

## Notas del modelo

> Tres vias de cobro y una sola verdad contable:
> - DEDUCCION_DE_ENTREGA -> deduccion_entrega (M4)
> - DEBITO_DE_BILLETERA  -> transaccion_billetera (M10)
> - OBLIGACION_DE_APORTE -> obligacion_aporte (M3)
> UNIQUE (deduccion_entrega_id): una deduccion
> respalda un solo cargo. Si el cobro falla tres
> veces, el devengo pasa a cuenta_por_cobrar_comision.

## Ver también

- Justificación de negocio: [[11_tarifas_comisiones]]
- Diagramas: `docs/entidades/11_tarifas_comisiones.puml`
- Índice: [[_Entidades]] · [[Index]]
