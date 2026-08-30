---
tags:
  - entidad
  - modulo/14-publicidad-y-campanas
tabla: anunciante
clase: Anunciante
modulo: "14 — Publicidad y Campañas"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 7
fk_salientes: 2
fk_entrantes: 2
append_only: false
---

# `anunciante`

> Módulo [[14_publicidad_campanas|14 — Publicidad y Campañas]] · clase `Anunciante` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `tipo` | VARCHAR(15) | IDX | no | CK, IDX |
| `organizador_id` | UUID | FK IDX | sí | FK, NULL, IDX, M7 |
| `socio_comercial_id` | UUID | FK IDX | sí | FK, NULL, IDX |
| `razon_social_facturacion` | VARCHAR(150) | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `creado_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_anunciante_tipo_exclusivo` | CHECK | `organizador_id`, `socio_comercial_id`, `tipo` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `organizador_id` | [[organizador]] | ↗ 07 | sí | [[anunciante.organizador_id → organizador]] |
| `socio_comercial_id` | [[socio_comercial]] | 14 | sí | [[anunciante.socio_comercial_id → socio_comercial]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[cuenta_publicitaria]] | `anunciante_id` | 14 | [[cuenta_publicitaria.anunciante_id → anunciante]] |
| [[pieza_creativa]] | `anunciante_id` | 14 | [[pieza_creativa.anunciante_id → anunciante]] |

## Entidades vecinas

[[cuenta_publicitaria]] · [[organizador]] · [[pieza_creativa]] · [[socio_comercial]]

## Notas del modelo

> CHECK ck_anunciante_tipo_exclusivo:
> (tipo = 'ORGANIZADOR' AND organizador_id IS NOT NULL
> AND socio_comercial_id IS NULL) OR
> (tipo = 'SOCIO_COMERCIAL' AND socio_comercial_id IS NOT NULL
> AND organizador_id IS NULL).
> organizador_id -> organizador.id (M7): quien paga,
> jamas quien cobra (RN-18).

## Ver también

- Justificación de negocio: [[14_publicidad_campanas]]
- Diagramas: `docs/entidades/14_publicidad_campanas.puml`
- Índice: [[_Entidades]] · [[Index]]
