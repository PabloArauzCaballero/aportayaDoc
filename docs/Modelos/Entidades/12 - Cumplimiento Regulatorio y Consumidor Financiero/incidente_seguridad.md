---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: incidente_seguridad
clase: IncidenteSeguridad
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 18
fk_salientes: 4
fk_entrantes: 0
append_only: false
---

# `incidente_seguridad`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `IncidenteSeguridad` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `codigo` | VARCHAR(20) | UQ | no | UQ |
| `activo_informacion_id` | UUID | FK IDX | sí | FK, NULL, IDX |
| `incidente_operativo_id` | UUID | FK | sí | FK, NULL, M9 |
| `evento_riesgo_id` | UUID | FK | sí | FK, NULL |
| `responsable_id` | UUID | FK | sí | FK, NULL |
| `tipo` | VARCHAR(30) | IDX | no | CK, IDX |
| `severidad` | VARCHAR(10) | IDX | no | CK, IDX |
| `vector_ataque` | VARCHAR(60) | — | sí | NULL |
| `datos_personales_afectados` | BOOLEAN | IDX | no | IDX |
| `usuarios_afectados` | INTEGER | — | no | — |
| `detectado_en` | TIMESTAMPTZ | IDX | no | IDX |
| `contenido_en` | TIMESTAMPTZ | — | sí | NULL |
| `reportado_al_organismo_en` | TIMESTAMPTZ | — | sí | NULL |
| `notificado_a_titulares_en` | TIMESTAMPTZ | — | sí | NULL |
| `plazo_reporte` | TIMESTAMPTZ | IDX | no | IDX |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `leccion_aprendida` | TEXT | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_incidente_notificacion` | CHECK | `datos_personales_afectados`, `estado`, `notificado_a_titulares_en` |
| `ck_incidente_plazo` | CHECK | `detectado_en`, `plazo_reporte` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `activo_informacion_id` | [[activo_informacion]] | 12 | sí | [[incidente_seguridad.activo_informacion_id → activo_informacion]] |
| `evento_riesgo_id` | [[evento_riesgo_operativo]] | 12 | sí | [[incidente_seguridad.evento_riesgo_id → evento_riesgo_operativo]] |
| `incidente_operativo_id` | [[incidente_operativo]] | ↗ 09 | sí | [[incidente_seguridad.incidente_operativo_id → incidente_operativo]] |
| `responsable_id` | [[usuario]] | ↗ 01 | sí | [[incidente_seguridad.responsable_id → usuario]] |

## Entidades vecinas

[[activo_informacion]] · [[evento_riesgo_operativo]] · [[incidente_operativo]] · [[usuario]]

## Notas del modelo

> Un incidente con datos personales afectados dispara
> tres relojes distintos: contencion, reporte al
> organismo supervisor y notificacion a los titulares.
> plazo_reporte se calcula al detectar y se guarda,
> para poder demostrar si se cumplio con el plazo que
> regia ese dia.

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
