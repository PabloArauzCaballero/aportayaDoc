---
tags:
  - relacion
  - fk
  - modulo/02-grupos-cupos-turnos-y-gobernanza
origen: turno
columna: periodo_id
destino: periodo
modulo_origen: "02"
modulo_destino: "02"
cross_modulo: false
opcional: false
cardinalidad: "uno a muchos (0..N)"
---

# turno.periodo_id → periodo

> **[[turno]]** `.periodo_id` → **[[periodo]]**

| | |
| --- | --- |
| Entidad origen | [[turno]] (módulo 02) |
| Entidad destino | [[periodo]] (módulo 02) |
| Columna | `periodo_id` — UUID |
| Cardinalidad | uno a muchos (0..N) |
| Obligatoria | sí |
| Uno a uno | sí (columna UNIQUE) |
| Cruza módulos | no |
| Semántica | "define" |

## Ver también

- [[02_grupos_turnos]] — justificación de negocio del origen
- [[02_grupos_turnos]] — justificación de negocio del destino
- [[_Relaciones]] · [[Index]]
