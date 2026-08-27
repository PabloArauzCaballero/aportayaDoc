---
tags:
  - entidad
  - modulo/09-auditoria-reportes-y-cumplimiento
tabla: indicador_kpi
clase: IndicadorKPI
modulo: "09 — Auditoría, Reportes y Cumplimiento"
clave_primaria: [id]
columnas: 14
fk_salientes: 1
fk_entrantes: 0
append_only: false
---

# `indicador_kpi`

> Módulo [[09_auditoria_reportes|09 — Auditoría, Reportes y Cumplimiento]] · clase `IndicadorKPI`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `definicion_indicador_id` | UUID | FK IDX | no | FK, IDX |
| `codigo` | VARCHAR(40) | UQ | no | UQ+dimension+dimension_id+periodo+definicion_indicador_id |
| `nombre` | VARCHAR(80) | — | no | — |
| `valor` | DECIMAL(16,4) | — | no | — |
| `unidad` | VARCHAR(15) | — | no | — |
| `dimension` | VARCHAR(20) | — | no | CK |
| `dimension_id` | UUID | — | sí | NULL |
| `periodo` | VARCHAR(10) | — | no | — |
| `meta` | DECIMAL(16,4) | — | sí | NULL |
| `variacion_periodo_anterior` | DECIMAL(8,4) | — | sí | NULL |
| `provisorio` | BOOLEAN | — | no | — |
| `casos` | INTEGER | — | sí | NULL |
| `calculado_en` | TIMESTAMPTZ | — | no | — |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `definicion_indicador_id` | [[definicion_indicador]] | 09 | no | [[indicador_kpi.definicion_indicador_id → definicion_indicador]] |

## Entidades vecinas

[[definicion_indicador]]

## Notas del modelo

> `provisorio` viaja con el numero y no se deduce al consultar: un indicador sobre
> un periodo sin cuadrar es una opinion, y quien lo calculo es el unico que sabe si
> los cierres cerraron. Deducirlo al publicar obligaria a auditoria a leer el
> esquema de nucleo-financiero, que no puede.
> 
> `casos` es el tamano de la muestra. Sin el no se puede aplicar el minimo de
> privacidad, y el valor se publicaria identificando a las personas que lo componen.
> 
> La tabla es APPEND-ONLY: un indicador corregido no pisa al anterior, entra como
> fila nueva con la version de definicion con que se recalculo. Por eso la unicidad
> incluye `definicion_indicador_id`: la serie vieja sigue disponible y el corte queda
> senalado, en vez de desaparecer bajo un UPDATE que nadie ve.
> 
> `definicion_indicador_id` ata el numero a la version de definicion con que se
> calculo: es lo
> que lo vuelve reproducible. El nombre lleva la tabla entera a proposito:
> `definicion_id` a secas ya esta tomado por `definicion_reporte`, en este mismo
> modulo, y la FK habria resuelto silenciosamente a la tabla equivocada.

## Ver también

- Justificación de negocio: [[09_auditoria_reportes]]
- Diagramas: `docs/entidades/09_auditoria_reportes.puml`
- Índice: [[_Entidades]] · [[Index]]
