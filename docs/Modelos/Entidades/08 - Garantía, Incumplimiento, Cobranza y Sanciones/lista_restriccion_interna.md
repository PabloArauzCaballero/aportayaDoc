---
tags:
  - entidad
  - modulo/08-garantia-incumplimiento-cobranza-y-sanciones
tabla: lista_restriccion_interna
clase: ListaRestriccionInterna
modulo: "08 — Garantía, Incumplimiento, Cobranza y Sanciones"
clave_primaria: [id]
columnas: 11
fk_salientes: 3
fk_entrantes: 0
append_only: false
---

# `lista_restriccion_interna`

> Módulo [[08_garantia_incumplimiento|08 — Garantía, Incumplimiento, Cobranza y Sanciones]] · clase `ListaRestriccionInterna`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `registro_origen_id` | UUID | FK | sí | FK, NULL |
| `motivo` | VARCHAR(300) | — | no | — |
| `nivel_restriccion` | VARCHAR(15) | — | no | CK |
| `monto_adeudado` | DECIMAL(14,2) | — | no | — |
| `incluido_en` | TIMESTAMPTZ | — | no | — |
| `vigente_hasta` | TIMESTAMPTZ | — | sí | NULL |
| `retirado_en` | TIMESTAMPTZ | — | sí | NULL |
| `retirado_por` | UUID | FK | sí | FK, NULL |
| `motivo_retiro` | VARCHAR(300) | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_lista_retiro_motivado` | CHECK | `motivo_retiro`, `retirado_en`, `retirado_por` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `registro_origen_id` | [[registro_incumplimiento]] | 08 | sí | [[lista_restriccion_interna.registro_origen_id → registro_incumplimiento]] |
| `retirado_por` | [[usuario]] | ↗ 01 | sí | [[lista_restriccion_interna.retirado_por → usuario]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[lista_restriccion_interna.usuario_id → usuario]] |

## Entidades vecinas

[[registro_incumplimiento]] · [[usuario]]

## Ver también

- Justificación de negocio: [[08_garantia_incumplimiento]]
- Diagramas: `docs/entidades/08_garantia_incumplimiento.puml`
- Índice: [[_Entidades]] · [[Index]]
