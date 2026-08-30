---
tags:
  - entidad
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
tabla: cotizacion_comision
clase: CotizacionComision
modulo: "11 — Tarifas, Comisiones, Impuestos y Facturación"
estereotipo: Objeto de valor
clave_primaria: [id]
columnas: 15
fk_salientes: 2
fk_entrantes: 1
append_only: false
---

# `cotizacion_comision`

> Módulo [[11_tarifas_comisiones|11 — Tarifas, Comisiones, Impuestos y Facturación]] · clase `CotizacionComision` · Objeto de valor

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `concepto_tarifa_id` | UUID | FK IDX | no | FK, IDX |
| `tarifario_id` | UUID | FK | no | FK |
| `referencia_tipo` | VARCHAR(30) | — | no | CK |
| `referencia_id` | UUID | IDX | no | IDX, polimorfica |
| `monto_base` | DECIMAL(14,2) | — | no | — |
| `monto_comision` | DECIMAL(12,2) | — | no | — |
| `monto_impuesto` | DECIMAL(12,2) | — | no | — |
| `monto_total` | DECIMAL(12,2) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `desglose` | JSONB | — | no | — |
| `valida_hasta` | TIMESTAMPTZ | — | no | — |
| `mostrada_al_usuario_en` | TIMESTAMPTZ | — | sí | NULL |
| `aceptada_en` | TIMESTAMPTZ | — | sí | NULL |
| `clave_idempotencia` | VARCHAR(100) | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_cotizacion_idem` | UNIQUE | `referencia_id`, `clave_idempotencia` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `concepto_tarifa_id` | [[concepto_tarifa]] | 11 | no | [[cotizacion_comision.concepto_tarifa_id → concepto_tarifa]] |
| `tarifario_id` | [[tarifario]] | 11 | no | [[cotizacion_comision.tarifario_id → tarifario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[devengo_comision]] | `cotizacion_id` | 11 | [[devengo_comision.cotizacion_id → cotizacion_comision]] |

## Entidades vecinas

[[concepto_tarifa]] · [[devengo_comision]] · [[tarifario]]

## Ver también

- Justificación de negocio: [[11_tarifas_comisiones]]
- Diagramas: `docs/entidades/11_tarifas_comisiones.puml`
- Índice: [[_Entidades]] · [[Index]]
