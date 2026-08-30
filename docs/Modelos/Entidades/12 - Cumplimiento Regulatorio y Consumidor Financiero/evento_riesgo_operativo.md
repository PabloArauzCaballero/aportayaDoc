---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
  - append-only
tabla: evento_riesgo_operativo
clase: EventoRiesgoOperativo
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 18
fk_salientes: 2
fk_entrantes: 2
append_only: true
---

# `evento_riesgo_operativo`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `EventoRiesgoOperativo` · Raíz de agregado · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `codigo` | VARCHAR(20) | UQ | no | UQ |
| `incidente_operativo_id` | UUID | FK | sí | FK, NULL, M9 |
| `registrado_por` | UUID | FK | no | FK |
| `categoria_evento` | VARCHAR(35) | IDX | no | CK, IDX |
| `factor_riesgo` | VARCHAR(30) | IDX | no | CK, IDX |
| `reportado_central_riesgo_operativo` | BOOLEAN | IDX | no | IDX |
| `linea_negocio` | VARCHAR(40) | — | no | — |
| `descripcion` | TEXT | — | no | — |
| `fecha_ocurrencia` | TIMESTAMPTZ | IDX | no | IDX |
| `fecha_deteccion` | TIMESTAMPTZ | — | no | — |
| `fecha_contabilizacion` | TIMESTAMPTZ | — | sí | NULL |
| `perdida_bruta` | DECIMAL(16,2) | — | no | — |
| `recuperacion` | DECIMAL(16,2) | — | no | — |
| `perdida_neta` | DECIMAL(16,2) | — | no | GENERATED |
| `moneda` | CHAR(3) | — | no | — |
| `causa_raiz` | TEXT | — | sí | NULL |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_evento_categoria` | CHECK | `categoria_evento` |
| `ck_evento_factor` | CHECK | `factor_riesgo` |
| `ck_evento_fechas` | CHECK | `fecha_deteccion`, `fecha_ocurrencia` |
| `ck_evento_recuperacion` | CHECK | `perdida_bruta`, `recuperacion` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `incidente_operativo_id` | [[incidente_operativo]] | ↗ 09 | sí | [[evento_riesgo_operativo.incidente_operativo_id → incidente_operativo]] |
| `registrado_por` | [[usuario]] | ↗ 01 | no | [[evento_riesgo_operativo.registrado_por → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[incidente_seguridad]] | `evento_riesgo_id` | 12 | [[incidente_seguridad.evento_riesgo_id → evento_riesgo_operativo]] |
| [[plan_accion_riesgo]] | `evento_riesgo_id` | 12 | [[plan_accion_riesgo.evento_riesgo_id → evento_riesgo_operativo]] |

## Entidades vecinas

[[incidente_operativo]] · [[incidente_seguridad]] · [[plan_accion_riesgo]] · [[usuario]]

## Notas del modelo

> **Base de datos de perdidas**
> append-only. Cada descuadre de custodia (M10), cada
> reverso por error operativo y cada fallo con impacto
> monetario entra aca. Es lo que permite responder
> "cuanto nos costo la operacion mal hecha el ultimo
> anio" con un numero y no con una impresion.

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
