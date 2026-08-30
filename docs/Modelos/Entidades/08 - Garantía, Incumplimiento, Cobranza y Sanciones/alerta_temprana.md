---
tags:
  - entidad
  - modulo/08-garantia-incumplimiento-cobranza-y-sanciones
tabla: alerta_temprana
clase: AlertaTemprana
modulo: "08 — Garantía, Incumplimiento, Cobranza y Sanciones"
clave_primaria: [id]
columnas: 8
fk_salientes: 2
fk_entrantes: 0
append_only: false
---

# `alerta_temprana`

> Módulo [[08_garantia_incumplimiento|08 — Garantía, Incumplimiento, Cobranza y Sanciones]] · clase `AlertaTemprana`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `grupo_id` | UUID | FK | sí | FK, NULL |
| `codigo` | VARCHAR(40) | — | no | CK |
| `descripcion` | VARCHAR(300) | — | no | — |
| `severidad` | VARCHAR(10) | — | no | CK |
| `estado` | VARCHAR(15) | — | no | CK |
| `generada_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_alerta_temprana_abierta` | UNIQUE parcial | expresión |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `grupo_id` | [[grupo]] | ↗ 02 | sí | [[alerta_temprana.grupo_id → grupo]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[alerta_temprana.usuario_id → usuario]] |

## Entidades vecinas

[[grupo]] · [[usuario]]

## Ver también

- Justificación de negocio: [[08_garantia_incumplimiento]]
- Diagramas: `docs/entidades/08_garantia_incumplimiento.puml`
- Índice: [[_Entidades]] · [[Index]]
