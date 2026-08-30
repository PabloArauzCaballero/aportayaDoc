---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: reclamo_cliente
clase: ReclamoCliente
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 25
fk_salientes: 5
fk_entrantes: 2
append_only: false
---

# `reclamo_cliente`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `ReclamoCliente` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `codigo` | VARCHAR(20) | UQ | no | — |
| `usuario_id` | UUID | FK IDX | no | FK, IDX, M1 |
| `punto_reclamo_id` | UUID | FK | no | FK |
| `responsable_id` | UUID | FK | sí | FK, NULL |
| `ticket_soporte_id` | UUID | FK | sí | FK, NULL, M9 |
| `devolucion_comision_id` | UUID | FK | sí | FK, NULL, M11 |
| `categoria` | VARCHAR(30) | IDX | no | CK, IDX |
| `producto` | VARCHAR(30) | — | no | — |
| `monto_reclamado` | DECIMAL(14,2) | — | sí | NULL |
| `descripcion` | TEXT | — | no | — |
| `canal_ingreso` | VARCHAR(15) | — | no | CK |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `fecha_ingreso` | TIMESTAMPTZ | IDX | no | IDX |
| `dias_habiles_plazo` | SMALLINT | — | no | — |
| `plazo_respuesta` | TIMESTAMPTZ | IDX | no | IDX |
| `plazo_prorrogado_hasta` | TIMESTAMPTZ | — | sí | NULL |
| `prorroga_comunicada_al_cliente_en` | TIMESTAMPTZ | — | sí | NULL |
| `prorroga_comunicada_al_organismo_en` | TIMESTAMPTZ | — | sí | NULL |
| `justificacion_prorroga` | VARCHAR(400) | — | sí | NULL |
| `fecha_respuesta` | TIMESTAMPTZ | — | sí | NULL |
| `resultado` | VARCHAR(15) | — | sí | CK, NULL |
| `respuesta` | TEXT | — | sí | NULL |
| `incluido_en_reporte_mensual` | CHAR(7) | IDX | sí | NULL, IDX |
| `conservar_hasta` | DATE | IDX | no | IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_reclamo_conservacion` | CHECK | `conservar_hasta`, `fecha_ingreso` |
| `ck_reclamo_dias` | CHECK | `dias_habiles_plazo` |
| `ck_reclamo_plazo` | CHECK | `fecha_ingreso`, `plazo_respuesta` |
| `ck_reclamo_prorroga` | CHECK | `plazo_prorrogado_hasta`, `plazo_respuesta`, `prorroga_comunicada_al_cliente_en` |
| `ck_reclamo_prorroga_extendida` | CHECK | `fecha_ingreso`, `justificacion_prorroga`, `plazo_prorrogado_hasta`, `prorroga_comunicada_al_organismo_en` |
| `ck_reclamo_reparacion` | CHECK | `devolucion_comision_id`, `estado`, `monto_reclamado`, `resultado` |
| `ix_reclamo_vencidos` | INDEX parcial | `plazo_respuesta` |
| `uq_reclamo_codigo` | UNIQUE | `codigo` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `devolucion_comision_id` | [[devolucion_comision]] | ↗ 11 | sí | [[reclamo_cliente.devolucion_comision_id → devolucion_comision]] |
| `punto_reclamo_id` | [[punto_reclamo]] | 12 | no | [[reclamo_cliente.punto_reclamo_id → punto_reclamo]] |
| `responsable_id` | [[usuario]] | ↗ 01 | sí | [[reclamo_cliente.responsable_id → usuario]] |
| `ticket_soporte_id` | [[ticket_soporte]] | ↗ 09 | sí | [[reclamo_cliente.ticket_soporte_id → ticket_soporte]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[reclamo_cliente.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[devolucion_comision]] | `reclamo_id` | ↗ 11 | [[devolucion_comision.reclamo_id → reclamo_cliente]] |
| [[instancia_reclamo]] | `reclamo_id` | 12 | [[instancia_reclamo.reclamo_id → reclamo_cliente]] |

## Entidades vecinas

[[devolucion_comision]] · [[instancia_reclamo]] · [[punto_reclamo]] · [[ticket_soporte]] · [[usuario]]

## Notas del modelo

> **Reglas duras**
> - plazo_respuesta se calcula al ingresar y se guarda:
> si la norma cambia, los reclamos viejos conservan
> el plazo que les regia.
> - Un reclamo con resultado FAVORABLE y monto
> reclamado exige una devolucion asociada
> (devolucion_comision_id en M11) o una transaccion
> de resarcimiento en M10.
> - Indice parcial de vencidos:
> CREATE INDEX ON reclamo_cliente (plazo_respuesta)
> WHERE estado IN ('INGRESADO','EN_ANALISIS');

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
