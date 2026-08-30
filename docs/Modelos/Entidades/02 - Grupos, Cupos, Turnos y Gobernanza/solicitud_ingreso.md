---
tags:
  - entidad
  - modulo/02-grupos-cupos-turnos-y-gobernanza
tabla: solicitud_ingreso
clase: SolicitudIngreso
modulo: "02 — Grupos, Cupos, Turnos y Gobernanza"
clave_primaria: [id]
columnas: 10
fk_salientes: 3
fk_entrantes: 0
append_only: false
---

# `solicitud_ingreso`

> Módulo [[02_grupos_turnos|02 — Grupos, Cupos, Turnos y Gobernanza]] · clase `SolicitudIngreso`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `grupo_id` | UUID | FK IDX | no | FK, IDX |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `cupos_solicitados` | SMALLINT | — | no | — |
| `mensaje` | VARCHAR(300) | — | sí | NULL |
| `estado` | VARCHAR(15) | — | no | CK |
| `puntaje_compatibilidad` | DECIMAL(5,2) | — | sí | NULL |
| `revisada_por` | UUID | FK | sí | FK, NULL |
| `fecha_solicitud` | TIMESTAMPTZ | — | no | — |
| `fecha_resolucion` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_solicitud_ingreso_resuelta` | CHECK | `estado`, `fecha_resolucion`, `revisada_por` |
| `uq_solicitud_ingreso_pendiente` | UNIQUE parcial | `grupo_id`, `usuario_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `grupo_id` | [[grupo]] | 02 | no | [[solicitud_ingreso.grupo_id → grupo]] |
| `revisada_por` | [[usuario]] | ↗ 01 | sí | [[solicitud_ingreso.revisada_por → usuario]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[solicitud_ingreso.usuario_id → usuario]] |

## Entidades vecinas

[[grupo]] · [[usuario]]

## Ver también

- Justificación de negocio: [[02_grupos_turnos]]
- Diagramas: `docs/entidades/02_grupos_turnos.puml`
- Índice: [[_Entidades]] · [[Index]]
