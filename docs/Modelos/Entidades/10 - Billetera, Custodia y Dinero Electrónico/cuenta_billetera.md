---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
tabla: cuenta_billetera
clase: CuentaBilletera
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 17
fk_salientes: 4
fk_entrantes: 14
append_only: false
---

# `cuenta_billetera`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `CuentaBilletera` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `numero_cuenta` | VARCHAR(20) | UQ | no | UQ |
| `tipo` | VARCHAR(35) | IDX | no | CK, IDX |
| `usuario_id` | UUID | FK IDX | sí | FK, NULL, IDX |
| `grupo_id` | UUID | FK IDX | sí | FK, NULL, IDX |
| `politica_billetera_id` | UUID | FK | sí | FK, NULL |
| `cuenta_contable_id` | UUID | FK | sí | FK, NULL, M3 |
| `moneda` | CHAR(3) | — | no | — |
| `estado` | VARCHAR(25) | IDX | no | CK, IDX |
| `nivel_debida_diligencia` | VARCHAR(15) | — | no | CK |
| `saldo_disponible` | DECIMAL(16,2) | — | no | — |
| `saldo_retenido` | DECIMAL(16,2) | — | no | — |
| `saldo_total` | DECIMAL(16,2) | — | no | GENERATED |
| `permite_saldo_negativo` | BOOLEAN | — | no | — |
| `fecha_apertura` | TIMESTAMPTZ | — | no | — |
| `fecha_cierre` | TIMESTAMPTZ | — | sí | NULL |
| `version` | INTEGER | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_cuenta_retenido_no_negativo` | CHECK | `saldo_retenido` |
| `ck_cuenta_saldo_no_negativo` | CHECK | `permite_saldo_negativo`, `saldo_disponible` |
| `ck_cuenta_titularidad` | CHECK | `grupo_id`, `tipo`, `usuario_id` |
| `uq_cuenta_grupo_moneda` | UNIQUE parcial | `grupo_id`, `moneda` |
| `uq_cuenta_usuario_moneda` | UNIQUE parcial | `usuario_id`, `moneda`, `tipo` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_contable_id` | [[cuenta_contable]] | ↗ 03 | sí | [[cuenta_billetera.cuenta_contable_id → cuenta_contable]] |
| `grupo_id` | [[grupo]] | ↗ 02 | sí | [[cuenta_billetera.grupo_id → grupo]] |
| `politica_billetera_id` | [[politica_billetera]] | 10 | sí | [[cuenta_billetera.politica_billetera_id → politica_billetera]] |
| `usuario_id` | [[usuario]] | ↗ 01 | sí | [[cuenta_billetera.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[alerta_monitoreo_lft]] | `cuenta_billetera_id` | ↗ 12 | [[alerta_monitoreo_lft.cuenta_billetera_id → cuenta_billetera]] |
| [[bloqueo_saldo]] | `cuenta_billetera_id` | 10 | [[bloqueo_saldo.cuenta_billetera_id → cuenta_billetera]] |
| [[certificado_saldo]] | `cuenta_billetera_id` | 10 | [[certificado_saldo.cuenta_billetera_id → cuenta_billetera]] |
| [[consumo_limite]] | `cuenta_billetera_id` | 10 | [[consumo_limite.cuenta_billetera_id → cuenta_billetera]] |
| [[estado_cuenta_billetera]] | `cuenta_billetera_id` | 10 | [[estado_cuenta_billetera.cuenta_billetera_id → cuenta_billetera]] |
| [[evaluacion_antifraude]] | `cuenta_billetera_id` | 10 | [[evaluacion_antifraude.cuenta_billetera_id → cuenta_billetera]] |
| [[movimiento_billetera]] | `cuenta_billetera_id` | 10 | [[movimiento_billetera.cuenta_billetera_id → cuenta_billetera]] |
| [[orden_recarga]] | `cuenta_billetera_id` | 10 | [[orden_recarga.cuenta_billetera_id → cuenta_billetera]] |
| [[orden_retiro]] | `cuenta_billetera_id` | 10 | [[orden_retiro.cuenta_billetera_id → cuenta_billetera]] |
| [[retencion_saldo]] | `cuenta_billetera_id` | 10 | [[retencion_saldo.cuenta_billetera_id → cuenta_billetera]] |
| [[saldo_diario_billetera]] | `cuenta_billetera_id` | 10 | [[saldo_diario_billetera.cuenta_billetera_id → cuenta_billetera]] |
| [[solicitud_cierre_billetera]] | `cuenta_billetera_id` | 10 | [[solicitud_cierre_billetera.cuenta_billetera_id → cuenta_billetera]] |
| [[transferencia_p2p]] | `cuenta_billetera_destino_id` | 10 | [[transferencia_p2p.cuenta_billetera_destino_id → cuenta_billetera]] |
| [[transferencia_p2p]] | `cuenta_billetera_origen_id` | 10 | [[transferencia_p2p.cuenta_billetera_origen_id → cuenta_billetera]] |

## Entidades vecinas

[[alerta_monitoreo_lft]] · [[bloqueo_saldo]] · [[certificado_saldo]] · [[consumo_limite]] · [[cuenta_contable]] · [[estado_cuenta_billetera]] · [[evaluacion_antifraude]] · [[grupo]] · [[movimiento_billetera]] · [[orden_recarga]] · [[orden_retiro]] · [[politica_billetera]] · [[retencion_saldo]] · [[saldo_diario_billetera]] · [[solicitud_cierre_billetera]] · [[transferencia_p2p]] · [[usuario]]

## Notas del modelo

> **Reglas duras**
> - UNIQUE (usuario_id, moneda, tipo) para tipo='USUARIO'.
> - UNIQUE (grupo_id, moneda) para tipo='GRUPO'.
> - CHECK: (tipo='USUARIO' AND usuario_id IS NOT NULL)
> OR (tipo='GRUPO'   AND grupo_id  IS NOT NULL)
> OR (tipo LIKE 'PLATAFORMA%' AND usuario_id IS NULL
> AND grupo_id IS NULL)
> - CHECK: saldo_disponible >= 0 salvo permite_saldo_negativo. La regla es
> condicional, asi que vive en el catalogo (R-BIL-02) y no como anotacion
> de la columna: las cuentas tecnicas de contrapartida operan en negativo.
> - saldo_total = saldo_disponible + saldo_retenido.
> - Las columnas de saldo son cache: un job de verificacion
> recalcula desde movimiento_billetera y alerta si difiere
> en un solo centavo.
> usuario_id y grupo_id referencian a los modulos 1 y 2;
> cuenta_contable_id al modulo 3.

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
