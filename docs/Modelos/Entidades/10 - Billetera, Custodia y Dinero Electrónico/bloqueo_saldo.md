---
tags:
  - entidad
  - modulo/10-billetera-custodia-y-dinero-electronico
tabla: bloqueo_saldo
clase: BloqueoSaldo
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
clave_primaria: [id]
columnas: 15
fk_salientes: 3
fk_entrantes: 1
append_only: false
---

# `bloqueo_saldo`

> Módulo [[10_billetera_custodia|10 — Billetera, Custodia y Dinero Electrónico]] · clase `BloqueoSaldo`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `cuenta_billetera_id` | UUID | FK IDX | no | FK, IDX |
| `retencion_id` | UUID | FK | sí | FK, NULL |
| `levantada_por` | UUID | FK | sí | FK, NULL |
| `autoridad` | VARCHAR(20) | — | no | CK |
| `tipo_orden` | VARCHAR(30) | — | no | CK |
| `numero_oficio` | VARCHAR(60) | UQ | no | — |
| `monto_bloqueado` | DECIMAL(16,2) | — | sí | NULL |
| `alcance` | VARCHAR(10) | — | no | CK |
| `documento_url` | VARCHAR(255) | — | no | — |
| `hash_documento` | VARCHAR(64) | — | no | — |
| `estado` | VARCHAR(15) | IDX | no | CK, IDX |
| `recibido_en` | TIMESTAMPTZ | — | no | — |
| `vence_en` | TIMESTAMPTZ | — | sí | NULL |
| `levantado_en` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `uq_bloqueo_oficio` | UNIQUE | `numero_oficio` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cuenta_billetera_id` | [[cuenta_billetera]] | 10 | no | [[bloqueo_saldo.cuenta_billetera_id → cuenta_billetera]] |
| `levantada_por` | [[usuario]] | ↗ 01 | sí | [[bloqueo_saldo.levantada_por → usuario]] |
| `retencion_id` | [[retencion_saldo]] | 10 | sí | [[bloqueo_saldo.retencion_id → retencion_saldo]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[requerimiento_autoridad]] | `bloqueo_saldo_id` | ↗ 12 | [[requerimiento_autoridad.bloqueo_saldo_id → bloqueo_saldo]] |

## Entidades vecinas

[[cuenta_billetera]] · [[requerimiento_autoridad]] · [[retencion_saldo]] · [[usuario]]

## Notas del modelo

> Una orden de autoridad se materializa como retencion_saldo
> (no como borrado ni como saldo negativo): el dinero sigue
> siendo del titular, pero deja de estar disponible.
> Se conserva el oficio y su hash para poder demostrar que
> el bloqueo tuvo respaldo legal.

## Ver también

- Justificación de negocio: [[10_billetera_custodia]]
- Diagramas: `docs/entidades/10_billetera_custodia.puml`
- Índice: [[_Entidades]] · [[Index]]
