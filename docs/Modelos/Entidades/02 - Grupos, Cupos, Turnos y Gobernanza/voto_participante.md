---
tags:
  - entidad
  - modulo/02-grupos-cupos-turnos-y-gobernanza
tabla: voto_participante
clase: VotoParticipante
modulo: "02 — Grupos, Cupos, Turnos y Gobernanza"
clave_primaria: [id]
columnas: 7
fk_salientes: 2
fk_entrantes: 0
append_only: false
---

# `voto_participante`

> Módulo [[02_grupos_turnos|02 — Grupos, Cupos, Turnos y Gobernanza]] · clase `VotoParticipante`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `acuerdo_id` | UUID | FK IDX | no | FK, IDX |
| `participante_id` | UUID | FK | no | FK |
| `sentido` | VARCHAR(12) | — | no | CK |
| `peso` | DECIMAL(4,2) | — | no | — |
| `comentario` | VARCHAR(300) | — | sí | NULL |
| `emitido_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_voto_acuerdo_participante` | UNIQUE | `acuerdo_id`, `participante_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `acuerdo_id` | [[acuerdo]] | 02 | no | [[voto_participante.acuerdo_id → acuerdo]] |
| `participante_id` | [[participante]] | 02 | no | [[voto_participante.participante_id → participante]] |

## Entidades vecinas

[[acuerdo]] · [[participante]]

## Ver también

- Justificación de negocio: [[02_grupos_turnos]]
- Diagramas: `docs/entidades/02_grupos_turnos.puml`
- Índice: [[_Entidades]] · [[Index]]
