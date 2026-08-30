---
tags:
  - entidad
  - modulo/13-contabilidad-financiera-y-erp
tabla: activo_fijo
clase: ActivoFijo
modulo: "13 — Contabilidad Financiera y ERP"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 13
fk_salientes: 3
fk_entrantes: 1
append_only: false
---

# `activo_fijo`

> Módulo [[13_contabilidad_erp|13 — Contabilidad Financiera y ERP]] · clase `ActivoFijo` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `categoria_activo_fijo_id` | UUID | FK IDX | no | FK, IDX |
| `centro_costo_id` | UUID | FK | sí | FK, NULL |
| `codigo_inventario` | VARCHAR(30) | UQ | no | UQ |
| `descripcion` | VARCHAR(200) | — | no | — |
| `fecha_adquisicion` | DATE | — | no | — |
| `costo_adquisicion` | DECIMAL(14,2) | — | no | CK: > 0 |
| `moneda` | CHAR(3) | — | no | — |
| `valor_residual` | DECIMAL(14,2) | — | no | — |
| `depreciacion_acumulada` | DECIMAL(14,2) | — | no | — |
| `valor_en_libros` | DECIMAL(14,2) | — | no | GENERATED |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `factura_proveedor_id` | UUID | FK | sí | FK, NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_activo_fijo_depreciacion` | CHECK | `costo_adquisicion`, `depreciacion_acumulada`, `valor_residual` |
| `ck_activo_fijo_residual` | CHECK | `costo_adquisicion`, `valor_residual` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `categoria_activo_fijo_id` | [[categoria_activo_fijo]] | 13 | no | [[activo_fijo.categoria_activo_fijo_id → categoria_activo_fijo]] |
| `centro_costo_id` | [[centro_costo]] | 13 | sí | [[activo_fijo.centro_costo_id → centro_costo]] |
| `factura_proveedor_id` | [[factura_proveedor]] | 13 | sí | [[activo_fijo.factura_proveedor_id → factura_proveedor]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[depreciacion_activo]] | `activo_fijo_id` | 13 | [[depreciacion_activo.activo_fijo_id → activo_fijo]] |

## Entidades vecinas

[[categoria_activo_fijo]] · [[centro_costo]] · [[depreciacion_activo]] · [[factura_proveedor]]

## Ver también

- Justificación de negocio: [[13_contabilidad_erp]]
- Diagramas: `docs/entidades/13_contabilidad_erp.puml`
- Índice: [[_Entidades]] · [[Index]]
