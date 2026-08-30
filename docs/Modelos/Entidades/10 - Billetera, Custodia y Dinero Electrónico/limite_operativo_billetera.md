---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
tabla: limite_operativo_billetera
clase: LimiteOperativoBilletera
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
estereotipo: Política configurable
clave_primaria: [id]
columnas: 11
fk_salientes: 0
fk_entrantes: 1
append_only: false
---

# `limite_operativo_billetera`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `LimiteOperativoBilletera` · Política configurable

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `concepto` | VARCHAR(25) | — | no | CK |
| `nivel_debida_diligencia` | VARCHAR(15) | — | no | CK |
| `ventana` | VARCHAR(10) | — | no | CK |
| `monto_maximo` | DECIMAL(16,2) | — | sí | NULL |
| `cantidad_maxima` | INTEGER | — | sí | NULL |
| `moneda` | CHAR(3) | — | no | — |
| `base_normativa` | VARCHAR(120) | — | no | — |
| `vigente_desde` | DATE | — | no | — |
| `vigente_hasta` | DATE | — | sí | NULL |
| `activo` | BOOLEAN | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ex_limite_vigencia` | EXCLUDE | `concepto`, `nivel_debida_diligencia`, `ventana`, `vigente_desde`, `vigente_hasta` |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[consumo_limite]] | `limite_id` | 10 | [[consumo_limite.limite_id → limite_operativo_billetera]] |

## Entidades vecinas

[[consumo_limite]]

## Notas del modelo

> Los limites son datos, no codigo: cambiar el techo de
> retiro mensual de un nivel de debida diligencia es un
> UPDATE con nueva vigencia, no un despliegue.
> base_normativa guarda la referencia al articulo que lo
> obliga, para poder justificar cada techo ante el regulador.

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
