---
tags:
  - entidad
  - modulo/08-garantia-incumplimiento-cobranza-y-sanciones
  - append-only
tabla: registro_incumplimiento
clase: RegistroIncumplimiento
modulo: "08 — Garantía, Incumplimiento, Cobranza y Sanciones"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 30
fk_salientes: 9
fk_entrantes: 11
append_only: true
---

# `registro_incumplimiento`

> Módulo [[08_garantia_incumplimiento|08 — Garantía, Incumplimiento, Cobranza y Sanciones]] · clase `RegistroIncumplimiento` · Raíz de agregado · **append-only** (sin `UPDATE`/`DELETE`)

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `codigo_expediente` | VARCHAR(20) | UQ | no | UQ |
| `usuario_id` | UUID | FK IDX | no | FK, IDX |
| `participante_id` | UUID | FK IDX | no | FK, IDX |
| `grupo_id` | UUID | FK IDX | no | FK, IDX |
| `periodo_id` | UUID | FK | sí | FK, NULL |
| `cupo_id` | UUID | FK | sí | FK, NULL |
| `obligacion_id` | UUID | FK UQ | sí | FK, NULL, UQ parcial |
| `entrega_afectada_id` | UUID | FK | sí | FK, NULL, M4 |
| `responsable_gestion` | UUID | FK | sí | FK, NULL |
| `tipo` | VARCHAR(40) | IDX | no | CK, IDX |
| `severidad` | VARCHAR(10) | IDX | no | CK, IDX |
| `estado` | VARCHAR(30) | IDX | no | CK, IDX |
| `origen_deteccion` | VARCHAR(30) | — | no | CK |
| `monto_involucrado` | DECIMAL(14,2) | — | no | — |
| `monto_recuperado` | DECIMAL(14,2) | — | no | — |
| `monto_castigado` | DECIMAL(14,2) | — | no | — |
| `dias_mora_al_detectar` | SMALLINT | — | no | — |
| `dias_mora_actuales` | SMALLINT | — | no | — |
| `es_reincidencia` | BOOLEAN | — | no | — |
| `numero_reincidencia` | SMALLINT | — | no | — |
| `afecto_a_la_entrega` | BOOLEAN | — | no | — |
| `detectado_en` | TIMESTAMPTZ | IDX | no | IDX |
| `notificado_en` | TIMESTAMPTZ | — | sí | NULL |
| `fecha_limite_subsanacion` | TIMESTAMPTZ | — | sí | NULL |
| `cerrado_en` | TIMESTAMPTZ | — | sí | NULL |
| `motivo_cierre` | VARCHAR(200) | — | sí | NULL |
| `resumen_resolucion` | TEXT | — | sí | NULL |
| `reportado_por` | UUID | FK | sí | FK, NULL |
| `version` | INTEGER | — | no | — |

## Reglas del catálogo

> Declaradas en [[Restricciones]], no en el modelo. El nombre es el que devuelve la base al rechazar.

| Regla | Tipo | Columnas |
| --- | :-: | --- |
| `ck_incumplimiento_cierre_motivado` | CHECK | `cerrado_en`, `motivo_cierre` |
| `ck_incumplimiento_plazo_guardado` | CHECK | `fecha_limite_subsanacion`, `notificado_en` |
| `ck_incumplimiento_plazo_posterior` | CHECK | `detectado_en`, `fecha_limite_subsanacion` |

## Claves foráneas salientes

| Columna | Referencia a | Módulo | Opcional | Relación |
| --- | --- | :-: | :-: | --- |
| `cupo_id` | [[cupo]] | ↗ 02 | sí | [[registro_incumplimiento.cupo_id → cupo]] |
| `entrega_afectada_id` | [[entrega_fondo]] | ↗ 04 | sí | [[registro_incumplimiento.entrega_afectada_id → entrega_fondo]] |
| `grupo_id` | [[grupo]] | ↗ 02 | no | [[registro_incumplimiento.grupo_id → grupo]] |
| `obligacion_id` | [[obligacion_aporte]] | ↗ 03 | sí | [[registro_incumplimiento.obligacion_id → obligacion_aporte]] |
| `participante_id` | [[participante]] | ↗ 02 | no | [[registro_incumplimiento.participante_id → participante]] |
| `periodo_id` | [[periodo]] | ↗ 02 | sí | [[registro_incumplimiento.periodo_id → periodo]] |
| `reportado_por` | [[usuario]] | ↗ 01 | sí | [[registro_incumplimiento.reportado_por → usuario]] |
| `responsable_gestion` | [[usuario]] | ↗ 01 | sí | [[registro_incumplimiento.responsable_gestion → usuario]] |
| `usuario_id` | [[usuario]] | ↗ 01 | no | [[registro_incumplimiento.usuario_id → usuario]] |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[acuerdo_quita]] | `registro_id` | 08 | [[acuerdo_quita.registro_id → registro_incumplimiento]] |
| [[cobertura_incumplimiento]] | `registro_id` | 08 | [[cobertura_incumplimiento.registro_id → registro_incumplimiento]] |
| [[descargo_participante]] | `registro_id` | 08 | [[descargo_participante.registro_id → registro_incumplimiento]] |
| [[deuda_participante]] | `registro_id` | 08 | [[deuda_participante.registro_id → registro_incumplimiento]] |
| [[ejecucion_aval]] | `registro_id` | 08 | [[ejecucion_aval.registro_id → registro_incumplimiento]] |
| [[evidencia_incumplimiento]] | `registro_id` | 08 | [[evidencia_incumplimiento.registro_id → registro_incumplimiento]] |
| [[gestion_cobranza]] | `registro_id` | 08 | [[gestion_cobranza.registro_id → registro_incumplimiento]] |
| [[historial_estado_incumplimiento]] | `registro_id` | 08 | [[historial_estado_incumplimiento.registro_id → registro_incumplimiento]] |
| [[lista_restriccion_interna]] | `registro_origen_id` | 08 | [[lista_restriccion_interna.registro_origen_id → registro_incumplimiento]] |
| [[reemplazo_participante]] | `registro_id` | 08 | [[reemplazo_participante.registro_id → registro_incumplimiento]] |
| [[sancion]] | `registro_id` | 08 | [[sancion.registro_id → registro_incumplimiento]] |

## Entidades vecinas

[[acuerdo_quita]] · [[cobertura_incumplimiento]] · [[cupo]] · [[descargo_participante]] · [[deuda_participante]] · [[ejecucion_aval]] · [[entrega_fondo]] · [[evidencia_incumplimiento]] · [[gestion_cobranza]] · [[grupo]] · [[historial_estado_incumplimiento]] · [[lista_restriccion_interna]] · [[obligacion_aporte]] · [[participante]] · [[periodo]] · [[reemplazo_participante]] · [[sancion]] · [[usuario]]

## Notas del modelo

> **Expediente unico por hecho**
> UNIQUE parcial (obligacion_id)
> WHERE estado NOT IN ('ANULADO_POR_ERROR')
> -> un aporte impago no genera dos expedientes.
> codigo_expediente es legible para citarlo en
> notificaciones y reclamos.
> Indices operativos:
> (estado, dias_mora_actuales DESC) para la
> bandeja de cobranza;
> (usuario_id, detectado_en DESC) para el
> historial portable.

## Ver también

- Justificación de negocio: [[08_garantia_incumplimiento]]
- Diagramas: `docs/entidades/08_garantia_incumplimiento.puml`
- Índice: [[_Entidades]] · [[Index]]
