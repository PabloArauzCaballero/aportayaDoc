-- Índices y restricciones de unicidad del módulo 10 — Billetera, Custodia y Dinero Electrónico
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE UNIQUE INDEX IF NOT EXISTS uq_politica_billetera_codigo
  ON nucleo_financiero.politica_billetera (codigo);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cuenta_billetera_numero_cuenta
  ON nucleo_financiero.cuenta_billetera (numero_cuenta);

CREATE INDEX IF NOT EXISTS ix_cuenta_billetera_tipo
  ON nucleo_financiero.cuenta_billetera (tipo);

CREATE INDEX IF NOT EXISTS ix_cuenta_billetera_usuario_id
  ON nucleo_financiero.cuenta_billetera (usuario_id);

CREATE INDEX IF NOT EXISTS ix_cuenta_billetera_grupo_id
  ON nucleo_financiero.cuenta_billetera (grupo_id);

CREATE INDEX IF NOT EXISTS ix_cuenta_billetera_estado
  ON nucleo_financiero.cuenta_billetera (estado);

CREATE INDEX IF NOT EXISTS ix_saldo_diario_billetera_cuenta_billetera_id
  ON nucleo_financiero.saldo_diario_billetera (cuenta_billetera_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_transaccion_billetera_secuencia
  ON nucleo_financiero.transaccion_billetera (secuencia);

CREATE INDEX IF NOT EXISTS ix_transaccion_billetera_tipo
  ON nucleo_financiero.transaccion_billetera (tipo);

CREATE INDEX IF NOT EXISTS ix_transaccion_billetera_estado
  ON nucleo_financiero.transaccion_billetera (estado);

CREATE INDEX IF NOT EXISTS ix_transaccion_billetera_grupo_id
  ON nucleo_financiero.transaccion_billetera (grupo_id);

CREATE INDEX IF NOT EXISTS ix_transaccion_billetera_origen_id
  ON nucleo_financiero.transaccion_billetera (origen_id);

CREATE INDEX IF NOT EXISTS ix_transaccion_billetera_ocurrida_en
  ON nucleo_financiero.transaccion_billetera (ocurrida_en);

CREATE INDEX IF NOT EXISTS ix_movimiento_billetera_transaccion_id
  ON nucleo_financiero.movimiento_billetera (transaccion_id);

CREATE INDEX IF NOT EXISTS ix_movimiento_billetera_cuenta_billetera_id
  ON nucleo_financiero.movimiento_billetera (cuenta_billetera_id);

CREATE INDEX IF NOT EXISTS ix_movimiento_billetera_registrado_en
  ON nucleo_financiero.movimiento_billetera (registrado_en);

CREATE INDEX IF NOT EXISTS ix_retencion_saldo_cuenta_billetera_id
  ON nucleo_financiero.retencion_saldo (cuenta_billetera_id);

CREATE INDEX IF NOT EXISTS ix_retencion_saldo_motivo
  ON nucleo_financiero.retencion_saldo (motivo);

CREATE INDEX IF NOT EXISTS ix_retencion_saldo_estado
  ON nucleo_financiero.retencion_saldo (estado);

CREATE INDEX IF NOT EXISTS ix_retencion_saldo_expira_en
  ON nucleo_financiero.retencion_saldo (expira_en);

CREATE INDEX IF NOT EXISTS ix_reverso_transaccion_transaccion_original_id
  ON nucleo_financiero.reverso_transaccion (transaccion_original_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_reverso_transaccion_transaccion_reverso_id
  ON nucleo_financiero.reverso_transaccion (transaccion_reverso_id);

CREATE INDEX IF NOT EXISTS ix_instrumento_fondeo_usuario_id
  ON nucleo_financiero.instrumento_fondeo (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_instrumento_fondeo_usuario_id_hash_identificador
  ON nucleo_financiero.instrumento_fondeo (usuario_id, hash_identificador);

CREATE INDEX IF NOT EXISTS ix_orden_recarga_cuenta_billetera_id
  ON nucleo_financiero.orden_recarga (cuenta_billetera_id);

CREATE INDEX IF NOT EXISTS ix_orden_recarga_estado
  ON nucleo_financiero.orden_recarga (estado);

CREATE INDEX IF NOT EXISTS ix_orden_retiro_cuenta_billetera_id
  ON nucleo_financiero.orden_retiro (cuenta_billetera_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_orden_retiro_retencion_id
  ON nucleo_financiero.orden_retiro (retencion_id);

CREATE INDEX IF NOT EXISTS ix_orden_retiro_solicitada_por
  ON nucleo_financiero.orden_retiro (solicitada_por);

CREATE INDEX IF NOT EXISTS ix_orden_retiro_estado
  ON nucleo_financiero.orden_retiro (estado);

CREATE UNIQUE INDEX IF NOT EXISTS uq_orden_retiro_referencia_proveedor
  ON nucleo_financiero.orden_retiro (referencia_proveedor);

CREATE UNIQUE INDEX IF NOT EXISTS uq_transferencia_p2p_transaccion_id
  ON nucleo_financiero.transferencia_p2p (transaccion_id);

CREATE INDEX IF NOT EXISTS ix_transferencia_p2p_cuenta_billetera_origen_id
  ON nucleo_financiero.transferencia_p2p (cuenta_billetera_origen_id);

CREATE INDEX IF NOT EXISTS ix_transferencia_p2p_cuenta_billetera_destino_id
  ON nucleo_financiero.transferencia_p2p (cuenta_billetera_destino_id);

CREATE INDEX IF NOT EXISTS ix_movimiento_custodia_cuenta_custodia_id
  ON nucleo_financiero.movimiento_custodia (cuenta_custodia_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_movimiento_custodia_movimiento_bancario_id
  ON nucleo_financiero.movimiento_custodia (movimiento_bancario_id);

CREATE INDEX IF NOT EXISTS ix_movimiento_custodia_fecha_valor
  ON nucleo_financiero.movimiento_custodia (fecha_valor);

CREATE UNIQUE INDEX IF NOT EXISTS uq_movimiento_custodia_referencia_bancaria
  ON nucleo_financiero.movimiento_custodia (referencia_bancaria);

CREATE INDEX IF NOT EXISTS ix_movimiento_custodia_conciliado
  ON nucleo_financiero.movimiento_custodia (conciliado);

CREATE INDEX IF NOT EXISTS ix_conciliacion_custodia_cuenta_custodia_id
  ON nucleo_financiero.conciliacion_custodia (cuenta_custodia_id);

CREATE INDEX IF NOT EXISTS ix_conciliacion_custodia_cumple_encaje
  ON nucleo_financiero.conciliacion_custodia (cumple_encaje);

CREATE INDEX IF NOT EXISTS ix_conciliacion_custodia_estado
  ON nucleo_financiero.conciliacion_custodia (estado);

CREATE INDEX IF NOT EXISTS ix_descuadre_custodia_conciliacion_custodia_id
  ON nucleo_financiero.descuadre_custodia (conciliacion_custodia_id);

CREATE INDEX IF NOT EXISTS ix_descuadre_custodia_severidad
  ON nucleo_financiero.descuadre_custodia (severidad);

CREATE INDEX IF NOT EXISTS ix_descuadre_custodia_estado
  ON nucleo_financiero.descuadre_custodia (estado);

CREATE INDEX IF NOT EXISTS ix_consumo_limite_cuenta_billetera_id
  ON nucleo_financiero.consumo_limite (cuenta_billetera_id);

CREATE INDEX IF NOT EXISTS ix_consumo_limite_limite_id
  ON nucleo_financiero.consumo_limite (limite_id);

CREATE INDEX IF NOT EXISTS ix_respuesta_idempotente_usuario_id
  ON nucleo_financiero.respuesta_idempotente (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_respuesta_idempotente_usuario_id_clave_idempotencia_4b4bc9
  ON nucleo_financiero.respuesta_idempotente (usuario_id, clave_idempotencia, operacion);

CREATE INDEX IF NOT EXISTS ix_respuesta_idempotente_expira_en
  ON nucleo_financiero.respuesta_idempotente (expira_en);

CREATE UNIQUE INDEX IF NOT EXISTS uq_regla_antifraude_codigo
  ON nucleo_financiero.regla_antifraude (codigo);

CREATE INDEX IF NOT EXISTS ix_regla_antifraude_activa
  ON nucleo_financiero.regla_antifraude (activa);

CREATE INDEX IF NOT EXISTS ix_evaluacion_antifraude_transaccion_id
  ON nucleo_financiero.evaluacion_antifraude (transaccion_id);

CREATE INDEX IF NOT EXISTS ix_evaluacion_antifraude_cuenta_billetera_id
  ON nucleo_financiero.evaluacion_antifraude (cuenta_billetera_id);

CREATE INDEX IF NOT EXISTS ix_evaluacion_antifraude_puntaje_riesgo
  ON nucleo_financiero.evaluacion_antifraude (puntaje_riesgo);

CREATE INDEX IF NOT EXISTS ix_evaluacion_antifraude_decision
  ON nucleo_financiero.evaluacion_antifraude (decision);

CREATE INDEX IF NOT EXISTS ix_evaluacion_antifraude_evaluada_en
  ON nucleo_financiero.evaluacion_antifraude (evaluada_en);

CREATE INDEX IF NOT EXISTS ix_bloqueo_saldo_cuenta_billetera_id
  ON nucleo_financiero.bloqueo_saldo (cuenta_billetera_id);

CREATE INDEX IF NOT EXISTS ix_bloqueo_saldo_estado
  ON nucleo_financiero.bloqueo_saldo (estado);

CREATE INDEX IF NOT EXISTS ix_estado_cuenta_billetera_cuenta_billetera_id
  ON nucleo_financiero.estado_cuenta_billetera (cuenta_billetera_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_estado_cuenta_billetera_cuenta_billetera_id_periodo_33629d
  ON nucleo_financiero.estado_cuenta_billetera (cuenta_billetera_id, periodo_hasta, periodo_desde);

CREATE INDEX IF NOT EXISTS ix_certificado_saldo_cuenta_billetera_id
  ON nucleo_financiero.certificado_saldo (cuenta_billetera_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_certificado_saldo_folio
  ON nucleo_financiero.certificado_saldo (folio);

CREATE UNIQUE INDEX IF NOT EXISTS uq_solicitud_cierre_billetera_cuenta_billetera_id
  ON nucleo_financiero.solicitud_cierre_billetera (cuenta_billetera_id);

CREATE INDEX IF NOT EXISTS ix_solicitud_cierre_billetera_estado
  ON nucleo_financiero.solicitud_cierre_billetera (estado);
