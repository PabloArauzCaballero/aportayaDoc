---
tags:
  - entidad
  - modulo/04-entregas-de-fondo
tabla: entrega_fondo
clase: EntregaFondo
modulo: "04 — Entregas de Fondo"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 23
fk_salientes: 8
fk_entrantes: 8
append_only: false
---

# `entrega_fondo`

> Módulo [[04_entregas_fondo|04 — Entregas de Fondo]] · clase `EntregaFondo` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `grupo_id` | UUID | FK IDX | no | FK, IDX |
| `periodo_id` | UUID | FK UQ | no | FK |
| `turno_id` | UUID | FK UQ | no | FK |
| `cupo_id` | UUID | FK | no | FK |
| `beneficiario_participante_id` | UUID | FK IDX | no | FK, IDX |
| `cuenta_destino_id` | UUID | FK | sí | FK, NULL |
| `monto_bolsa_bruto` | DECIMAL(14,2) | — | no | — |
| `total_deducciones` | DECIMAL(14,2) | — | no | — |
| `monto_neto_a_entregar` | DECIMAL(14,2) | — | no | — |
| `monto_efectivamente_entregado` | DECIMAL(14,2) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `estado` | VARCHAR(35) | IDX | no | CK, IDX |
| `metodo_desembolso` | VARCHAR(30) | — | no | CK |
| `fecha_programada` | DATE | IDX | no | IDX |
| `fecha_autorizacion` | TIMESTAMPTZ | — | sí | NULL |
| `fecha_entrega` | TIMESTAMPTZ | — | sí | NULL |
| `autorizada_por` | UUID | FK | sí | FK, NULL |
| `ejecutada_por` | UUID | FK | sí | FK, NULL |
| `comprobante_url` | VARCHAR(255) | — | sí | NULL |
| `hash_comprobante` | VARCHAR(64) | — | sí | NULL |
| `observaciones` | VARCHAR(400) | — | sí | NULL |
| `version` | INTEGER | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_entrega_neto` | CHECK | `monto_bolsa_bruto`, `monto_neto_a_entregar`, `total_deducciones` |
| `ck_entrega_neto_no_negativo` | CHECK | `monto_neto_a_entregar` |
| `ck_entrega_segregacion` | CHECK | `autorizada_por`, `ejecutada_por` |
| `uq_entrega_periodo` | UNIQUE | `periodo_id` |
| `uq_entrega_turno` | UNIQUE | `turno_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `autorizada_por` | [[usuario]] | ↗ 01 | sí | [[entrega_fondo.autorizada_por → usuario]] |
| `beneficiario_participante_id` | [[participante]] | ↗ 02 | no | [[entrega_fondo.beneficiario_participante_id → participante]] |
| `cuenta_destino_id` | [[cuenta_bancaria_beneficiario]] | 04 | sí | [[entrega_fondo.cuenta_destino_id → cuenta_bancaria_beneficiario]] |
| `cupo_id` | [[cupo]] | ↗ 02 | no | [[entrega_fondo.cupo_id → cupo]] |
| `ejecutada_por` | [[usuario]] | ↗ 01 | sí | [[entrega_fondo.ejecutada_por → usuario]] |
| `grupo_id` | [[grupo]] | ↗ 02 | no | [[entrega_fondo.grupo_id → grupo]] |
| `periodo_id` | [[periodo]] | ↗ 02 | no | [[entrega_fondo.periodo_id → periodo]] |
| `turno_id` | [[turno]] | ↗ 02 | no | [[entrega_fondo.turno_id → turno]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[abono_recuperacion]] | `entrega_id` | ↗ 08 | [[abono_recuperacion.entrega_id → entrega_fondo]] |
| [[confirmacion_recepcion]] | `entrega_id` | 04 | [[confirmacion_recepcion.entrega_id → entrega_fondo]] |
| [[deduccion_entrega]] | `entrega_id` | 04 | [[deduccion_entrega.entrega_id → entrega_fondo]] |
| [[historial_estado_entrega]] | `entrega_id` | 04 | [[historial_estado_entrega.entrega_id → entrega_fondo]] |
| [[incidencia_entrega]] | `entrega_id` | 04 | [[incidencia_entrega.entrega_id → entrega_fondo]] |
| [[orden_desembolso]] | `entrega_id` | 04 | [[orden_desembolso.entrega_id → entrega_fondo]] |
| [[registro_incumplimiento]] | `entrega_afectada_id` | ↗ 08 | [[registro_incumplimiento.entrega_afectada_id → entrega_fondo]] |
| [[validacion_pre_entrega]] | `entrega_id` | 04 | [[validacion_pre_entrega.entrega_id → entrega_fondo]] |

## Entidades vecinas

[[abono_recuperacion]] · [[confirmacion_recepcion]] · [[cuenta_bancaria_beneficiario]] · [[cupo]] · [[deduccion_entrega]] · [[grupo]] · [[historial_estado_entrega]] · [[incidencia_entrega]] · [[orden_desembolso]] · [[participante]] · [[periodo]] · [[registro_incumplimiento]] · [[turno]] · [[usuario]] · [[validacion_pre_entrega]]

## Notas del modelo

> **Restricciones clave**
> - UNIQUE (turno_id) y UNIQUE (periodo_id):
> imposible entregar dos veces el mismo turno.
> - CHECK monto_neto_a_entregar =
> monto_bolsa_bruto - total_deducciones.
> - Trigger: no se permite pasar a AUTORIZADA
> si existe validacion_pre_entrega bloqueante
> con resultado 'RECHAZADA'.

## Ver también

- Justificación de negocio: [[04_entregas_fondo]]
- Diagramas: `docs/entidades/04_entregas_fondo.puml`
- Índice: [[_Entidades]] · [[Index]]
