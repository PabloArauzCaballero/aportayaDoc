---
tags:
  - entidad
  - modulo/09-auditoria-reportes-y-cumplimiento
tabla: definicion_indicador
clase: DefinicionIndicador
modulo: "09 — Auditoría, Reportes y Cumplimiento"
estereotipo: Política configurable
clave_primaria: [id]
columnas: 11
fk_salientes: 0
fk_entrantes: 1
append_only: false
---

# `definicion_indicador`

> Módulo [[09_auditoria_reportes|09 — Auditoría, Reportes y Cumplimiento]] · clase `DefinicionIndicador` · Política configurable

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `codigo` | VARCHAR(40) | UQ | no | UQ+version |
| `version` | VARCHAR(20) | — | no | — |
| `familia` | VARCHAR(20) | — | no | CK |
| `dueno_familia` | VARCHAR(80) | — | no | — |
| `sentido_meta` | VARCHAR(20) | — | no | CK |
| `formula` | VARCHAR(400) | — | no | — |
| `fuente` | VARCHAR(300) | — | no | — |
| `minimo_casos` | SMALLINT | — | no | — |
| `vigente_desde` | TIMESTAMPTZ | — | no | — |
| `vigente_hasta` | TIMESTAMPTZ | — | sí | NULL |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[indicador_kpi]] | `definicion_indicador_id` | 09 | [[indicador_kpi.definicion_indicador_id → definicion_indicador]] |

## Entidades vecinas

[[indicador_kpi]]

## Notas del modelo

> Un indicador es una DEFINICION, no una consulta: si dos lugares lo recalculan,
> ya hay dos indicadores. Aca vive lo que permite interpretarlo y lo que hace que
> un numero de hace un ano vuelva a salir igual.
> 
> La `version` se cierra y se abre otra cuando la formula cambia; la serie vieja
> conserva la suya, y por eso el corte de serie se puede senalar en el grafico en
> vez de aparecer como una mejora del 40 por ciento que nadie explica.
> 
> `minimo_casos` es el piso de muestra para publicar sin identificar personas:
> un promedio de tres personas identifica a las tres (R-SEG-03).

## Ver también

- Justificación de negocio: [[09_auditoria_reportes]]
- Diagramas: `docs/entidades/09_auditoria_reportes.puml`
- Índice: [[_Entidades]] · [[Index]]
