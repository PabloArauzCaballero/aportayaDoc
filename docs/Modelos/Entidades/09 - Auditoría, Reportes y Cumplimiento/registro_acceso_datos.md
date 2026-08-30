---
tags:
  - entidad
  - modulo/09-auditoria-reportes-y-cumplimiento
  - append-only
tabla: registro_acceso_datos
clase: RegistroAccesoDatos
modulo: "09 — Auditoría, Reportes y Cumplimiento"
clave_primaria: [id]
columnas: 10
fk_salientes: 2
fk_entrantes: 0
append_only: true
---

# `registro_acceso_datos`

> Módulo [[09_auditoria_reportes|09 — Auditoría, Reportes y Cumplimiento]] · clase `RegistroAccesoDatos` · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_consultor_id` | UUID | FK IDX | no | FK, IDX |
| `usuario_afectado_id` | UUID | FK IDX | no | FK, IDX |
| `tipo_dato` | VARCHAR(30) | — | no | CK |
| `operacion` | VARCHAR(15) | — | no | CK |
| `justificacion` | VARCHAR(300) | — | no | — |
| `ticket_soporte_id` | VARCHAR(30) | — | sí | NULL |
| `cantidad_registros` | INTEGER | — | no | — |
| `ip_origen` | INET | — | no | — |
| `fecha_hora` | TIMESTAMPTZ | IDX | no | IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_acceso_justificacion` | CHECK | `justificacion` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `usuario_afectado_id` | [[usuario]] | ↗ 01 | no | [[registro_acceso_datos.usuario_afectado_id → usuario]] |
| `usuario_consultor_id` | [[usuario]] | ↗ 01 | no | [[registro_acceso_datos.usuario_consultor_id → usuario]] |

## Entidades vecinas

[[usuario]]

## Notas del modelo

> Auditoria de LECTURA, no de escritura.
> Responde "quien vio la cedula o la cuenta
> bancaria de este usuario y con que
> justificacion". Consultas masivas por un
> mismo consultor disparan alerta_cumplimiento
> (categoria RED_SOSPECHOSA).

## Ver también

- Justificación de negocio: [[09_auditoria_reportes]]
- Diagramas: `docs/entidades/09_auditoria_reportes.puml`
- Índice: [[_Entidades]] · [[Index]]
