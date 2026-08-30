---
tags:
  - entidad
  - modulo/14-publicidad-y-campanas
tabla: campana_publicitaria
clase: CampanaPublicitaria
modulo: "14 — Publicidad y Campañas"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 11
fk_salientes: 2
fk_entrantes: 1
append_only: false
---

# `campana_publicitaria`

> Módulo [[14_publicidad_campanas|14 — Publicidad y Campañas]] · clase `CampanaPublicitaria` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `cuenta_publicitaria_id` | UUID | FK IDX | no | FK, IDX |
| `nombre` | VARCHAR(120) | — | no | — |
| `objetivo` | VARCHAR(25) | — | no | CK |
| `presupuesto_total` | DECIMAL(14,2) | — | no | CK: > 0 |
| `presupuesto_consumido` | DECIMAL(14,2) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `fecha_inicio` | TIMESTAMPTZ | — | no | — |
| `fecha_fin` | TIMESTAMPTZ | — | sí | NULL |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `aprobada_por` | UUID | FK | sí | FK, NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_campana_pub_aprobacion` | CHECK | `aprobada_por`, `estado` |
| `ck_campana_pub_consumo` | CHECK | `presupuesto_consumido`, `presupuesto_total` |
| `ck_campana_pub_vigencia` | CHECK | `fecha_fin`, `fecha_inicio` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `aprobada_por` | [[usuario]] | ↗ 01 | sí | [[campana_publicitaria.aprobada_por → usuario]] |
| `cuenta_publicitaria_id` | [[cuenta_publicitaria]] | 14 | no | [[campana_publicitaria.cuenta_publicitaria_id → cuenta_publicitaria]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[conjunto_anuncios]] | `campana_publicitaria_id` | 14 | [[conjunto_anuncios.campana_publicitaria_id → campana_publicitaria]] |

## Entidades vecinas

[[conjunto_anuncios]] · [[cuenta_publicitaria]] · [[usuario]]

## Ver también

- Justificación de negocio: [[14_publicidad_campanas]]
- Diagramas: `docs/entidades/14_publicidad_campanas.puml`
- Índice: [[_Entidades]] · [[Index]]
