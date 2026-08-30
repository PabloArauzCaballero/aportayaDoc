---
tags:
  - entidad
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
tabla: tarifario
clase: Tarifario
modulo: "11 — Tarifas, Comisiones, Impuestos y Facturación"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 15
fk_salientes: 2
fk_entrantes: 9
append_only: false
---

# `tarifario`

> Módulo [[11_tarifas_comisiones|11 — Tarifas, Comisiones, Impuestos y Facturación]] · clase `Tarifario` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `codigo` | VARCHAR(30) | UQ | no | UQ+version |
| `version` | SMALLINT | — | no | — |
| `nombre` | VARCHAR(120) | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `moneda_base` | CHAR(3) | — | no | — |
| `vigente_desde` | TIMESTAMPTZ | IDX | no | IDX |
| `vigente_hasta` | TIMESTAMPTZ | — | sí | NULL |
| `dias_preaviso` | SMALLINT | — | no | — |
| `publicado_en` | TIMESTAMPTZ | — | sí | NULL |
| `url_publicacion` | VARCHAR(255) | — | sí | NULL |
| `hash_documento` | VARCHAR(64) | — | sí | NULL |
| `tarifario_anterior_id` | UUID | FK | sí | FK, NULL |
| `aprobado_por` | UUID | FK | sí | FK, NULL |
| `acta_aprobacion` | VARCHAR(80) | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_tarifario_publicado` | CHECK | `estado`, `hash_documento`, `publicado_en`, `url_publicacion` |
| `ex_tarifario_vigente` | EXCLUDE | `codigo`, `vigente_desde`, `vigente_hasta` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `aprobado_por` | [[usuario]] | ↗ 01 | sí | [[tarifario.aprobado_por → usuario]] |
| `tarifario_anterior_id` | [[tarifario]] | 11 | sí | [[tarifario.tarifario_anterior_id → tarifario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[asignacion_tarifario]] | `tarifario_id` | 11 | [[asignacion_tarifario.tarifario_id → tarifario]] |
| [[cambio_tarifario]] | `tarifario_anterior_id` | 11 | [[cambio_tarifario.tarifario_anterior_id → tarifario]] |
| [[cambio_tarifario]] | `tarifario_nuevo_id` | 11 | [[cambio_tarifario.tarifario_nuevo_id → tarifario]] |
| [[concepto_tarifa]] | `tarifario_id` | 11 | [[concepto_tarifa.tarifario_id → tarifario]] |
| [[cotizacion_comision]] | `tarifario_id` | 11 | [[cotizacion_comision.tarifario_id → tarifario]] |
| [[devengo_comision]] | `tarifario_id` | 11 | [[devengo_comision.tarifario_id → tarifario]] |
| [[simulacion_tarifa]] | `tarifario_id` | 11 | [[simulacion_tarifa.tarifario_id → tarifario]] |
| [[tarifa_congelada_grupo]] | `tarifario_id` | 11 | [[tarifa_congelada_grupo.tarifario_id → tarifario]] |
| [[tarifario]] | `tarifario_anterior_id` | 11 | [[tarifario.tarifario_anterior_id → tarifario]] |

## Entidades vecinas

[[asignacion_tarifario]] · [[cambio_tarifario]] · [[concepto_tarifa]] · [[cotizacion_comision]] · [[devengo_comision]] · [[simulacion_tarifa]] · [[tarifa_congelada_grupo]] · [[tarifario]] · [[usuario]]

## Notas del modelo

> **Regla dura**
> Solo un tarifario en estado VIGENTE por
> (codigo, ambito) en una fecha dada:
> EXCLUDE USING gist (codigo WITH =,
> tstzrange(vigente_desde, vigente_hasta) WITH &&)
> WHERE (estado = 'VIGENTE').
> Un tarifario VIGENTE es inmutable: para cambiar
> un precio se crea la version siguiente. Los
> tarifarios sustituidos nunca se borran.

## Ver también

- Justificación de negocio: [[11_tarifas_comisiones]]
- Diagramas: `docs/entidades/11_tarifas_comisiones.puml`
- Índice: [[_Entidades]] · [[Index]]
