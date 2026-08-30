---
tags:
  - entidad
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
tabla: requerimiento_autoridad
clase: RequerimientoAutoridad
modulo: "12 — Cumplimiento Regulatorio y Consumidor Financiero"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 14
fk_salientes: 3
fk_entrantes: 0
append_only: false
---

# `requerimiento_autoridad`

> Módulo [[12_cumplimiento_asfi|12 — Cumplimiento Regulatorio y Consumidor Financiero]] · clase `RequerimientoAutoridad` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_afectado_id` | UUID | FK | sí | FK, NULL, M1 |
| `bloqueo_saldo_id` | UUID | FK | sí | FK, NULL, M10 |
| `respondido_por` | UUID | FK | sí | FK, NULL |
| `autoridad` | VARCHAR(15) | IDX | no | CK, IDX |
| `numero_oficio` | VARCHAR(60) | UQ | no | UQ |
| `fecha_recepcion` | TIMESTAMPTZ | — | no | — |
| `plazo_respuesta` | TIMESTAMPTZ | IDX | no | IDX |
| `alcance` | VARCHAR(300) | — | no | — |
| `documento_url` | VARCHAR(255) | — | no | — |
| `hash_documento` | VARCHAR(64) | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `respuesta_url` | VARCHAR(255) | — | sí | NULL |
| `respondido_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ix_requerimiento_vencidos` | INDEX parcial | `plazo_respuesta` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `bloqueo_saldo_id` | [[bloqueo_saldo]] | ↗ 10 | sí | [[requerimiento_autoridad.bloqueo_saldo_id → bloqueo_saldo]] |
| `respondido_por` | [[usuario]] | ↗ 01 | sí | [[requerimiento_autoridad.respondido_por → usuario]] |
| `usuario_afectado_id` | [[usuario]] | ↗ 01 | sí | [[requerimiento_autoridad.usuario_afectado_id → usuario]] |

## Entidades vecinas

[[bloqueo_saldo]] · [[usuario]]

## Notas del modelo

> Los oficios de autoridad se guardan con su hash y su
> plazo. Un requerimiento que ordena inmovilizar fondos
> genera bloqueo_saldo en M10; uno que pide informacion
> genera un registro de acceso a datos en M9. Nunca se
> ejecuta una orden sin dejar el documento que la
> respalda.

## Ver también

- Justificación de negocio: [[12_cumplimiento_asfi]]
- Diagramas: `docs/entidades/12_cumplimiento_asfi.puml`
- Índice: [[_Entidades]] · [[Index]]
