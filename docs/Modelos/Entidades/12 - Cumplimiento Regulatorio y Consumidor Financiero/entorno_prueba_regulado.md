---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: entorno_prueba_regulado
clase: EntornoPruebaRegulado
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
clave_primaria: [id]
columnas: 11
fk_salientes: 1
fk_entrantes: 0
append_only: false
---

# `entorno_prueba_regulado`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `EntornoPruebaRegulado`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `licencia_regulatoria_id` | UUID | FK IDX | no | FK, IDX |
| `servicio_en_prueba` | VARCHAR(120) | — | no | — |
| `alcance` | JSONB | — | no | — |
| `limite_usuarios` | INTEGER | — | sí | NULL |
| `limite_monto_operacion` | DECIMAL(16,2) | — | sí | NULL |
| `garantia_constituida` | DECIMAL(16,2) | — | sí | NULL |
| `fecha_inicio` | DATE | — | no | — |
| `fecha_fin` | DATE | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `informes_remitidos` | SMALLINT | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_sandbox_limites` | CHECK | `estado`, `fecha_fin`, `fecha_inicio`, `limite_monto_operacion`, `limite_usuarios` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `licencia_regulatoria_id` | [[licencia_regulatoria]] | 12 | no | [[entorno_prueba_regulado.licencia_regulatoria_id → licencia_regulatoria]] |

## Entidades vecinas

[[licencia_regulatoria]]

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
