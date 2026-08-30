---
tags:
  - entidad
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
tabla: concepto_tarifa
clase: ConceptoTarifa
modulo: "11 — Tarifas, Comisiones, Impuestos y Facturación"
clave_primaria: [id]
columnas: 22
fk_salientes: 4
fk_entrantes: 4
append_only: false
---

# `concepto_tarifa`

> Módulo [[11_tarifas_comisiones|11 — Tarifas, Comisiones, Impuestos y Facturación]] · clase `ConceptoTarifa`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `tarifario_id` | UUID | FK IDX | no | FK, IDX |
| `hecho_generador_id` | UUID | FK IDX | no | FK, IDX |
| `politica_redondeo_id` | UUID | FK | sí | FK, NULL |
| `cuenta_ingreso_id` | UUID | FK | sí | FK, NULL, M3 |
| `codigo` | VARCHAR(40) | UQ | no | UQ+tarifario_id |
| `nombre_comercial` | VARCHAR(80) | — | no | — |
| `descripcion_usuario` | VARCHAR(300) | — | no | — |
| `metodo_calculo` | VARCHAR(25) | — | no | CK |
| `base_calculo` | VARCHAR(35) | — | no | CK |
| `valor_porcentual` | DECIMAL(7,4) | — | sí | NULL |
| `valor_fijo` | DECIMAL(12,2) | — | sí | NULL |
| `monto_minimo` | DECIMAL(12,2) | — | sí | NULL |
| `monto_maximo` | DECIMAL(12,2) | — | sí | NULL |
| `sujeto_obligado` | VARCHAR(35) | — | no | CK |
| `forma_cobro` | VARCHAR(30) | — | no | CK |
| `momento_cobro` | VARCHAR(25) | — | no | CK |
| `gravado_iva` | BOOLEAN | — | no | — |
| `gravado_it` | BOOLEAN | — | no | — |
| `precio_incluye_impuesto` | BOOLEAN | — | no | — |
| `orden_aplicacion` | SMALLINT | — | no | — |
| `activo` | BOOLEAN | IDX | no | IDX |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_concepto_metodo` | CHECK | `metodo_calculo`, `valor_fijo`, `valor_porcentual` |
| `ck_concepto_piso_techo` | CHECK | `monto_maximo`, `monto_minimo` |
| `ck_concepto_precio_final` | CHECK | `gravado_iva`, `precio_incluye_impuesto`, `sujeto_obligado` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_ingreso_id` | [[cuenta_contable]] | ↗ 03 | sí | [[concepto_tarifa.cuenta_ingreso_id → cuenta_contable]] |
| `hecho_generador_id` | [[catalogo_hecho_generador]] | 11 | no | [[concepto_tarifa.hecho_generador_id → catalogo_hecho_generador]] |
| `politica_redondeo_id` | [[politica_redondeo]] | 11 | sí | [[concepto_tarifa.politica_redondeo_id → politica_redondeo]] |
| `tarifario_id` | [[tarifario]] | 11 | no | [[concepto_tarifa.tarifario_id → tarifario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[cotizacion_comision]] | `concepto_tarifa_id` | 11 | [[cotizacion_comision.concepto_tarifa_id → concepto_tarifa]] |
| [[devengo_comision]] | `concepto_tarifa_id` | 11 | [[devengo_comision.concepto_tarifa_id → concepto_tarifa]] |
| [[exencion_comision]] | `concepto_tarifa_id` | 11 | [[exencion_comision.concepto_tarifa_id → concepto_tarifa]] |
| [[regla_tarifa]] | `concepto_tarifa_id` | 11 | [[regla_tarifa.concepto_tarifa_id → concepto_tarifa]] |

## Entidades vecinas

[[catalogo_hecho_generador]] · [[cotizacion_comision]] · [[cuenta_contable]] · [[devengo_comision]] · [[exencion_comision]] · [[politica_redondeo]] · [[regla_tarifa]] · [[tarifario]]

## Notas del modelo

> **La politica de cobro completa, en columnas**
> hecho_generador_id -> sobre que evento se cobra
> base_calculo       -> sobre que monto
> metodo_calculo     -> como se calcula
> sujeto_obligado    -> quien lo paga
> forma_cobro        -> por que via se cobra
> momento_cobro      -> cuando
> Cambiar cualquiera de estos seis es un UPDATE
> sobre la version nueva del tarifario. El motor
> de cobro no tiene ninguna de estas decisiones
> escrita en el codigo.
> CHECK: metodo_calculo='PORCENTUAL' exige
> valor_porcentual NOT NULL; 'FIJO' exige
> valor_fijo NOT NULL; 'ESCALONADO_*' exige al
> menos una regla_tarifa.

## Ver también

- Justificación de negocio: [[11_tarifas_comisiones]]
- Diagramas: `docs/entidades/11_tarifas_comisiones.puml`
- Índice: [[_Entidades]] · [[Index]]
