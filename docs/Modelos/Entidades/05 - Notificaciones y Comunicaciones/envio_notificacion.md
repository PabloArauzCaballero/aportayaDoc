---
tags:
  - entidad
  - modulo/05-notificaciones-y-comunicaciones
tabla: envio_notificacion
clase: EnvioNotificacion
modulo: "05 — Notificaciones y Comunicaciones"
clave_primaria: [id]
columnas: 22
fk_salientes: 4
fk_entrantes: 3
append_only: false
---

# `envio_notificacion`

> Módulo [[05_notificaciones|05 — Notificaciones y Comunicaciones]] · clase `EnvioNotificacion`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `notificacion_id` | UUID | FK IDX | no | FK, IDX |
| `proveedor_id` | UUID | FK | no | FK |
| `version_plantilla_id` | UUID | FK | no | FK |
| `canal_vinculado_id` | UUID | FK | sí | FK, NULL |
| `canal` | VARCHAR(15) | — | no | CK |
| `destinatario` | VARCHAR(150) | — | no | — |
| `clave_idempotencia` | VARCHAR(120) | — | no | — |
| `encolado_en` | TIMESTAMPTZ | — | no | — |
| `contenido_enviado` | TEXT | — | no | — |
| `estado` | VARCHAR(25) | IDX | no | CK, IDX |
| `id_mensaje_proveedor` | VARCHAR(120) | UQ | sí | UQ, NULL |
| `orden` | SMALLINT | — | no | — |
| `intentos` | SMALLINT | — | no | — |
| `max_intentos` | SMALLINT | — | no | — |
| `costo` | DECIMAL(10,4) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `codigo_error` | VARCHAR(40) | — | sí | NULL |
| `enviado_en` | TIMESTAMPTZ | — | sí | NULL |
| `entregado_en` | TIMESTAMPTZ | — | sí | NULL |
| `leido_en` | TIMESTAMPTZ | — | sí | NULL |
| `proximo_reintento_en` | TIMESTAMPTZ | IDX | sí | NULL, IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_envio_idempotencia` | UNIQUE | `notificacion_id`, `clave_idempotencia` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `canal_vinculado_id` | [[canal_vinculado]] | 05 | sí | [[envio_notificacion.canal_vinculado_id → canal_vinculado]] |
| `notificacion_id` | [[notificacion]] | 05 | no | [[envio_notificacion.notificacion_id → notificacion]] |
| `proveedor_id` | [[proveedor_mensajeria]] | 05 | no | [[envio_notificacion.proveedor_id → proveedor_mensajeria]] |
| `version_plantilla_id` | [[version_plantilla]] | 05 | no | [[envio_notificacion.version_plantilla_id → version_plantilla]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[cola_envio]] | `envio_id` | 05 | [[cola_envio.envio_id → envio_notificacion]] |
| [[cola_muerta]] | `envio_id` | 05 | [[cola_muerta.envio_id → envio_notificacion]] |
| [[evento_entrega_mensaje]] | `envio_id` | 05 | [[evento_entrega_mensaje.envio_id → envio_notificacion]] |

## Entidades vecinas

[[canal_vinculado]] · [[cola_envio]] · [[cola_muerta]] · [[evento_entrega_mensaje]] · [[notificacion]] · [[proveedor_mensajeria]] · [[version_plantilla]]

## Notas del modelo

> id_mensaje_proveedor es UNIQUE: es la llave
> con la que llegan los webhooks de estado.
> evento_entrega_mensaje.clave_idempotencia
> evita aplicar dos veces el mismo callback
> (los proveedores reintentan).

## Ver también

- Justificación de negocio: [[05_notificaciones]]
- Diagramas: `docs/entidades/05_notificaciones.puml`
- Índice: [[_Entidades]] · [[Index]]
