---
tags:
  - entidad
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
tabla: nota_credito_debito
clase: NotaCreditoDebito
modulo: "11 — Tarifas, Comisiones, Impuestos y Facturación"
clave_primaria: [id]
columnas: 9
fk_salientes: 2
fk_entrantes: 0
append_only: false
---

# `nota_credito_debito`

> Módulo [[11_tarifas_comisiones|11 — Tarifas, Comisiones, Impuestos y Facturación]] · clase `NotaCreditoDebito`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `factura_id` | UUID | FK IDX | no | FK, IDX |
| `devolucion_comision_id` | UUID | FK | sí | FK, NULL |
| `tipo` | VARCHAR(8) | — | no | CK |
| `motivo` | VARCHAR(200) | — | no | — |
| `monto` | DECIMAL(12,2) | — | no | — |
| `cuf` | VARCHAR(80) | UQ | no | — |
| `fecha_emision` | TIMESTAMPTZ | — | no | — |
| `estado_fiscal` | VARCHAR(20) | — | no | CK |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_nota_cuf` | UNIQUE | `cuf` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `devolucion_comision_id` | [[devolucion_comision]] | 11 | sí | [[nota_credito_debito.devolucion_comision_id → devolucion_comision]] |
| `factura_id` | [[factura_electronica]] | 11 | no | [[nota_credito_debito.factura_id → factura_electronica]] |

## Entidades vecinas

[[devolucion_comision]] · [[factura_electronica]]

## Ver también

- Justificación de negocio: [[11_tarifas_comisiones]]
- Diagramas: `docs/entidades/11_tarifas_comisiones.puml`
- Índice: [[_Entidades]] · [[Index]]
