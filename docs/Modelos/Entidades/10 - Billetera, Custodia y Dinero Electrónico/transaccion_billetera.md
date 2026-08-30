---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
  - append-only
tabla: transaccion_billetera
clase: TransaccionBilletera
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 20
fk_salientes: 5
fk_entrantes: 14
append_only: true
---

# `transaccion_billetera`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `TransaccionBilletera` · Raíz de agregado · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `secuencia` | BIGSERIAL | UQ | no | UQ |
| `tipo` | VARCHAR(30) | IDX | no | CK, IDX |
| `estado` | VARCHAR(20) | IDX | no | CK, IDX |
| `moneda` | CHAR(3) | — | no | — |
| `monto_total` | DECIMAL(16,2) | — | no | CK: > 0 |
| `grupo_id` | UUID | FK IDX | sí | FK, NULL, IDX |
| `asiento_contable_id` | UUID | FK | sí | FK, NULL, M3 |
| `sesion_id` | UUID | FK | sí | FK, NULL, M1 |
| `dispositivo_id` | UUID | FK | sí | FK, NULL, M1 |
| `iniciada_por` | UUID | FK | sí | FK, NULL |
| `origen_tipo` | VARCHAR(30) | — | no | CK |
| `origen_id` | UUID | IDX | no | IDX, polimorfica |
| `canal` | VARCHAR(15) | — | no | CK |
| `ip_origen` | INET | — | sí | NULL |
| `clave_idempotencia` | VARCHAR(100) | — | no | — |
| `hash_registro` | VARCHAR(64) | — | no | — |
| `hash_anterior` | VARCHAR(64) | — | sí | NULL |
| `ocurrida_en` | TIMESTAMPTZ | IDX | no | IDX |
| `registrada_en` | TIMESTAMPTZ | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ix_transaccion_ocurrida` | INDEX | `ocurrida_en` |
| `uq_tx_idem` | UNIQUE | expresión |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `asiento_contable_id` | [[asiento_contable]] | ↗ 03 | sí | [[transaccion_billetera.asiento_contable_id → asiento_contable]] |
| `dispositivo_id` | [[dispositivo]] | ↗ 01 | sí | [[transaccion_billetera.dispositivo_id → dispositivo]] |
| `grupo_id` | [[grupo]] | ↗ 02 | sí | [[transaccion_billetera.grupo_id → grupo]] |
| `iniciada_por` | [[usuario]] | ↗ 01 | sí | [[transaccion_billetera.iniciada_por → usuario]] |
| `sesion_id` | [[sesion]] | ↗ 01 | sí | [[transaccion_billetera.sesion_id → sesion]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[alerta_monitoreo_lft]] | `transaccion_id` | ↗ 12 | [[alerta_monitoreo_lft.transaccion_id → transaccion_billetera]] |
| [[cargo_comision]] | `transaccion_id` | ↗ 11 | [[cargo_comision.transaccion_id → transaccion_billetera]] |
| [[costo_proveedor_operacion]] | `transaccion_id` | ↗ 11 | [[costo_proveedor_operacion.transaccion_id → transaccion_billetera]] |
| [[declaracion_origen_fondos]] | `transaccion_id` | ↗ 12 | [[declaracion_origen_fondos.transaccion_id → transaccion_billetera]] |
| [[devolucion_comision]] | `transaccion_id` | ↗ 11 | [[devolucion_comision.transaccion_id → transaccion_billetera]] |
| [[evaluacion_antifraude]] | `transaccion_id` | 10 | [[evaluacion_antifraude.transaccion_id → transaccion_billetera]] |
| [[movimiento_billetera]] | `transaccion_id` | 10 | [[movimiento_billetera.transaccion_id → transaccion_billetera]] |
| [[orden_recarga]] | `transaccion_id` | 10 | [[orden_recarga.transaccion_id → transaccion_billetera]] |
| [[orden_retiro]] | `transaccion_id` | 10 | [[orden_retiro.transaccion_id → transaccion_billetera]] |
| [[registro_operacion_relevante]] | `transaccion_id` | ↗ 12 | [[registro_operacion_relevante.transaccion_id → transaccion_billetera]] |
| [[retencion_saldo]] | `transaccion_origen_id` | 10 | [[retencion_saldo.transaccion_origen_id → transaccion_billetera]] |
| [[reverso_transaccion]] | `transaccion_original_id` | 10 | [[reverso_transaccion.transaccion_original_id → transaccion_billetera]] |
| [[reverso_transaccion]] | `transaccion_reverso_id` | 10 | [[reverso_transaccion.transaccion_reverso_id → transaccion_billetera]] |
| [[transferencia_p2p]] | `transaccion_id` | 10 | [[transferencia_p2p.transaccion_id → transaccion_billetera]] |

## Entidades vecinas

[[alerta_monitoreo_lft]] · [[asiento_contable]] · [[cargo_comision]] · [[costo_proveedor_operacion]] · [[declaracion_origen_fondos]] · [[devolucion_comision]] · [[dispositivo]] · [[evaluacion_antifraude]] · [[grupo]] · [[movimiento_billetera]] · [[orden_recarga]] · [[orden_retiro]] · [[registro_operacion_relevante]] · [[retencion_saldo]] · [[reverso_transaccion]] · [[sesion]] · [[transferencia_p2p]] · [[usuario]]

## Notas del modelo

> **Cadena de integridad**
> hash_registro = SHA256(secuencia || tipo || monto_total ||
> origen_tipo || origen_id || ocurrida_en || hash_anterior).
> Enlaza con evento_dominio y bitacora_evento (M9).
> origen_tipo/origen_id son polimorficas: obligacion_aporte.id
> (M3), entrega_fondo.id (M4), devengo_comision.id (M11),
> cobertura_incumplimiento.id (M8).

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
