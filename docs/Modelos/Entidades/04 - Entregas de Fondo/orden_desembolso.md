---
tags:
  - entidad
  - modulo/04-entregas-de-fondo
tabla: orden_desembolso
clase: OrdenDesembolso
modulo: "04 — Entregas de Fondo"
clave_primaria: [id]
columnas: 12
fk_salientes: 3
fk_entrantes: 1
append_only: false
---

# `orden_desembolso`

> Módulo [[04_entregas_fondo|04 — Entregas de Fondo]] · clase `OrdenDesembolso`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `entrega_id` | UUID | FK IDX | no | FK, IDX |
| `proveedor_id` | UUID | FK | no | FK, M3 |
| `cuenta_destino_id` | UUID | FK | no | FK |
| `monto` | DECIMAL(14,2) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `estado` | VARCHAR(25) | IDX | no | CK, IDX |
| `referencia_proveedor` | VARCHAR(80) | UQ | sí | UQ, NULL |
| `glosa` | VARCHAR(140) | — | no | — |
| `clave_idempotencia` | VARCHAR(80) | — | no | — |
| `creada_en` | TIMESTAMPTZ | — | no | — |
| `acreditada_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_orden_desembolso_acreditada` | CHECK | `acreditada_en`, `estado`, `referencia_proveedor` |
| `ck_orden_desembolso_monto` | CHECK | `monto` |
| `uq_orden_desembolso_clave` | UNIQUE | `entrega_id`, `clave_idempotencia` |
| `uq_orden_desembolso_entrega_viva` | UNIQUE parcial | `entrega_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_destino_id` | [[cuenta_bancaria_beneficiario]] | 04 | no | [[orden_desembolso.cuenta_destino_id → cuenta_bancaria_beneficiario]] |
| `entrega_id` | [[entrega_fondo]] | 04 | no | [[orden_desembolso.entrega_id → entrega_fondo]] |
| `proveedor_id` | [[proveedor_pago]] | ↗ 03 | no | [[orden_desembolso.proveedor_id → proveedor_pago]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[intento_desembolso]] | `orden_desembolso_id` | 04 | [[intento_desembolso.orden_desembolso_id → orden_desembolso]] |

## Entidades vecinas

[[cuenta_bancaria_beneficiario]] · [[entrega_fondo]] · [[intento_desembolso]] · [[proveedor_pago]]

## Ver también

- Justificación de negocio: [[04_entregas_fondo]]
- Diagramas: `docs/entidades/04_entregas_fondo.puml`
- Índice: [[_Entidades]] · [[Index]]
