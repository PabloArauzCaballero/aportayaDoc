-- Claves foráneas del módulo 03 — Aportes, Pagos QR y Conciliación
-- Generado por scripts/generar_ddl.py — no editar a mano.
-- Se aplican después de crear todas las tablas: el modelo tiene
-- referencias circulares entre módulos.

ALTER TABLE nucleo_financiero.asiento_contable
  ADD CONSTRAINT fk_asiento_contable_asiento_reversa_id
  FOREIGN KEY (asiento_reversa_id) REFERENCES nucleo_financiero.asiento_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.asiento_contable
  ADD CONSTRAINT fk_asiento_contable_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.asiento_contable
  ADD CONSTRAINT fk_asiento_contable_periodo_contable_id
  FOREIGN KEY (periodo_contable_id) REFERENCES erp.periodo_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.asiento_contable
  ADD CONSTRAINT fk_asiento_contable_registrado_por
  FOREIGN KEY (registrado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.cierre_diario
  ADD CONSTRAINT fk_cierre_diario_cerrado_por
  FOREIGN KEY (cerrado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.comprobante_manual
  ADD CONSTRAINT fk_comprobante_manual_pago_id
  FOREIGN KEY (pago_id) REFERENCES aportes.pago (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.comprobante_manual
  ADD CONSTRAINT fk_comprobante_manual_revisado_por
  FOREIGN KEY (revisado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.comprobante_manual
  ADD CONSTRAINT fk_comprobante_manual_segunda_revision_por
  FOREIGN KEY (segunda_revision_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.conciliacion
  ADD CONSTRAINT fk_conciliacion_conciliado_por
  FOREIGN KEY (conciliado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.conciliacion
  ADD CONSTRAINT fk_conciliacion_movimiento_bancario_id
  FOREIGN KEY (movimiento_bancario_id) REFERENCES aportes.movimiento_bancario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.conciliacion
  ADD CONSTRAINT fk_conciliacion_pago_id
  FOREIGN KEY (pago_id) REFERENCES aportes.pago (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.constancia_pago
  ADD CONSTRAINT fk_constancia_pago_pago_id
  FOREIGN KEY (pago_id) REFERENCES aportes.pago (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.cuenta_contable
  ADD CONSTRAINT fk_cuenta_contable_cuenta_padre_id
  FOREIGN KEY (cuenta_padre_id) REFERENCES nucleo_financiero.cuenta_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.cuenta_contable
  ADD CONSTRAINT fk_cuenta_contable_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.cuenta_contable
  ADD CONSTRAINT fk_cuenta_contable_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.disputa_pago
  ADD CONSTRAINT fk_disputa_pago_pago_id
  FOREIGN KEY (pago_id) REFERENCES aportes.pago (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.enlace_pago_rapido
  ADD CONSTRAINT fk_enlace_pago_rapido_orden_cobro_id
  FOREIGN KEY (orden_cobro_id) REFERENCES aportes.orden_cobro (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.enlace_pago_rapido
  ADD CONSTRAINT fk_enlace_pago_rapido_token_id
  FOREIGN KEY (token_id) REFERENCES identidad.token_verificacion (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.excepcion_conciliacion
  ADD CONSTRAINT fk_excepcion_conciliacion_asignada_a
  FOREIGN KEY (asignada_a) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.excepcion_conciliacion
  ADD CONSTRAINT fk_excepcion_conciliacion_conciliacion_id
  FOREIGN KEY (conciliacion_id) REFERENCES aportes.conciliacion (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.extracto_bancario
  ADD CONSTRAINT fk_extracto_bancario_importado_por
  FOREIGN KEY (importado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.extracto_bancario
  ADD CONSTRAINT fk_extracto_bancario_proveedor_id
  FOREIGN KEY (proveedor_id) REFERENCES aportes.proveedor_pago (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.intento_pago
  ADD CONSTRAINT fk_intento_pago_orden_cobro_id
  FOREIGN KEY (orden_cobro_id) REFERENCES aportes.orden_cobro (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.movimiento_bancario
  ADD CONSTRAINT fk_movimiento_bancario_extracto_id
  FOREIGN KEY (extracto_id) REFERENCES aportes.extracto_bancario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.movimiento_contable
  ADD CONSTRAINT fk_movimiento_contable_asiento_id
  FOREIGN KEY (asiento_id) REFERENCES nucleo_financiero.asiento_contable (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.movimiento_contable
  ADD CONSTRAINT fk_movimiento_contable_cuenta_id
  FOREIGN KEY (cuenta_id) REFERENCES nucleo_financiero.cuenta_contable (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.obligacion_aporte
  ADD CONSTRAINT fk_obligacion_aporte_cupo_id
  FOREIGN KEY (cupo_id) REFERENCES grupos.cupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.obligacion_aporte
  ADD CONSTRAINT fk_obligacion_aporte_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.obligacion_aporte
  ADD CONSTRAINT fk_obligacion_aporte_obligacion_origen_id
  FOREIGN KEY (obligacion_origen_id) REFERENCES aportes.obligacion_aporte (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.obligacion_aporte
  ADD CONSTRAINT fk_obligacion_aporte_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.obligacion_aporte
  ADD CONSTRAINT fk_obligacion_aporte_periodo_id
  FOREIGN KEY (periodo_id) REFERENCES grupos.periodo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.obligacion_aporte
  ADD CONSTRAINT fk_obligacion_aporte_plan_regularizacion_id
  FOREIGN KEY (plan_regularizacion_id) REFERENCES aportes.plan_regularizacion (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.obligacion_aporte
  ADD CONSTRAINT fk_obligacion_aporte_politica_mora_id
  FOREIGN KEY (politica_mora_id) REFERENCES aportes.politica_mora (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.orden_cobro
  ADD CONSTRAINT fk_orden_cobro_obligacion_id
  FOREIGN KEY (obligacion_id) REFERENCES aportes.obligacion_aporte (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.orden_cobro
  ADD CONSTRAINT fk_orden_cobro_proveedor_id
  FOREIGN KEY (proveedor_id) REFERENCES aportes.proveedor_pago (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.pago
  ADD CONSTRAINT fk_pago_intento_pago_id
  FOREIGN KEY (intento_pago_id) REFERENCES aportes.intento_pago (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.pago
  ADD CONSTRAINT fk_pago_obligacion_id
  FOREIGN KEY (obligacion_id) REFERENCES aportes.obligacion_aporte (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.pago
  ADD CONSTRAINT fk_pago_proveedor_id
  FOREIGN KEY (proveedor_id) REFERENCES aportes.proveedor_pago (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.pago
  ADD CONSTRAINT fk_pago_registrado_por
  FOREIGN KEY (registrado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.plan_regularizacion
  ADD CONSTRAINT fk_plan_regularizacion_aprobado_por
  FOREIGN KEY (aprobado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.plan_regularizacion
  ADD CONSTRAINT fk_plan_regularizacion_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.politica_mora
  ADD CONSTRAINT fk_politica_mora_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.qr_cobro
  ADD CONSTRAINT fk_qr_cobro_orden_cobro_id
  FOREIGN KEY (orden_cobro_id) REFERENCES aportes.orden_cobro (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.reembolso
  ADD CONSTRAINT fk_reembolso_aprobado_por
  FOREIGN KEY (aprobado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.reembolso
  ADD CONSTRAINT fk_reembolso_pago_id
  FOREIGN KEY (pago_id) REFERENCES aportes.pago (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.reembolso
  ADD CONSTRAINT fk_reembolso_solicitado_por
  FOREIGN KEY (solicitado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE aportes.webhook_pasarela
  ADD CONSTRAINT fk_webhook_pasarela_pago_id
  FOREIGN KEY (pago_id) REFERENCES aportes.pago (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aportes.webhook_pasarela
  ADD CONSTRAINT fk_webhook_pasarela_proveedor_id
  FOREIGN KEY (proveedor_id) REFERENCES aportes.proveedor_pago (id) ON DELETE RESTRICT ON UPDATE CASCADE;
