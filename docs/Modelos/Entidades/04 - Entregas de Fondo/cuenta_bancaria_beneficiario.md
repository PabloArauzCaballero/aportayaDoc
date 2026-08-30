---
tags:
  - entidad
  - modulo/04-entregas-de-fondo
tabla: cuenta_bancaria_beneficiario
clase: CuentaBancariaBeneficiario
modulo: "04 — Entregas de Fondo"
clave_primaria: [id]
columnas: 16
fk_salientes: 1
fk_entrantes: 2
append_only: false
---

# `cuenta_bancaria_beneficiario`

> Módulo [[04_entregas_fondo|04 — Entregas de Fondo]] · clase `CuentaBancariaBeneficiario`

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `tipo_cuenta` | VARCHAR(15) | — | no | CK |
| `entidad_financiera` | VARCHAR(60) | — | no | — |
| `numero_cuenta_cifrado` | VARCHAR(255) | — | no | — |
| `version_llave` | SMALLINT | — | no | — |
| `hash_numero_cuenta` | VARCHAR(64) | — | no | — |
| `numero_enmascarado` | VARCHAR(30) | — | no | — |
| `titular_nombre` | VARCHAR(120) | — | no | — |
| `titular_documento` | VARCHAR(30) | — | no | — |
| `moneda` | CHAR(3) | — | no | — |
| `es_principal` | BOOLEAN | — | no | — |
| `estado_verificacion` | VARCHAR(15) | — | no | CK |
| `metodo_verificacion` | VARCHAR(20) | — | sí | NULL |
| `verificada_en` | TIMESTAMPTZ | — | sí | NULL |
| `bloqueada_hasta` | TIMESTAMPTZ | — | sí | NULL |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_cuenta_bancaria_sin_claro` | CHECK | `hash_numero_cuenta`, `numero_enmascarado` |
| `ck_cuenta_bancaria_version_llave` | CHECK | `version_llave` |
| `ck_cuenta_benef_hash_completo` | CHECK | `hash_numero_cuenta` |
| `ck_cuenta_benef_verificada` | CHECK | `estado_verificacion`, `verificada_en` |
| `uq_cuenta_benef_hash` | UNIQUE | `usuario_id`, `hash_numero_cuenta` |
| `uq_cuenta_benef_principal` | UNIQUE parcial | `usuario_id` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[cuenta_bancaria_beneficiario.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[entrega_fondo]] | `cuenta_destino_id` | 04 | [[entrega_fondo.cuenta_destino_id → cuenta_bancaria_beneficiario]] |
| [[orden_desembolso]] | `cuenta_destino_id` | 04 | [[orden_desembolso.cuenta_destino_id → cuenta_bancaria_beneficiario]] |

## Entidades vecinas

[[entrega_fondo]] · [[orden_desembolso]] · [[usuario]]

## Notas del modelo

> hash_numero_cuenta detecta que dos usuarios
> distintos declaren la misma cuenta destino
> (patron tipico de mula financiera): dispara
> alerta de cumplimiento en el modulo 9.

## Ver también

- Justificación de negocio: [[04_entregas_fondo]]
- Diagramas: `docs/entidades/04_entregas_fondo.puml`
- Índice: [[_Entidades]] · [[Index]]
