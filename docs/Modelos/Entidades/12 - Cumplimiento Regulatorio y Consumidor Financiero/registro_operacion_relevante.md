---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
  - append-only
tabla: registro_operacion_relevante
clase: RegistroOperacionRelevante
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
estereotipo: Objeto de valor
clave_primaria: [id]
columnas: 25
fk_salientes: 6
fk_entrantes: 1
append_only: true
---

# `registro_operacion_relevante`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `RegistroOperacionRelevante` · Objeto de valor · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX, M1 |
| `transaccion_id` | UUID | FK IDX | no | FK, IDX, M10 |
| `umbral_reporte_id` | UUID | FK IDX | no | FK, IDX |
| `operacion_inicio_ventana_id` | UUID | FK | sí | FK, NULL |
| `declaracion_origen_fondos_id` | UUID | FK | sí | FK, NULL |
| `reporte_regulatorio_id` | UUID | FK | sí | FK, NULL |
| `formulario` | VARCHAR(10) | IDX | no | CK, IDX |
| `concepto_operacion` | VARCHAR(30) | — | no | CK |
| `es_acumulada` | BOOLEAN | — | no | — |
| `ventana_desde` | DATE | — | sí | NULL |
| `ventana_hasta` | DATE | — | sí | NULL |
| `monto` | DECIMAL(16,2) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `monto_acumulado_ventana` | DECIMAL(16,2) | — | no | — |
| `tipo_cambio_aplicado` | DECIMAL(12,6) | — | no | — |
| `monto_equivalente_usd` | DECIMAL(16,2) | IDX | no | IDX |
| `umbral_aplicado_usd` | DECIMAL(16,2) | — | no | — |
| `origen_declarado` | VARCHAR(300) | — | sí | NULL |
| `destino_declarado` | VARCHAR(300) | — | sí | NULL |
| `exento` | BOOLEAN | — | no | — |
| `motivo_exencion` | VARCHAR(120) | — | sí | NULL |
| `periodo_remision` | CHAR(7) | IDX | no | IDX |
| `fecha_operacion` | TIMESTAMPTZ | IDX | no | IDX |
| `registrada_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_operelev_declaracion` | CHECK | `destino_declarado`, `exento`, `formulario`, `motivo_exencion`, `origen_declarado` |
| `ck_operelev_periodo` | CHECK | `periodo_remision` |
| `ck_operelev_tipo_cambio` | CHECK | `moneda`, `tipo_cambio_aplicado` |
| `ck_operelev_ventana` | CHECK | `es_acumulada`, `ventana_desde`, `ventana_hasta` |
| `ix_operelev_periodo` | INDEX parcial | `periodo_remision`, `formulario` |
| `ix_operelev_usuario_fecha` | INDEX | expresión |
| `uq_operelev_tx_umbral` | UNIQUE | `transaccion_id`, `umbral_reporte_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `declaracion_origen_fondos_id` | [[declaracion_origen_fondos]] | 12 | sí | [[registro_operacion_relevante.declaracion_origen_fondos_id → declaracion_origen_fondos]] |
| `operacion_inicio_ventana_id` | [[registro_operacion_relevante]] | 12 | sí | [[registro_operacion_relevante.operacion_inicio_ventana_id → registro_operacion_relevante]] |
| `reporte_regulatorio_id` | [[reporte_regulatorio]] | 12 | sí | [[registro_operacion_relevante.reporte_regulatorio_id → reporte_regulatorio]] |
| `transaccion_id` | [[transaccion_billetera]] | ↗ 10 | no | [[registro_operacion_relevante.transaccion_id → transaccion_billetera]] |
| `umbral_reporte_id` | [[umbral_reporte_uif]] | 12 | no | [[registro_operacion_relevante.umbral_reporte_id → umbral_reporte_uif]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[registro_operacion_relevante.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[registro_operacion_relevante]] | `operacion_inicio_ventana_id` | 12 | [[registro_operacion_relevante.operacion_inicio_ventana_id → registro_operacion_relevante]] |

## Entidades vecinas

[[declaracion_origen_fondos]] · [[registro_operacion_relevante]] · [[reporte_regulatorio]] · [[transaccion_billetera]] · [[umbral_reporte_uif]] · [[usuario]]

## Notas del modelo

> **Ventana de acumulacion con reinicio**
> operacion_inicio_ventana_id implementa la regla
> "se considera como inicio la operacion posterior a
> la ultima que hubiera superado el umbral".
> En operaciones acumuladas solo se declara origen y
> destino de la ultima operacion con la que se alcanza
> el umbral. monto_equivalente_usd guarda el tipo de
> cambio aplicado para que el umbral sea reproducible.
> periodo_remision alimenta el envio mensual: hasta el
> dia 15 del mes siguiente, y si no hubo operaciones
> igual se informa (reporte_regulatorio.reporte_en_cero).

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
