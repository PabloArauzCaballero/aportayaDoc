---
tags:
  - entidad
  - modulo/06-transparencia-y-reputacion
tabla: bloque_transparencia
clase: BloqueTransparencia
modulo: "06 — Transparencia y Reputación"
clave_primaria: [id]
columnas: 11
fk_salientes: 1
fk_entrantes: 1
append_only: false
---

# `bloque_transparencia`

> Módulo [[06_transparencia_reputacion|06 — Transparencia y Reputación]] · clase `BloqueTransparencia`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `grupo_id` | UUID | FK IDX | no | FK, IDX |
| `numero_bloque` | BIGINT | — | no | — |
| `hash_bloque_anterior` | VARCHAR(64) | — | no | — |
| `raiz_merkle` | VARCHAR(64) | — | no | — |
| `hash_bloque` | VARCHAR(64) | UQ | no | UQ |
| `cantidad_eventos` | INTEGER | — | no | — |
| `periodo_cubierto_desde` | TIMESTAMPTZ | — | no | — |
| `periodo_cubierto_hasta` | TIMESTAMPTZ | — | no | — |
| `sellado_en` | TIMESTAMPTZ | — | no | — |
| `sello_externo` | VARCHAR(255) | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_bloque_genesis` | CHECK | `hash_bloque_anterior`, `numero_bloque` |
| `uq_bloque_grupo_numero` | UNIQUE | `grupo_id`, `numero_bloque` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `grupo_id` | [[grupo]] | ↗ 02 | no | [[bloque_transparencia.grupo_id → grupo]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[registro_sellado]] | `bloque_id` | 06 | [[registro_sellado.bloque_id → bloque_transparencia]] |

## Entidades vecinas

[[grupo]] · [[registro_sellado]]

## Notas del modelo

> Verificacion: hash_bloque =
> H(numero_bloque || hash_bloque_anterior ||
> raiz_merkle || periodo_cubierto_hasta).
> Romper la cadena en un bloque invalida todos
> los siguientes: manipular un pago viejo se nota.

## Ver también

- Justificación de negocio: [[06_transparencia_reputacion]]
- Diagramas: `docs/entidades/06_transparencia_reputacion.puml`
- Índice: [[_Entidades]] · [[Index]]
