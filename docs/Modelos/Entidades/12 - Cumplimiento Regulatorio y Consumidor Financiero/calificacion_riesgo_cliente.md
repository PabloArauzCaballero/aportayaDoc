---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: calificacion_riesgo_cliente
clase: CalificacionRiesgoCliente
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 13
fk_salientes: 3
fk_entrantes: 2
append_only: false
---

# `calificacion_riesgo_cliente`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `CalificacionRiesgoCliente` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX, M1 |
| `matriz_riesgo_id` | UUID | FK | sí | FK, NULL |
| `calificado_por` | UUID | FK | sí | FK, NULL |
| `nivel` | VARCHAR(6) | IDX | no | CK, IDX |
| `puntaje_total` | DECIMAL(6,2) | — | no | — |
| `nivel_dd_requerido` | VARCHAR(15) | — | no | CK |
| `periodicidad_revision_meses` | SMALLINT | — | no | — |
| `vigente_desde` | TIMESTAMPTZ | — | no | — |
| `vigente_hasta` | TIMESTAMPTZ | — | sí | NULL |
| `proxima_revision` | DATE | IDX | no | IDX |
| `es_automatica` | BOOLEAN | — | no | — |
| `motivo_cambio` | VARCHAR(300) | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ex_calificacion_vigente` | EXCLUDE | `usuario_id`, `vigente_desde`, `vigente_hasta` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `calificado_por` | [[usuario]] | ↗ 01 | sí | [[calificacion_riesgo_cliente.calificado_por → usuario]] |
| `matriz_riesgo_id` | [[matriz_riesgo_lft]] | 12 | sí | [[calificacion_riesgo_cliente.matriz_riesgo_id → matriz_riesgo_lft]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[calificacion_riesgo_cliente.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[debida_diligencia]] | `calificacion_riesgo_id` | 12 | [[debida_diligencia.calificacion_riesgo_id → calificacion_riesgo_cliente]] |
| [[revision_periodica_kyc]] | `calificacion_riesgo_id` | 12 | [[revision_periodica_kyc.calificacion_riesgo_id → calificacion_riesgo_cliente]] |

## Entidades vecinas

[[debida_diligencia]] · [[matriz_riesgo_lft]] · [[revision_periodica_kyc]] · [[usuario]]

## Notas del modelo

> **Una sola calificacion vigente por usuario**
> EXCLUDE USING gist (usuario_id WITH =,
> tstzrange(vigente_desde, vigente_hasta) WITH &&).
> Las calificaciones anteriores no se borran: hay que
> poder probar en que nivel estaba el cliente el dia
> de una operacion cuestionada.
> nivel_dd_requerido gobierna los limites de M10:
> cambiar la calificacion cambia lo que el usuario
> puede operar, sin intervencion manual.

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
