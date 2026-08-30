---
tags:
  - entidad
  - modulo/08-garantia-incumplimiento-cobranza-y-sanciones
tabla: ejecucion_aval
clase: EjecucionAval
modulo: "08 — Garantía, Incumplimiento, Cobranza y Sanciones"
clave_primaria: [id]
columnas: 10
fk_salientes: 4
fk_entrantes: 0
append_only: false
---

# `ejecucion_aval`

> Módulo [[08_garantia_incumplimiento|08 — Garantía, Incumplimiento, Cobranza y Sanciones]] · clase `EjecucionAval`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `aval_id` | UUID | FK IDX | no | FK, IDX |
| `registro_id` | UUID | FK IDX | no | FK, IDX |
| `deuda_id` | UUID | FK | no | FK |
| `pago_id` | UUID | FK | sí | FK, NULL, M3 |
| `monto_ejecutado` | DECIMAL(14,2) | — | no | — |
| `estado` | VARCHAR(15) | — | no | CK |
| `notificada_en` | TIMESTAMPTZ | — | no | — |
| `plazo_respuesta` | TIMESTAMPTZ | — | no | — |
| `genera_deuda_del_avalista` | BOOLEAN | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_ejecucion_aval_monto` | CHECK | `monto_ejecutado` |
| `ck_ejecucion_aval_plazo` | CHECK | `notificada_en`, `plazo_respuesta` |
| `uq_ejecucion_aval_registro` | UNIQUE | `aval_id`, `registro_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `aval_id` | [[aval_participante]] | 08 | no | [[ejecucion_aval.aval_id → aval_participante]] |
| `deuda_id` | [[deuda_participante]] | 08 | no | [[ejecucion_aval.deuda_id → deuda_participante]] |
| `pago_id` | [[pago]] | ↗ 03 | sí | [[ejecucion_aval.pago_id → pago]] |
| `registro_id` | [[registro_incumplimiento]] | 08 | no | [[ejecucion_aval.registro_id → registro_incumplimiento]] |

## Entidades vecinas

[[aval_participante]] · [[deuda_participante]] · [[pago]] · [[registro_incumplimiento]]

## Ver también

- Justificación de negocio: [[08_garantia_incumplimiento]]
- Diagramas: `docs/entidades/08_garantia_incumplimiento.puml`
- Índice: [[_Entidades]] · [[Index]]
