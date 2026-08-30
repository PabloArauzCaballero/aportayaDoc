---
tags:
  - entidad
  - modulo/13-contabilidad-financiera-y-erp
  - append-only
tabla: cuenta_por_cobrar
clase: CuentaPorCobrar
modulo: "13 — Contabilidad Financiera y ERP"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 10
fk_salientes: 1
fk_entrantes: 2
append_only: true
---

# `cuenta_por_cobrar`

> Módulo [[13_contabilidad_erp|13 — Contabilidad Financiera y ERP]] · clase `CuentaPorCobrar` · Raíz de agregado · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `origen_tipo` | VARCHAR(20) | IDX | no | CK, IDX |
| `origen_id` | UUID | IDX | no | IDX, polimorfica |
| `tercero_comercial_id` | UUID | FK IDX | sí | FK, NULL, IDX |
| `monto` | DECIMAL(14,2) | — | no | CK: > 0 |
| `moneda` | CHAR(3) | — | no | — |
| `monto_cobrado` | DECIMAL(14,2) | — | no | — |
| `saldo_pendiente` | DECIMAL(14,2) | — | no | GENERATED |
| `fecha_vencimiento` | DATE | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_cxc_cobrado` | CHECK | `monto`, `monto_cobrado` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `tercero_comercial_id` | [[tercero_comercial]] | 13 | sí | [[cuenta_por_cobrar.tercero_comercial_id → tercero_comercial]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[cobro_cuenta_por_cobrar]] | `cuenta_por_cobrar_id` | 13 | [[cobro_cuenta_por_cobrar.cuenta_por_cobrar_id → cuenta_por_cobrar]] |
| [[factura_publicidad]] | `cuenta_por_cobrar_id` | ↗ 14 | [[factura_publicidad.cuenta_por_cobrar_id → cuenta_por_cobrar]] |

## Entidades vecinas

[[cobro_cuenta_por_cobrar]] · [[factura_publicidad]] · [[tercero_comercial]]

## Notas del modelo

> origen_tipo = 'FACTURA_PUBLICIDAD' -> origen_id
> apunta a factura_publicidad.id (M14). Es el unico
> puente entre M13 y M14: la publicidad no factura
> por su cuenta, factura a traves de esta tabla.

## Ver también

- Justificación de negocio: [[13_contabilidad_erp]]
- Diagramas: `docs/entidades/13_contabilidad_erp.puml`
- Índice: [[_Entidades]] · [[Index]]
