-- Índices y restricciones de unicidad del módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE UNIQUE INDEX IF NOT EXISTS uq_fondo_garantia_grupo_id
  ON garantia.fondo_garantia (grupo_id);

CREATE INDEX IF NOT EXISTS ix_movimiento_fondo_fondo_id
  ON garantia.movimiento_fondo (fondo_id);

CREATE INDEX IF NOT EXISTS ix_movimiento_fondo_tipo
  ON garantia.movimiento_fondo (tipo);

CREATE INDEX IF NOT EXISTS ix_movimiento_fondo_referencia_id
  ON garantia.movimiento_fondo (referencia_id);

CREATE INDEX IF NOT EXISTS ix_movimiento_fondo_fecha
  ON garantia.movimiento_fondo (fecha);

CREATE INDEX IF NOT EXISTS ix_devolucion_fondo_fondo_id
  ON garantia.devolucion_fondo (fondo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_registro_incumplimiento_codigo_expediente
  ON garantia.registro_incumplimiento (codigo_expediente);

CREATE INDEX IF NOT EXISTS ix_registro_incumplimiento_usuario_id
  ON garantia.registro_incumplimiento (usuario_id);

CREATE INDEX IF NOT EXISTS ix_registro_incumplimiento_participante_id
  ON garantia.registro_incumplimiento (participante_id);

CREATE INDEX IF NOT EXISTS ix_registro_incumplimiento_grupo_id
  ON garantia.registro_incumplimiento (grupo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_registro_incumplimiento_obligacion_id
  ON garantia.registro_incumplimiento (obligacion_id);

CREATE INDEX IF NOT EXISTS ix_registro_incumplimiento_tipo
  ON garantia.registro_incumplimiento (tipo);

CREATE INDEX IF NOT EXISTS ix_registro_incumplimiento_severidad
  ON garantia.registro_incumplimiento (severidad);

CREATE INDEX IF NOT EXISTS ix_registro_incumplimiento_estado
  ON garantia.registro_incumplimiento (estado);

CREATE INDEX IF NOT EXISTS ix_registro_incumplimiento_detectado_en
  ON garantia.registro_incumplimiento (detectado_en);

CREATE INDEX IF NOT EXISTS ix_evidencia_incumplimiento_registro_id
  ON garantia.evidencia_incumplimiento (registro_id);

CREATE INDEX IF NOT EXISTS ix_historial_estado_incumplimiento_registro_id
  ON garantia.historial_estado_incumplimiento (registro_id);

CREATE INDEX IF NOT EXISTS ix_historial_estado_incumplimiento_fecha_hora
  ON garantia.historial_estado_incumplimiento (fecha_hora);

CREATE INDEX IF NOT EXISTS ix_descargo_participante_registro_id
  ON garantia.descargo_participante (registro_id);

CREATE INDEX IF NOT EXISTS ix_historial_incumplimiento_usuario_incumplimientos_abiertos
  ON garantia.historial_incumplimiento_usuario (incumplimientos_abiertos);

CREATE INDEX IF NOT EXISTS ix_lista_restriccion_interna_usuario_id
  ON garantia.lista_restriccion_interna (usuario_id);

CREATE INDEX IF NOT EXISTS ix_score_riesgo_incumplimiento_usuario_id
  ON garantia.score_riesgo_incumplimiento (usuario_id);

CREATE INDEX IF NOT EXISTS ix_score_riesgo_incumplimiento_nivel_riesgo
  ON garantia.score_riesgo_incumplimiento (nivel_riesgo);

CREATE INDEX IF NOT EXISTS ix_alerta_temprana_usuario_id
  ON garantia.alerta_temprana (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_estrategia_cobranza_dias_mora_desde_etapa
  ON garantia.estrategia_cobranza (dias_mora_desde, etapa);

CREATE UNIQUE INDEX IF NOT EXISTS uq_gestion_cobranza_registro_id
  ON garantia.gestion_cobranza (registro_id);

CREATE INDEX IF NOT EXISTS ix_gestion_cobranza_gestor_asignado_id
  ON garantia.gestion_cobranza (gestor_asignado_id);

CREATE INDEX IF NOT EXISTS ix_gestion_cobranza_etapa_actual
  ON garantia.gestion_cobranza (etapa_actual);

CREATE INDEX IF NOT EXISTS ix_gestion_cobranza_proxima_accion_en
  ON garantia.gestion_cobranza (proxima_accion_en);

CREATE INDEX IF NOT EXISTS ix_gestion_cobranza_estado
  ON garantia.gestion_cobranza (estado);

CREATE INDEX IF NOT EXISTS ix_accion_cobranza_gestion_id
  ON garantia.accion_cobranza (gestion_id);

CREATE INDEX IF NOT EXISTS ix_accion_cobranza_ejecutada_en
  ON garantia.accion_cobranza (ejecutada_en);

CREATE INDEX IF NOT EXISTS ix_promesa_pago_gestion_id
  ON garantia.promesa_pago (gestion_id);

CREATE INDEX IF NOT EXISTS ix_promesa_pago_fecha_prometida
  ON garantia.promesa_pago (fecha_prometida);

CREATE INDEX IF NOT EXISTS ix_promesa_pago_estado
  ON garantia.promesa_pago (estado);

CREATE UNIQUE INDEX IF NOT EXISTS uq_acuerdo_quita_registro_id
  ON garantia.acuerdo_quita (registro_id);

CREATE INDEX IF NOT EXISTS ix_cobertura_incumplimiento_fondo_id
  ON garantia.cobertura_incumplimiento (fondo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cobertura_incumplimiento_registro_id
  ON garantia.cobertura_incumplimiento (registro_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cobertura_incumplimiento_obligacion_id
  ON garantia.cobertura_incumplimiento (obligacion_id);

CREATE INDEX IF NOT EXISTS ix_cobertura_incumplimiento_estado
  ON garantia.cobertura_incumplimiento (estado);

CREATE INDEX IF NOT EXISTS ix_deuda_participante_usuario_id
  ON garantia.deuda_participante (usuario_id);

CREATE INDEX IF NOT EXISTS ix_deuda_participante_grupo_id
  ON garantia.deuda_participante (grupo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_deuda_participante_registro_id
  ON garantia.deuda_participante (registro_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_deuda_participante_cobertura_id
  ON garantia.deuda_participante (cobertura_id);

CREATE INDEX IF NOT EXISTS ix_deuda_participante_saldo_actual
  ON garantia.deuda_participante (saldo_actual);

CREATE INDEX IF NOT EXISTS ix_deuda_participante_estado
  ON garantia.deuda_participante (estado);

CREATE INDEX IF NOT EXISTS ix_deuda_participante_fecha_prescripcion
  ON garantia.deuda_participante (fecha_prescripcion);

CREATE UNIQUE INDEX IF NOT EXISTS uq_subrogacion_cobertura_id
  ON garantia.subrogacion (cobertura_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_subrogacion_deuda_id
  ON garantia.subrogacion (deuda_id);

CREATE INDEX IF NOT EXISTS ix_abono_recuperacion_deuda_id
  ON garantia.abono_recuperacion (deuda_id);

CREATE INDEX IF NOT EXISTS ix_abono_recuperacion_fecha
  ON garantia.abono_recuperacion (fecha);

CREATE UNIQUE INDEX IF NOT EXISTS uq_castigo_deuda_deuda_id
  ON garantia.castigo_deuda (deuda_id);

CREATE INDEX IF NOT EXISTS ix_aval_participante_grupo_id
  ON garantia.aval_participante (grupo_id);

CREATE INDEX IF NOT EXISTS ix_aval_participante_participante_avalado_id
  ON garantia.aval_participante (participante_avalado_id);

CREATE INDEX IF NOT EXISTS ix_aval_participante_avalista_usuario_id
  ON garantia.aval_participante (avalista_usuario_id);

CREATE INDEX IF NOT EXISTS ix_aval_participante_estado
  ON garantia.aval_participante (estado);

CREATE INDEX IF NOT EXISTS ix_ejecucion_aval_aval_id
  ON garantia.ejecucion_aval (aval_id);

CREATE INDEX IF NOT EXISTS ix_ejecucion_aval_registro_id
  ON garantia.ejecucion_aval (registro_id);

CREATE INDEX IF NOT EXISTS ix_matriz_sancion_politica_id
  ON garantia.matriz_sancion (politica_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_matriz_sancion_severidad_numero_reincidencia_tipo_i_1bd698
  ON garantia.matriz_sancion (severidad, numero_reincidencia, tipo_incumplimiento);

CREATE INDEX IF NOT EXISTS ix_sancion_registro_id
  ON garantia.sancion (registro_id);

CREATE INDEX IF NOT EXISTS ix_sancion_usuario_id
  ON garantia.sancion (usuario_id);

CREATE INDEX IF NOT EXISTS ix_sancion_tipo
  ON garantia.sancion (tipo);

CREATE INDEX IF NOT EXISTS ix_sancion_estado
  ON garantia.sancion (estado);

CREATE INDEX IF NOT EXISTS ix_apelacion_sancion_sancion_id
  ON garantia.apelacion_sancion (sancion_id);

CREATE INDEX IF NOT EXISTS ix_reemplazo_participante_grupo_id
  ON garantia.reemplazo_participante (grupo_id);

CREATE INDEX IF NOT EXISTS ix_candidato_reemplazo_reemplazo_id
  ON garantia.candidato_reemplazo (reemplazo_id);

CREATE INDEX IF NOT EXISTS ix_plan_contingencia_grupo_id
  ON garantia.plan_contingencia (grupo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_disolucion_anticipada_grupo_id
  ON garantia.disolucion_anticipada (grupo_id);

CREATE INDEX IF NOT EXISTS ix_liquidacion_participante_disolucion_id
  ON garantia.liquidacion_participante (disolucion_id);
