---
tags:
  - entidad
  - modulo/02-grupos-cupos-turnos-y-gobernanza
tabla: turno
clase: Turno
modulo: "02 — Grupos, Cupos, Turnos y Gobernanza"
clave_primaria: [id]
columnas: 11
fk_salientes: 4
fk_entrantes: 4
append_only: false
---

# `turno`

> Módulo [[02_grupos_turnos|02 — Grupos, Cupos, Turnos y Gobernanza]] · clase `Turno`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `grupo_id` | UUID | FK IDX | no | FK, IDX |
| `periodo_id` | UUID | FK UQ | no | FK |
| `cupo_id` | UUID | FK | no | FK |
| `orden_asignado` | SMALLINT | — | no | — |
| `estado` | VARCHAR(15) | — | no | CK |
| `criterio_asignacion` | VARCHAR(25) | — | no | — |
| `monto_estimado_cobro` | DECIMAL(14,2) | — | no | — |
| `descuento_subasta` | DECIMAL(14,2) | — | sí | NULL |
| `permutado_con_turno_id` | UUID | FK | sí | FK, NULL |
| `confirmado_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_turno_orden` | UNIQUE | `grupo_id`, `orden_asignado` |
| `uq_turno_periodo` | UNIQUE | `periodo_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cupo_id` | [[cupo]] | 02 | no | [[turno.cupo_id → cupo]] |
| `grupo_id` | [[grupo]] | 02 | no | [[turno.grupo_id → grupo]] |
| `periodo_id` | [[periodo]] | 02 | no | [[turno.periodo_id → periodo]] |
| `permutado_con_turno_id` | [[turno]] | 02 | sí | [[turno.permutado_con_turno_id → turno]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[entrega_fondo]] | `turno_id` | ↗ 04 | [[entrega_fondo.turno_id → turno]] |
| [[solicitud_permuta]] | `turno_destino_id` | 02 | [[solicitud_permuta.turno_destino_id → turno]] |
| [[solicitud_permuta]] | `turno_origen_id` | 02 | [[solicitud_permuta.turno_origen_id → turno]] |
| [[turno]] | `permutado_con_turno_id` | 02 | [[turno.permutado_con_turno_id → turno]] |

## Entidades vecinas

[[cupo]] · [[entrega_fondo]] · [[grupo]] · [[periodo]] · [[solicitud_permuta]] · [[turno]]

## Notas del modelo

> Reglas de integridad:
> - UNIQUE (periodo_id) si la modalidad entrega
> un solo beneficiario por periodo.
> - UNIQUE (grupo_id, orden_asignado).
> - CHECK: el cupo pertenece al mismo grupo
> que el periodo (trigger de coherencia).

## Ver también

- Justificación de negocio: [[02_grupos_turnos]]
- Diagramas: `docs/entidades/02_grupos_turnos.puml`
- Índice: [[_Entidades]] · [[Index]]
