---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
tabla: consumo_limite
clase: ConsumoLimite
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
estereotipo: Objeto de valor
clave_primaria: [id]
columnas: 8
fk_salientes: 2
fk_entrantes: 0
append_only: false
---

# `consumo_limite`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `ConsumoLimite` · Objeto de valor

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `cuenta_billetera_id` | UUID | FK IDX | no | FK, IDX |
| `limite_id` | UUID | FK IDX | no | FK, IDX |
| `ventana_inicio` | TIMESTAMPTZ | — | no | — |
| `ventana_fin` | TIMESTAMPTZ | — | no | — |
| `monto_acumulado` | DECIMAL(16,2) | — | no | — |
| `cantidad_acumulada` | INTEGER | — | no | — |
| `actualizado_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_consumo_ventana` | UNIQUE | `cuenta_billetera_id`, `limite_id`, `ventana_inicio` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_billetera_id` | [[cuenta_billetera]] | 10 | no | [[consumo_limite.cuenta_billetera_id → cuenta_billetera]] |
| `limite_id` | [[limite_operativo_billetera]] | 10 | no | [[consumo_limite.limite_id → limite_operativo_billetera]] |

## Entidades vecinas

[[cuenta_billetera]] · [[limite_operativo_billetera]]

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
