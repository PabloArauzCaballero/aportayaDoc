---
tags:
  - entidad
  - modulo/14-publicidad-y-campanas
tabla: cuenta_publicitaria
clase: CuentaPublicitaria
modulo: "14 — Publicidad y Campañas"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 7
fk_salientes: 1
fk_entrantes: 2
append_only: false
---

# `cuenta_publicitaria`

> Módulo [[14_publicidad_campanas|14 — Publicidad y Campañas]] · clase `CuentaPublicitaria` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `anunciante_id` | UUID | FK UQ | no | FK |
| `limite_gasto_mensual` | DECIMAL(14,2) | — | sí | NULL |
| `moneda` | CHAR(3) | — | no | — |
| `saldo_consumido_mes` | DECIMAL(14,2) | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `creada_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_cuenta_publicitaria_consumo` | CHECK | `limite_gasto_mensual`, `saldo_consumido_mes` |
| `uq_cuenta_publicitaria_anunciante` | UNIQUE | `anunciante_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `anunciante_id` | [[anunciante]] | 14 | no | [[cuenta_publicitaria.anunciante_id → anunciante]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[campana_publicitaria]] | `cuenta_publicitaria_id` | 14 | [[campana_publicitaria.cuenta_publicitaria_id → cuenta_publicitaria]] |
| [[factura_publicidad]] | `cuenta_publicitaria_id` | 14 | [[factura_publicidad.cuenta_publicitaria_id → cuenta_publicitaria]] |

## Entidades vecinas

[[anunciante]] · [[campana_publicitaria]] · [[factura_publicidad]]

## Ver también

- Justificación de negocio: [[14_publicidad_campanas]]
- Diagramas: `docs/entidades/14_publicidad_campanas.puml`
- Índice: [[_Entidades]] · [[Index]]
