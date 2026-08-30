---
tags:
  - entidad
  - modulo/01-identidad-usuarios-y-seguridad
tabla: token_verificacion
clase: TokenVerificacion
modulo: "01 — Identidad, Usuarios y Seguridad"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 31
fk_salientes: 4
fk_entrantes: 10
append_only: false
---

# `token_verificacion`

> Módulo [[01_identidad_usuarios|01 — Identidad, Usuarios y Seguridad]] · clase `TokenVerificacion` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | sí | FK, NULL, IDX |
| `politica_id` | UUID | FK | no | FK |
| `dispositivo_id` | UUID | FK | sí | FK, NULL |
| `tipo_token` | VARCHAR(20) | — | no | CK: OTP|ENLACE|REFRESCO |
| `proposito` | VARCHAR(35) | IDX | no | IDX |
| `hash_token` | VARCHAR(128) | UQ | no | UQ |
| `algoritmo_hash` | VARCHAR(20) | — | no | — |
| `canal_entrega` | VARCHAR(20) | — | no | — |
| `destino_enmascarado` | VARCHAR(40) | — | no | — |
| `estado` | VARCHAR(25) | IDX | no | IDX |
| `emitido_en` | TIMESTAMPTZ | — | no | — |
| `expira_en` | TIMESTAMPTZ | IDX | no | IDX |
| `enviado_en` | TIMESTAMPTZ | — | sí | NULL |
| `consumido_en` | TIMESTAMPTZ | — | sí | NULL |
| `invalidado_en` | TIMESTAMPTZ | — | sí | NULL |
| `motivo_invalidacion` | VARCHAR(120) | — | sí | NULL |
| `intentos_fallidos` | SMALLINT | — | no | — |
| `max_intentos` | SMALLINT | — | no | — |
| `reenvios` | SMALLINT | — | no | — |
| `ip_origen` | INET | — | no | — |
| `agente_usuario` | VARCHAR(255) | — | no | — |
| `correlation_id` | UUID | IDX | no | IDX |
| `clave_idempotencia` | VARCHAR(80) | — | no | — |
| `longitud` | SMALLINT | — | sí | NULL, subtipo OTP |
| `url_destino` | VARCHAR(255) | — | sí | NULL, subtipo ENLACE |
| `firma_hmac` | VARCHAR(128) | — | sí | NULL, subtipo ENLACE |
| `uso_unico` | BOOLEAN | — | sí | NULL, subtipo ENLACE |
| `clicks` | SMALLINT | — | sí | NULL, subtipo ENLACE |
| `familia_id` | UUID | — | sí | NULL, subtipo REFRESCO |
| `rotado_de_id` | UUID | FK | sí | FK, NULL, subtipo REFRESCO |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_token_refresco_familia` | CHECK | `familia_id`, `tipo_token` |
| `uq_token_refresco_vivo` | UNIQUE parcial | `familia_id` |
| `uq_token_verificacion_idem` | UNIQUE | expresión |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `dispositivo_id` | [[dispositivo]] | 01 | sí | [[token_verificacion.dispositivo_id → dispositivo]] |
| `politica_id` | [[politica_sancion]] | ↗ 08 | no | [[token_verificacion.politica_id → politica_sancion]] |
| `rotado_de_id` | [[token_verificacion]] | 01 | sí | [[token_verificacion.rotado_de_id → token_verificacion]] |
| `usuario_id` | [[usuario]] | 01 | sí | [[token_verificacion.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[aceptacion_contrato]] | `token_firma_id` | ↗ 12 | [[aceptacion_contrato.token_firma_id → token_verificacion]] |
| [[aceptacion_reglamento]] | `token_firma_id` | ↗ 02 | [[aceptacion_reglamento.token_firma_id → token_verificacion]] |
| [[aval_participante]] | `token_aceptacion_id` | ↗ 08 | [[aval_participante.token_aceptacion_id → token_verificacion]] |
| [[confirmacion_recepcion]] | `token_confirmacion_id` | ↗ 04 | [[confirmacion_recepcion.token_confirmacion_id → token_verificacion]] |
| [[contrato_organizador]] | `token_firma_id` | ↗ 07 | [[contrato_organizador.token_firma_id → token_verificacion]] |
| [[enlace_pago_notificado]] | `token_id` | ↗ 05 | [[enlace_pago_notificado.token_id → token_verificacion]] |
| [[enlace_pago_rapido]] | `token_id` | ↗ 03 | [[enlace_pago_rapido.token_id → token_verificacion]] |
| [[intento_validacion_token]] | `token_id` | 01 | [[intento_validacion_token.token_id → token_verificacion]] |
| [[invitacion]] | `token_id` | ↗ 02 | [[invitacion.token_id → token_verificacion]] |
| [[token_verificacion]] | `rotado_de_id` | 01 | [[token_verificacion.rotado_de_id → token_verificacion]] |

## Entidades vecinas

[[aceptacion_contrato]] · [[aceptacion_reglamento]] · [[aval_participante]] · [[confirmacion_recepcion]] · [[contrato_organizador]] · [[dispositivo]] · [[enlace_pago_notificado]] · [[enlace_pago_rapido]] · [[intento_validacion_token]] · [[invitacion]] · [[politica_sancion]] · [[token_verificacion]] · [[usuario]]

## Notas del modelo

> **Tabla unica con herencia por tabla (STI)**
> tipo_token discrimina OTP / ENLACE / REFRESCO.
> Indice parcial recomendado:
> UNIQUE (usuario_id, proposito)
> WHERE estado IN ('EMITIDO','ENVIADO')
> -> garantiza un solo token vigente por proposito.
> Indice de limpieza: (estado, expira_en).
> hash_token es UNIQUE: colision = intento de replay.

## Ver también

- Justificación de negocio: [[01_identidad_usuarios]]
- Diagramas: `docs/entidades/01_identidad_usuarios.puml`
- Índice: [[_Entidades]] · [[Index]]
