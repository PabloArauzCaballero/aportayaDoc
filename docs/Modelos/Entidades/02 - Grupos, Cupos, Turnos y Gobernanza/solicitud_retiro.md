---
tags:
  - entidad
  - modulo/02-grupos-cupos-turnos-y-gobernanza
tabla: solicitud_retiro
clase: SolicitudRetiro
modulo: "02 — Grupos, Cupos, Turnos y Gobernanza"
clave_primaria: [id]
columnas: 9
fk_salientes: 2
fk_entrantes: 0
append_only: false
---

# `solicitud_retiro`

> Módulo [[02_grupos_turnos|02 — Grupos, Cupos, Turnos y Gobernanza]] · clase `SolicitudRetiro`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `participante_id` | UUID | FK UQ | no | FK, UQ parcial |
| `motivo` | VARCHAR(200) | — | no | — |
| `solicitado_en` | TIMESTAMPTZ | — | no | — |
| `estado` | VARCHAR(15) | — | no | CK |
| `posicion` | VARCHAR(10) | — | sí | CK, NULL |
| `plan_regularizacion_id` | UUID | FK | sí | FK, NULL |
| `requiere_reemplazo` | BOOLEAN | — | no | — |
| `liquidacion_calculada` | DECIMAL(14,2) | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_retiro_deudor_con_plan` | CHECK | `estado`, `plan_regularizacion_id`, `posicion` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `participante_id` | [[participante]] | 02 | no | [[solicitud_retiro.participante_id → participante]] |
| `plan_regularizacion_id` | [[plan_regularizacion]] | ↗ 03 | sí | [[solicitud_retiro.plan_regularizacion_id → plan_regularizacion]] |

## Entidades vecinas

[[participante]] · [[plan_regularizacion]]

## Ver también

- Justificación de negocio: [[02_grupos_turnos]]
- Diagramas: `docs/entidades/02_grupos_turnos.puml`
- Índice: [[_Entidades]] · [[Index]]
