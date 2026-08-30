---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: umbral_reporte_uif
clase: UmbralReporteUif
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
estereotipo: Política configurable
clave_primaria: [id]
columnas: 13
fk_salientes: 0
fk_entrantes: 1
append_only: false
---

# `umbral_reporte_uif`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `UmbralReporteUif` · Política configurable

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `formulario` | VARCHAR(10) | UQ | no | CK, UQ+concepto_operacion+es_acumulado+vigente_desde |
| `inciso` | VARCHAR(4) | — | no | — |
| `concepto_operacion` | VARCHAR(30) | — | no | CK |
| `es_acumulado` | BOOLEAN | — | no | — |
| `umbral_usd` | DECIMAL(16,2) | — | no | — |
| `ventana_dias_calendario` | SMALLINT | — | sí | NULL |
| `exige_declaracion_origen_destino` | BOOLEAN | — | no | — |
| `reinicia_tras_superar` | BOOLEAN | — | no | — |
| `base_normativa` | VARCHAR(160) | — | no | — |
| `vigente_desde` | DATE | — | no | — |
| `vigente_hasta` | DATE | — | sí | NULL |
| `activo` | BOOLEAN | IDX | no | IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_umbral_base_normativa` | CHECK | `base_normativa` |
| `ck_umbral_ventana` | CHECK | `es_acumulado`, `ventana_dias_calendario` |
| `ex_umbral_vigencia` | EXCLUDE | `concepto_operacion`, `es_acumulado`, `formulario`, `vigente_desde`, `vigente_hasta` |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[registro_operacion_relevante]] | `umbral_reporte_id` | 12 | [[registro_operacion_relevante.umbral_reporte_id → umbral_reporte_uif]] |

## Entidades vecinas

[[registro_operacion_relevante]]

## Notas del modelo

> **Los umbrales son datos, no constantes**
> Cada fila reproduce un inciso del instructivo de la
> unidad de inteligencia financiera: formulario,
> concepto de operacion, si es acumulado, umbral en
> USD, ventana en dias calendario y si exige declarar
> origen y destino. Cuando la autoridad cambia un
> umbral (paso reciente: carga y retiro de billetera
> movil acumulados >= USD 1.000 en 1 a 3 dias), es una
> fila nueva con vigencia, no un despliegue.
> base_normativa guarda el articulo exacto.

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
