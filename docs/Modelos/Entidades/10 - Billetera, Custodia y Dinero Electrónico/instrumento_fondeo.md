---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
tabla: instrumento_fondeo
clase: InstrumentoFondeo
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
clave_primaria: [id]
columnas: 16
fk_salientes: 1
fk_entrantes: 2
append_only: false
---

# `instrumento_fondeo`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `InstrumentoFondeo`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `tipo` | VARCHAR(20) | — | no | CK |
| `entidad_financiera` | VARCHAR(60) | — | no | — |
| `token_proveedor` | VARCHAR(255) | — | sí | NULL |
| `hash_identificador` | VARCHAR(64) | UQ | no | UQ+usuario_id |
| `enmascarado` | VARCHAR(30) | — | no | — |
| `titular_nombre` | VARCHAR(120) | — | no | — |
| `titular_documento` | VARCHAR(30) | — | no | — |
| `titular_coincide` | BOOLEAN | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `es_principal` | BOOLEAN | — | no | — |
| `estado_verificacion` | VARCHAR(15) | — | no | CK |
| `metodo_verificacion` | VARCHAR(20) | — | sí | NULL |
| `verificado_en` | TIMESTAMPTZ | — | sí | NULL |
| `bloqueado_hasta` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_instrumento_sin_pan` | CHECK | `enmascarado`, `hash_identificador` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[instrumento_fondeo.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[orden_recarga]] | `instrumento_fondeo_id` | 10 | [[orden_recarga.instrumento_fondeo_id → instrumento_fondeo]] |
| [[orden_retiro]] | `instrumento_destino_id` | 10 | [[orden_retiro.instrumento_destino_id → instrumento_fondeo]] |

## Entidades vecinas

[[orden_recarga]] · [[orden_retiro]] · [[usuario]]

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
