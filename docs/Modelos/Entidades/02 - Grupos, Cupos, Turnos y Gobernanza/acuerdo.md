---
tags:
  - entidad
  - modulo/02-grupos-cupos-turnos-y-gobernanza
tabla: acuerdo
clase: Acuerdo
modulo: "02 — Grupos, Cupos, Turnos y Gobernanza"
clave_primaria: [id]
columnas: 15
fk_salientes: 2
fk_entrantes: 8
append_only: false
---

# `acuerdo`

> Módulo [[02_grupos_turnos|02 — Grupos, Cupos, Turnos y Gobernanza]] · clase `Acuerdo`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `grupo_id` | UUID | FK IDX | no | FK, IDX |
| `tipo` | VARCHAR(30) | — | no | CK |
| `descripcion` | VARCHAR(400) | — | no | — |
| `propuesto_por` | UUID | FK | no | FK |
| `quorum_requerido` | DECIMAL(4,3) | — | no | — |
| `votos_a_favor` | SMALLINT | — | no | — |
| `votos_en_contra` | SMALLINT | — | no | — |
| `abstenciones` | SMALLINT | — | no | — |
| `estado` | VARCHAR(15) | — | no | CK |
| `referencia_afectada_id` | UUID | — | sí | NULL, polimorfica |
| `abierto_en` | TIMESTAMPTZ | — | no | — |
| `cierra_en` | TIMESTAMPTZ | — | no | — |
| `resuelto_en` | TIMESTAMPTZ | — | sí | NULL |
| `ejecutado_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_acuerdo_abierto` | UNIQUE parcial | expresión |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `grupo_id` | [[grupo]] | 02 | no | [[acuerdo.grupo_id → grupo]] |
| `propuesto_por` | [[usuario]] | ↗ 01 | no | [[acuerdo.propuesto_por → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[acuerdo_quita]] | `acuerdo_grupo_id` | ↗ 08 | [[acuerdo_quita.acuerdo_grupo_id → acuerdo]] |
| [[disolucion_anticipada]] | `acuerdo_grupo_id` | ↗ 08 | [[disolucion_anticipada.acuerdo_grupo_id → acuerdo]] |
| [[plan_contingencia]] | `acuerdo_grupo_id` | ↗ 08 | [[plan_contingencia.acuerdo_grupo_id → acuerdo]] |
| [[reemplazo_participante]] | `acuerdo_grupo_id` | ↗ 08 | [[reemplazo_participante.acuerdo_grupo_id → acuerdo]] |
| [[sancion]] | `acuerdo_grupo_id` | ↗ 08 | [[sancion.acuerdo_grupo_id → acuerdo]] |
| [[tarifa_congelada_grupo]] | `acuerdo_id` | ↗ 11 | [[tarifa_congelada_grupo.acuerdo_id → acuerdo]] |
| [[traspaso_cupo]] | `aprobado_por_acuerdo_id` | 02 | [[traspaso_cupo.aprobado_por_acuerdo_id → acuerdo]] |
| [[voto_participante]] | `acuerdo_id` | 02 | [[voto_participante.acuerdo_id → acuerdo]] |

## Entidades vecinas

[[acuerdo_quita]] · [[disolucion_anticipada]] · [[grupo]] · [[plan_contingencia]] · [[reemplazo_participante]] · [[sancion]] · [[tarifa_congelada_grupo]] · [[traspaso_cupo]] · [[usuario]] · [[voto_participante]]

## Notas del modelo

> referencia_afectada_id es polimorfica segun
> "tipo": participante.id, turno.id o
> registro_incumplimiento.id (M8).

## Ver también

- Justificación de negocio: [[02_grupos_turnos]]
- Diagramas: `docs/entidades/02_grupos_turnos.puml`
- Índice: [[_Entidades]] · [[Index]]
