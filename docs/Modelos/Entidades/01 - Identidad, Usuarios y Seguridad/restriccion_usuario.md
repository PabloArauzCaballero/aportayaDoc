---
tags:
  - entidad
  - modulo/01-identidad-usuarios-y-seguridad
tabla: restriccion_usuario
clase: RestriccionUsuario
modulo: "01 — Identidad, Usuarios y Seguridad"
clave_primaria: [id]
columnas: 10
fk_salientes: 2
fk_entrantes: 0
append_only: false
---

# `restriccion_usuario`

> Módulo [[01_identidad_usuarios|01 — Identidad, Usuarios y Seguridad]] · clase `RestriccionUsuario`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `tipo` | VARCHAR(30) | — | no | CK |
| `origen` | VARCHAR(20) | — | no | CK |
| `referencia_origen_id` | UUID | — | sí | NULL |
| `valor_limite` | DECIMAL(14,2) | — | sí | NULL |
| `vigente_desde` | TIMESTAMPTZ | — | no | — |
| `vigente_hasta` | TIMESTAMPTZ | — | sí | NULL |
| `levantada_por` | UUID | FK | sí | FK, NULL |
| `motivo_levantamiento` | VARCHAR(160) | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_restriccion_levantamiento` | CHECK | `levantada_por`, `motivo_levantamiento`, `vigente_hasta` |
| `ck_restriccion_vigencia` | CHECK | `vigente_desde`, `vigente_hasta` |
| `uq_restriccion_usuario_vigente` | UNIQUE parcial | `usuario_id`, `tipo` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `levantada_por` | [[usuario]] | 01 | sí | [[restriccion_usuario.levantada_por → usuario]] |
| `usuario_id` | [[usuario]] | 01 | no | [[restriccion_usuario.usuario_id → usuario]] |

## Entidades vecinas

[[usuario]]

## Notas del modelo

> referencia_origen_id apunta a
> registro_incumplimiento.id (modulo 8)
> cuando origen = 'INCUMPLIMIENTO'.

## Ver también

- Justificación de negocio: [[01_identidad_usuarios]]
- Diagramas: `docs/entidades/01_identidad_usuarios.puml`
- Índice: [[_Entidades]] · [[Index]]
