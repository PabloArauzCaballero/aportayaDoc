---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
tabla: conciliacion_custodia
clase: ConciliacionCustodia
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 13
fk_salientes: 3
fk_entrantes: 1
append_only: false
---

# `conciliacion_custodia`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `ConciliacionCustodia` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `cuenta_custodia_id` | UUID | FK IDX | no | FK, IDX |
| `cierre_diario_id` | UUID | FK | sí | FK, NULL, M3 |
| `ejecutada_por` | UUID | FK | sí | FK, NULL |
| `fecha` | DATE | — | no | — |
| `saldo_dinero_electronico` | DECIMAL(18,2) | — | no | — |
| `saldo_custodia` | DECIMAL(18,2) | — | no | — |
| `saldo_en_transito` | DECIMAL(18,2) | — | no | — |
| `diferencia` | DECIMAL(18,2) | — | no | GENERATED |
| `ratio_cobertura` | DECIMAL(9,6) | — | no | GENERATED |
| `cumple_encaje` | BOOLEAN | IDX | no | IDX |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `ejecutada_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_conciliacion_encaje` | CHECK | `cumple_encaje`, `ratio_cobertura` |
| `uq_conciliacion_cuenta_fecha` | UNIQUE | `cuenta_custodia_id`, `fecha` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cierre_diario_id` | [[cierre_diario]] | ↗ 03 | sí | [[conciliacion_custodia.cierre_diario_id → cierre_diario]] |
| `cuenta_custodia_id` | [[cuenta_custodia]] | 10 | no | [[conciliacion_custodia.cuenta_custodia_id → cuenta_custodia]] |
| `ejecutada_por` | [[usuario]] | ↗ 01 | sí | [[conciliacion_custodia.ejecutada_por → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[descuadre_custodia]] | `conciliacion_custodia_id` | 10 | [[descuadre_custodia.conciliacion_custodia_id → conciliacion_custodia]] |

## Entidades vecinas

[[cierre_diario]] · [[cuenta_custodia]] · [[descuadre_custodia]] · [[usuario]]

## Notas del modelo

> **Encaje 100%**
> ratio_cobertura = saldo_custodia / saldo_dinero_electronico.
> cumple_encaje = ratio_cobertura >= 1.0000.
> El dinero de los usuarios NO es patrimonio de la
> plataforma: es un pasivo exigible respaldado peso por peso.
> El cierre_diario de M3 no puede marcarse cuadrado si existe
> una conciliacion_custodia DESCUADRADA de esa fecha.

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
