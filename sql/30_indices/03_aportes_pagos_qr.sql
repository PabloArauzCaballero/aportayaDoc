-- Índices y restricciones de unicidad del módulo 03 — Aportes, Pagos QR y Conciliación
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE INDEX IF NOT EXISTS ix_obligacion_aporte_grupo_id
  ON aportes.obligacion_aporte (grupo_id);

CREATE INDEX IF NOT EXISTS ix_obligacion_aporte_periodo_id
  ON aportes.obligacion_aporte (periodo_id);

CREATE INDEX IF NOT EXISTS ix_obligacion_aporte_cupo_id
  ON aportes.obligacion_aporte (cupo_id);

CREATE INDEX IF NOT EXISTS ix_obligacion_aporte_participante_id
  ON aportes.obligacion_aporte (participante_id);

CREATE INDEX IF NOT EXISTS ix_obligacion_aporte_estado
  ON aportes.obligacion_aporte (estado);

CREATE INDEX IF NOT EXISTS ix_obligacion_aporte_fecha_vencimiento
  ON aportes.obligacion_aporte (fecha_vencimiento);

CREATE INDEX IF NOT EXISTS ix_plan_regularizacion_participante_id
  ON aportes.plan_regularizacion (participante_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_proveedor_pago_codigo
  ON aportes.proveedor_pago (codigo);

CREATE INDEX IF NOT EXISTS ix_orden_cobro_obligacion_id
  ON aportes.orden_cobro (obligacion_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_orden_cobro_referencia_unica
  ON aportes.orden_cobro (referencia_unica);

CREATE INDEX IF NOT EXISTS ix_orden_cobro_estado
  ON aportes.orden_cobro (estado);

CREATE INDEX IF NOT EXISTS ix_orden_cobro_expira_en
  ON aportes.orden_cobro (expira_en);

CREATE UNIQUE INDEX IF NOT EXISTS uq_qr_cobro_orden_cobro_id
  ON aportes.qr_cobro (orden_cobro_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_enlace_pago_rapido_orden_cobro_id
  ON aportes.enlace_pago_rapido (orden_cobro_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_enlace_pago_rapido_token_id
  ON aportes.enlace_pago_rapido (token_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_enlace_pago_rapido_url_corta
  ON aportes.enlace_pago_rapido (url_corta);

CREATE INDEX IF NOT EXISTS ix_intento_pago_orden_cobro_id
  ON aportes.intento_pago (orden_cobro_id);

CREATE INDEX IF NOT EXISTS ix_pago_obligacion_id
  ON aportes.pago (obligacion_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pago_intento_pago_id
  ON aportes.pago (intento_pago_id);

CREATE INDEX IF NOT EXISTS ix_pago_proveedor_id
  ON aportes.pago (proveedor_id);

CREATE INDEX IF NOT EXISTS ix_pago_estado
  ON aportes.pago (estado);

CREATE INDEX IF NOT EXISTS ix_pago_fecha_hora_pago
  ON aportes.pago (fecha_hora_pago);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pago_proveedor_id_referencia_proveedor
  ON aportes.pago (proveedor_id, referencia_proveedor);

CREATE UNIQUE INDEX IF NOT EXISTS uq_comprobante_manual_pago_id
  ON aportes.comprobante_manual (pago_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_constancia_pago_pago_id
  ON aportes.constancia_pago (pago_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_constancia_pago_codigo_verificacion
  ON aportes.constancia_pago (codigo_verificacion);

CREATE INDEX IF NOT EXISTS ix_reembolso_pago_id
  ON aportes.reembolso (pago_id);

CREATE INDEX IF NOT EXISTS ix_disputa_pago_pago_id
  ON aportes.disputa_pago (pago_id);

CREATE INDEX IF NOT EXISTS ix_movimiento_bancario_extracto_id
  ON aportes.movimiento_bancario (extracto_id);

CREATE INDEX IF NOT EXISTS ix_movimiento_bancario_fecha_movimiento
  ON aportes.movimiento_bancario (fecha_movimiento);

CREATE INDEX IF NOT EXISTS ix_movimiento_bancario_glosa
  ON aportes.movimiento_bancario (glosa);

CREATE UNIQUE INDEX IF NOT EXISTS uq_movimiento_bancario_extracto_id_referencia_banco
  ON aportes.movimiento_bancario (extracto_id, referencia_banco);

CREATE INDEX IF NOT EXISTS ix_movimiento_bancario_conciliado
  ON aportes.movimiento_bancario (conciliado);

CREATE UNIQUE INDEX IF NOT EXISTS uq_conciliacion_pago_id
  ON aportes.conciliacion (pago_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_conciliacion_movimiento_bancario_id
  ON aportes.conciliacion (movimiento_bancario_id);

CREATE INDEX IF NOT EXISTS ix_conciliacion_estado
  ON aportes.conciliacion (estado);

CREATE INDEX IF NOT EXISTS ix_excepcion_conciliacion_conciliacion_id
  ON aportes.excepcion_conciliacion (conciliacion_id);

CREATE INDEX IF NOT EXISTS ix_excepcion_conciliacion_estado
  ON aportes.excepcion_conciliacion (estado);

CREATE INDEX IF NOT EXISTS ix_webhook_pasarela_proveedor_id
  ON aportes.webhook_pasarela (proveedor_id);

CREATE INDEX IF NOT EXISTS ix_webhook_pasarela_recibido_en
  ON aportes.webhook_pasarela (recibido_en);

CREATE INDEX IF NOT EXISTS ix_webhook_pasarela_estado
  ON aportes.webhook_pasarela (estado);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tipo_cambio_moneda_destino_fecha_moneda_origen
  ON catalogo.tipo_cambio (moneda_destino, fecha, moneda_origen);

CREATE INDEX IF NOT EXISTS ix_tipo_cambio_fecha
  ON catalogo.tipo_cambio (fecha);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cuenta_contable_codigo
  ON nucleo_financiero.cuenta_contable (codigo);

CREATE INDEX IF NOT EXISTS ix_cuenta_contable_cuenta_padre_id
  ON nucleo_financiero.cuenta_contable (cuenta_padre_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_asiento_contable_numero
  ON nucleo_financiero.asiento_contable (numero);

CREATE INDEX IF NOT EXISTS ix_asiento_contable_fecha
  ON nucleo_financiero.asiento_contable (fecha);

CREATE INDEX IF NOT EXISTS ix_asiento_contable_origen_id
  ON nucleo_financiero.asiento_contable (origen_id);

CREATE INDEX IF NOT EXISTS ix_asiento_contable_grupo_id
  ON nucleo_financiero.asiento_contable (grupo_id);

CREATE INDEX IF NOT EXISTS ix_asiento_contable_periodo_contable_id
  ON nucleo_financiero.asiento_contable (periodo_contable_id);

CREATE INDEX IF NOT EXISTS ix_movimiento_contable_asiento_id
  ON nucleo_financiero.movimiento_contable (asiento_id);

CREATE INDEX IF NOT EXISTS ix_movimiento_contable_cuenta_id
  ON nucleo_financiero.movimiento_contable (cuenta_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cierre_diario_fecha
  ON nucleo_financiero.cierre_diario (fecha);
