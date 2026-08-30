---
tags:
  - entidad
  - modulo/14-publicidad-y-campanas
  - append-only
tabla: factura_publicidad
clase: FacturaPublicidad
modulo: "14 — Publicidad y Campañas"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 9
fk_salientes: 3
fk_entrantes: 0
append_only: true
---

# `factura_publicidad`

> Módulo [[14_publicidad_campanas|14 — Publicidad y Campañas]] · clase `FacturaPublicidad` · Raíz de agregado · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `cuenta_publicitaria_id` | UUID | FK IDX | no | FK, IDX |
| `periodo` | VARCHAR(7) | — | no | — |
| `monto_total` | DECIMAL(14,2) | — | no | CK: > 0 |
| `moneda` | CHAR(3) | — | no | — |
| `factura_electronica_id` | UUID | FK | sí | FK, NULL, M11 |
| `cuenta_por_cobrar_id` | UUID | FK | sí | FK, NULL, M13 |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `generada_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_factura_publicidad_cuenta_periodo` | UNIQUE | `cuenta_publicitaria_id`, `periodo` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_por_cobrar_id` | [[cuenta_por_cobrar]] | ↗ 13 | sí | [[factura_publicidad.cuenta_por_cobrar_id → cuenta_por_cobrar]] |
| `cuenta_publicitaria_id` | [[cuenta_publicitaria]] | 14 | no | [[factura_publicidad.cuenta_publicitaria_id → cuenta_publicitaria]] |
| `factura_electronica_id` | [[factura_electronica]] | ↗ 11 | sí | [[factura_publicidad.factura_electronica_id → factura_electronica]] |

## Entidades vecinas

[[cuenta_por_cobrar]] · [[cuenta_publicitaria]] · [[factura_electronica]]

## Notas del modelo

> Un solo camino de cobro: factura_publicidad ->
> cuenta_por_cobrar (M13) -> cobro_cuenta_por_cobrar ->
> asiento_contable (M3). factura_electronica_id es el
> comprobante fiscal (M11); no reemplaza el
> seguimiento de cobro de M13.

## Ver también

- Justificación de negocio: [[14_publicidad_campanas]]
- Diagramas: `docs/entidades/14_publicidad_campanas.puml`
- Índice: [[_Entidades]] · [[Index]]
