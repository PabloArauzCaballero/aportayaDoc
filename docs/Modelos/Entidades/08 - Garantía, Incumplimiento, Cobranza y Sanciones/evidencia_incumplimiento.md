---
tags:
  - entidad
  - modulo/08-garantia-incumplimiento-cobranza-y-sanciones
tabla: evidencia_incumplimiento
clase: EvidenciaIncumplimiento
modulo: "08 — Garantía, Incumplimiento, Cobranza y Sanciones"
clave_primaria: [id]
columnas: 10
fk_salientes: 2
fk_entrantes: 0
append_only: false
---

# `evidencia_incumplimiento`

> Módulo [[08_garantia_incumplimiento|08 — Garantía, Incumplimiento, Cobranza y Sanciones]] · clase `EvidenciaIncumplimiento`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `registro_id` | UUID | FK IDX | no | FK, IDX |
| `tipo` | VARCHAR(25) | — | no | CK |
| `descripcion` | VARCHAR(300) | — | no | — |
| `url_archivo` | VARCHAR(255) | — | sí | NULL |
| `hash_archivo` | VARCHAR(64) | — | sí | NULL |
| `contenido_estructurado` | JSONB | — | sí | NULL |
| `aportada_por` | UUID | FK | sí | FK, NULL |
| `fecha_hora` | TIMESTAMPTZ | — | no | — |
| `es_inmutable` | BOOLEAN | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_evidencia_con_respaldo` | CHECK | `hash_archivo`, `url_archivo` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `aportada_por` | [[usuario]] | ↗ 01 | sí | [[evidencia_incumplimiento.aportada_por → usuario]] |
| `registro_id` | [[registro_incumplimiento]] | 08 | no | [[evidencia_incumplimiento.registro_id → registro_incumplimiento]] |

## Entidades vecinas

[[registro_incumplimiento]] · [[usuario]]

## Ver también

- Justificación de negocio: [[08_garantia_incumplimiento]]
- Diagramas: `docs/entidades/08_garantia_incumplimiento.puml`
- Índice: [[_Entidades]] · [[Index]]
