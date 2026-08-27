---
tags:
  - relacion
  - fk
  - modulo/09-auditoria-reportes-y-cumplimiento
origen: indicador_kpi
columna: definicion_indicador_id
destino: definicion_indicador
modulo_origen: "09"
modulo_destino: "09"
cross_modulo: false
opcional: false
cardinalidad: "no declarada en el diagrama"
---

# indicador_kpi.definicion_indicador_id → definicion_indicador

> **[[indicador_kpi]]** `.definicion_indicador_id` → **[[definicion_indicador]]**

| | |
| --- | --- |
| Entidad origen | [[indicador_kpi]] (módulo 09) |
| Entidad destino | [[definicion_indicador]] (módulo 09) |
| Columna | `definicion_indicador_id` — UUID |
| Cardinalidad | no declarada en el diagrama |
| Obligatoria | sí |
| Uno a uno | no |
| Cruza módulos | no |

## Ver también

- [[09_auditoria_reportes]] — justificación de negocio del origen
- [[09_auditoria_reportes]] — justificación de negocio del destino
- [[_Relaciones]] · [[Index]]
