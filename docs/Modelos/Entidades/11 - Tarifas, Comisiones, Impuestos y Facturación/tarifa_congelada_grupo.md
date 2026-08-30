---
tags:
  - entidad
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
tabla: tarifa_congelada_grupo
clase: TarifaCongeladaGrupo
modulo: "11 — Tarifas, Comisiones, Impuestos y Facturación"
estereotipo: Objeto de valor
clave_primaria: [id]
columnas: 8
fk_salientes: 3
fk_entrantes: 0
append_only: false
---

# `tarifa_congelada_grupo`

> Módulo [[11_tarifas_comisiones|11 — Tarifas, Comisiones, Impuestos y Facturación]] · clase `TarifaCongeladaGrupo` · Objeto de valor

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `grupo_id` | UUID | FK UQ | no | FK, M2 |
| `tarifario_id` | UUID | FK | no | FK |
| `acuerdo_id` | UUID | FK | sí | FK, NULL, M2 |
| `snapshot_conceptos` | JSONB | — | no | — |
| `hash_snapshot` | VARCHAR(64) | — | no | — |
| `congelada_en` | TIMESTAMPTZ | — | no | — |
| `vigente_hasta_ciclo_nro` | SMALLINT | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_tarifa_congelada_grupo` | UNIQUE | `grupo_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `acuerdo_id` | [[acuerdo]] | ↗ 02 | sí | [[tarifa_congelada_grupo.acuerdo_id → acuerdo]] |
| `grupo_id` | [[grupo]] | ↗ 02 | no | [[tarifa_congelada_grupo.grupo_id → grupo]] |
| `tarifario_id` | [[tarifario]] | 11 | no | [[tarifa_congelada_grupo.tarifario_id → tarifario]] |

## Entidades vecinas

[[acuerdo]] · [[grupo]] · [[tarifario]]

## Ver también

- Justificación de negocio: [[11_tarifas_comisiones]]
- Diagramas: `docs/entidades/11_tarifas_comisiones.puml`
- Índice: [[_Entidades]] · [[Index]]
