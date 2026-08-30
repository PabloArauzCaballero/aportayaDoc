---
tags:
  - entidad
  - modulo/05-notificaciones-y-comunicaciones
tabla: evento_entrega_mensaje
clase: EventoEntregaMensaje
modulo: "05 — Notificaciones y Comunicaciones"
clave_primaria: [id]
columnas: 8
fk_salientes: 1
fk_entrantes: 0
append_only: false
---

# `evento_entrega_mensaje`

> Módulo [[05_notificaciones|05 — Notificaciones y Comunicaciones]] · clase `EventoEntregaMensaje`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `envio_id` | UUID | FK IDX | no | FK, IDX |
| `tipo_evento` | VARCHAR(20) | — | no | CK |
| `fecha_hora_proveedor` | TIMESTAMPTZ | — | no | — |
| `recibido_en` | TIMESTAMPTZ | — | no | — |
| `payload_crudo` | JSONB | — | no | — |
| `codigo_error` | VARCHAR(40) | — | sí | NULL |
| `clave_idempotencia` | VARCHAR(120) | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_evento_entrega_idempotencia` | UNIQUE | `envio_id`, `clave_idempotencia` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `envio_id` | [[envio_notificacion]] | 05 | no | [[evento_entrega_mensaje.envio_id → envio_notificacion]] |

## Entidades vecinas

[[envio_notificacion]]

## Ver también

- Justificación de negocio: [[05_notificaciones]]
- Diagramas: `docs/entidades/05_notificaciones.puml`
- Índice: [[_Entidades]] · [[Index]]
