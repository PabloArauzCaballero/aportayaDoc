---
tags:
  - entidad
  - modulo/07-organizador-y-automatizacion
tabla: solicitud_organizador
clase: SolicitudOrganizador
modulo: "07 — Organizador y Automatización"
clave_primaria: [id]
columnas: 11
fk_salientes: 3
fk_entrantes: 0
append_only: false
---

# `solicitud_organizador`

> Módulo [[07_organizador_automatizacion|07 — Organizador y Automatización]] · clase `SolicitudOrganizador`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `motivacion` | TEXT | — | no | — |
| `experiencia_declarada` | TEXT | — | no | — |
| `kyc_reforzado_id` | UUID | FK | sí | FK, NULL, M1 |
| `puntaje_reputacion_al_solicitar` | DECIMAL(6,2) | — | no | — |
| `estado` | VARCHAR(15) | — | no | CK |
| `revisada_por` | UUID | FK | sí | FK, NULL |
| `motivo_rechazo` | VARCHAR(300) | — | sí | NULL |
| `fecha_solicitud` | TIMESTAMPTZ | — | no | — |
| `fecha_resolucion` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_solicitud_org_rechazo_motivado` | CHECK | `estado`, `motivo_rechazo` |
| `ck_solicitud_org_resuelta` | CHECK | `estado`, `fecha_resolucion` |
| `uq_solicitud_organizador_pendiente` | UNIQUE parcial | `usuario_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `kyc_reforzado_id` | [[verificacion_kyc]] | ↗ 01 | sí | [[solicitud_organizador.kyc_reforzado_id → verificacion_kyc]] |
| `revisada_por` | [[usuario]] | ↗ 01 | sí | [[solicitud_organizador.revisada_por → usuario]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[solicitud_organizador.usuario_id → usuario]] |

## Entidades vecinas

[[usuario]] · [[verificacion_kyc]]

## Ver también

- Justificación de negocio: [[07_organizador_automatizacion]]
- Diagramas: `docs/entidades/07_organizador_automatizacion.puml`
- Índice: [[_Entidades]] · [[Index]]
