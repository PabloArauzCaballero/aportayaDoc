---
tags:
  - entidad
  - modulo/13-contabilidad-financiera-y-erp
tabla: linea_plantilla_asiento
clase: LineaPlantillaAsiento
modulo: "13 — Contabilidad Financiera y ERP"
clave_primaria: [id]
columnas: 6
fk_salientes: 2
fk_entrantes: 0
append_only: false
---

# `linea_plantilla_asiento`

> Módulo [[13_contabilidad_erp|13 — Contabilidad Financiera y ERP]] · clase `LineaPlantillaAsiento`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `plantilla_id` | UUID | FK IDX | no | FK, IDX |
| `cuenta_contable_id` | UUID | FK IDX | no | FK, IDX, M3 |
| `tipo_movimiento` | VARCHAR(5) | — | no | CK |
| `monto_referencial` | DECIMAL(14,2) | — | sí | NULL |
| `orden` | SMALLINT | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_linea_plantilla_orden` | UNIQUE | `plantilla_id`, `orden` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_contable_id` | [[cuenta_contable]] | ↗ 03 | no | [[linea_plantilla_asiento.cuenta_contable_id → cuenta_contable]] |
| `plantilla_id` | [[asiento_plantilla]] | 13 | no | [[linea_plantilla_asiento.plantilla_id → asiento_plantilla]] |

## Entidades vecinas

[[asiento_plantilla]] · [[cuenta_contable]]

## Ver también

- Justificación de negocio: [[13_contabilidad_erp]]
- Diagramas: `docs/entidades/13_contabilidad_erp.puml`
- Índice: [[_Entidades]] · [[Index]]
