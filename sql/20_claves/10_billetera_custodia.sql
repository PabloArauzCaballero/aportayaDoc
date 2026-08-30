-- Claves foráneas del módulo 10 — Billetera, Custodia y Dinero Electrónico
-- Generado por scripts/generar_ddl.py — no editar a mano.
-- Se aplican después de crear todas las tablas: el modelo tiene
-- referencias circulares entre módulos.
--
-- Cada una se borra si existe antes de crearse: PostgreSQL no tiene
-- ADD CONSTRAINT IF NOT EXISTS, y sql/aplicar.sql se aplica también
-- sobre una base que ya lo tiene. Borrar y volver a crear —en vez de
-- saltear si ya está— es lo que hace que un ON DELETE cambiado en el
-- modelo quede corregido al reaplicar.

ALTER TABLE nucleo_financiero.bloqueo_saldo DROP CONSTRAINT IF EXISTS fk_bloqueo_saldo_cuenta_billetera_id;
ALTER TABLE nucleo_financiero.bloqueo_saldo
  ADD CONSTRAINT fk_bloqueo_saldo_cuenta_billetera_id
  FOREIGN KEY (cuenta_billetera_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.bloqueo_saldo DROP CONSTRAINT IF EXISTS fk_bloqueo_saldo_levantada_por;
ALTER TABLE nucleo_financiero.bloqueo_saldo
  ADD CONSTRAINT fk_bloqueo_saldo_levantada_por
  FOREIGN KEY (levantada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.bloqueo_saldo DROP CONSTRAINT IF EXISTS fk_bloqueo_saldo_retencion_id;
ALTER TABLE nucleo_financiero.bloqueo_saldo
  ADD CONSTRAINT fk_bloqueo_saldo_retencion_id
  FOREIGN KEY (retencion_id) REFERENCES nucleo_financiero.retencion_saldo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.certificado_saldo DROP CONSTRAINT IF EXISTS fk_certificado_saldo_cuenta_billetera_id;
ALTER TABLE nucleo_financiero.certificado_saldo
  ADD CONSTRAINT fk_certificado_saldo_cuenta_billetera_id
  FOREIGN KEY (cuenta_billetera_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.certificado_saldo DROP CONSTRAINT IF EXISTS fk_certificado_saldo_solicitado_por;
ALTER TABLE nucleo_financiero.certificado_saldo
  ADD CONSTRAINT fk_certificado_saldo_solicitado_por
  FOREIGN KEY (solicitado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.conciliacion_custodia DROP CONSTRAINT IF EXISTS fk_conciliacion_custodia_cierre_diario_id;
ALTER TABLE nucleo_financiero.conciliacion_custodia
  ADD CONSTRAINT fk_conciliacion_custodia_cierre_diario_id
  FOREIGN KEY (cierre_diario_id) REFERENCES nucleo_financiero.cierre_diario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.conciliacion_custodia DROP CONSTRAINT IF EXISTS fk_conciliacion_custodia_cuenta_custodia_id;
ALTER TABLE nucleo_financiero.conciliacion_custodia
  ADD CONSTRAINT fk_conciliacion_custodia_cuenta_custodia_id
  FOREIGN KEY (cuenta_custodia_id) REFERENCES nucleo_financiero.cuenta_custodia (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.conciliacion_custodia DROP CONSTRAINT IF EXISTS fk_conciliacion_custodia_ejecutada_por;
ALTER TABLE nucleo_financiero.conciliacion_custodia
  ADD CONSTRAINT fk_conciliacion_custodia_ejecutada_por
  FOREIGN KEY (ejecutada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.consumo_limite DROP CONSTRAINT IF EXISTS fk_consumo_limite_cuenta_billetera_id;
ALTER TABLE nucleo_financiero.consumo_limite
  ADD CONSTRAINT fk_consumo_limite_cuenta_billetera_id
  FOREIGN KEY (cuenta_billetera_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.consumo_limite DROP CONSTRAINT IF EXISTS fk_consumo_limite_limite_id;
ALTER TABLE nucleo_financiero.consumo_limite
  ADD CONSTRAINT fk_consumo_limite_limite_id
  FOREIGN KEY (limite_id) REFERENCES catalogo.limite_operativo_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.cuenta_billetera DROP CONSTRAINT IF EXISTS fk_cuenta_billetera_cuenta_contable_id;
ALTER TABLE nucleo_financiero.cuenta_billetera
  ADD CONSTRAINT fk_cuenta_billetera_cuenta_contable_id
  FOREIGN KEY (cuenta_contable_id) REFERENCES nucleo_financiero.cuenta_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.cuenta_billetera DROP CONSTRAINT IF EXISTS fk_cuenta_billetera_grupo_id;
ALTER TABLE nucleo_financiero.cuenta_billetera
  ADD CONSTRAINT fk_cuenta_billetera_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.cuenta_billetera DROP CONSTRAINT IF EXISTS fk_cuenta_billetera_politica_billetera_id;
ALTER TABLE nucleo_financiero.cuenta_billetera
  ADD CONSTRAINT fk_cuenta_billetera_politica_billetera_id
  FOREIGN KEY (politica_billetera_id) REFERENCES nucleo_financiero.politica_billetera (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.cuenta_billetera DROP CONSTRAINT IF EXISTS fk_cuenta_billetera_usuario_id;
ALTER TABLE nucleo_financiero.cuenta_billetera
  ADD CONSTRAINT fk_cuenta_billetera_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.descuadre_custodia DROP CONSTRAINT IF EXISTS fk_descuadre_custodia_conciliacion_custodia_id;
ALTER TABLE nucleo_financiero.descuadre_custodia
  ADD CONSTRAINT fk_descuadre_custodia_conciliacion_custodia_id
  FOREIGN KEY (conciliacion_custodia_id) REFERENCES nucleo_financiero.conciliacion_custodia (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.descuadre_custodia DROP CONSTRAINT IF EXISTS fk_descuadre_custodia_incidente_operativo_id;
ALTER TABLE nucleo_financiero.descuadre_custodia
  ADD CONSTRAINT fk_descuadre_custodia_incidente_operativo_id
  FOREIGN KEY (incidente_operativo_id) REFERENCES auditoria.incidente_operativo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.descuadre_custodia DROP CONSTRAINT IF EXISTS fk_descuadre_custodia_resuelto_por;
ALTER TABLE nucleo_financiero.descuadre_custodia
  ADD CONSTRAINT fk_descuadre_custodia_resuelto_por
  FOREIGN KEY (resuelto_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.estado_cuenta_billetera DROP CONSTRAINT IF EXISTS fk_estado_cuenta_billetera_cuenta_billetera_id;
ALTER TABLE nucleo_financiero.estado_cuenta_billetera
  ADD CONSTRAINT fk_estado_cuenta_billetera_cuenta_billetera_id
  FOREIGN KEY (cuenta_billetera_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.evaluacion_antifraude DROP CONSTRAINT IF EXISTS fk_evaluacion_antifraude_cuenta_billetera_id;
ALTER TABLE nucleo_financiero.evaluacion_antifraude
  ADD CONSTRAINT fk_evaluacion_antifraude_cuenta_billetera_id
  FOREIGN KEY (cuenta_billetera_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.evaluacion_antifraude DROP CONSTRAINT IF EXISTS fk_evaluacion_antifraude_revisada_por;
ALTER TABLE nucleo_financiero.evaluacion_antifraude
  ADD CONSTRAINT fk_evaluacion_antifraude_revisada_por
  FOREIGN KEY (revisada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.evaluacion_antifraude DROP CONSTRAINT IF EXISTS fk_evaluacion_antifraude_transaccion_id;
ALTER TABLE nucleo_financiero.evaluacion_antifraude
  ADD CONSTRAINT fk_evaluacion_antifraude_transaccion_id
  FOREIGN KEY (transaccion_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.instrumento_fondeo DROP CONSTRAINT IF EXISTS fk_instrumento_fondeo_usuario_id;
ALTER TABLE nucleo_financiero.instrumento_fondeo
  ADD CONSTRAINT fk_instrumento_fondeo_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.movimiento_billetera DROP CONSTRAINT IF EXISTS fk_movimiento_billetera_cuenta_billetera_id;
ALTER TABLE nucleo_financiero.movimiento_billetera
  ADD CONSTRAINT fk_movimiento_billetera_cuenta_billetera_id
  FOREIGN KEY (cuenta_billetera_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.movimiento_billetera DROP CONSTRAINT IF EXISTS fk_movimiento_billetera_transaccion_id;
ALTER TABLE nucleo_financiero.movimiento_billetera
  ADD CONSTRAINT fk_movimiento_billetera_transaccion_id
  FOREIGN KEY (transaccion_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.movimiento_custodia DROP CONSTRAINT IF EXISTS fk_movimiento_custodia_cuenta_custodia_id;
ALTER TABLE nucleo_financiero.movimiento_custodia
  ADD CONSTRAINT fk_movimiento_custodia_cuenta_custodia_id
  FOREIGN KEY (cuenta_custodia_id) REFERENCES nucleo_financiero.cuenta_custodia (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.movimiento_custodia DROP CONSTRAINT IF EXISTS fk_movimiento_custodia_movimiento_bancario_id;
ALTER TABLE nucleo_financiero.movimiento_custodia
  ADD CONSTRAINT fk_movimiento_custodia_movimiento_bancario_id
  FOREIGN KEY (movimiento_bancario_id) REFERENCES aportes.movimiento_bancario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.orden_recarga DROP CONSTRAINT IF EXISTS fk_orden_recarga_cuenta_billetera_id;
ALTER TABLE nucleo_financiero.orden_recarga
  ADD CONSTRAINT fk_orden_recarga_cuenta_billetera_id
  FOREIGN KEY (cuenta_billetera_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.orden_recarga DROP CONSTRAINT IF EXISTS fk_orden_recarga_instrumento_fondeo_id;
ALTER TABLE nucleo_financiero.orden_recarga
  ADD CONSTRAINT fk_orden_recarga_instrumento_fondeo_id
  FOREIGN KEY (instrumento_fondeo_id) REFERENCES nucleo_financiero.instrumento_fondeo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.orden_recarga DROP CONSTRAINT IF EXISTS fk_orden_recarga_pago_id;
ALTER TABLE nucleo_financiero.orden_recarga
  ADD CONSTRAINT fk_orden_recarga_pago_id
  FOREIGN KEY (pago_id) REFERENCES aportes.pago (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.orden_recarga DROP CONSTRAINT IF EXISTS fk_orden_recarga_proveedor_id;
ALTER TABLE nucleo_financiero.orden_recarga
  ADD CONSTRAINT fk_orden_recarga_proveedor_id
  FOREIGN KEY (proveedor_id) REFERENCES aportes.proveedor_pago (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.orden_recarga DROP CONSTRAINT IF EXISTS fk_orden_recarga_transaccion_id;
ALTER TABLE nucleo_financiero.orden_recarga
  ADD CONSTRAINT fk_orden_recarga_transaccion_id
  FOREIGN KEY (transaccion_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.orden_retiro DROP CONSTRAINT IF EXISTS fk_orden_retiro_aprobada_por;
ALTER TABLE nucleo_financiero.orden_retiro
  ADD CONSTRAINT fk_orden_retiro_aprobada_por
  FOREIGN KEY (aprobada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.orden_retiro DROP CONSTRAINT IF EXISTS fk_orden_retiro_cuenta_billetera_id;
ALTER TABLE nucleo_financiero.orden_retiro
  ADD CONSTRAINT fk_orden_retiro_cuenta_billetera_id
  FOREIGN KEY (cuenta_billetera_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.orden_retiro DROP CONSTRAINT IF EXISTS fk_orden_retiro_instrumento_destino_id;
ALTER TABLE nucleo_financiero.orden_retiro
  ADD CONSTRAINT fk_orden_retiro_instrumento_destino_id
  FOREIGN KEY (instrumento_destino_id) REFERENCES nucleo_financiero.instrumento_fondeo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.orden_retiro DROP CONSTRAINT IF EXISTS fk_orden_retiro_proveedor_id;
ALTER TABLE nucleo_financiero.orden_retiro
  ADD CONSTRAINT fk_orden_retiro_proveedor_id
  FOREIGN KEY (proveedor_id) REFERENCES aportes.proveedor_pago (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.orden_retiro DROP CONSTRAINT IF EXISTS fk_orden_retiro_retencion_id;
ALTER TABLE nucleo_financiero.orden_retiro
  ADD CONSTRAINT fk_orden_retiro_retencion_id
  FOREIGN KEY (retencion_id) REFERENCES nucleo_financiero.retencion_saldo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.orden_retiro DROP CONSTRAINT IF EXISTS fk_orden_retiro_solicitada_por;
ALTER TABLE nucleo_financiero.orden_retiro
  ADD CONSTRAINT fk_orden_retiro_solicitada_por
  FOREIGN KEY (solicitada_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.orden_retiro DROP CONSTRAINT IF EXISTS fk_orden_retiro_transaccion_id;
ALTER TABLE nucleo_financiero.orden_retiro
  ADD CONSTRAINT fk_orden_retiro_transaccion_id
  FOREIGN KEY (transaccion_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.politica_billetera DROP CONSTRAINT IF EXISTS fk_politica_billetera_aprobada_por;
ALTER TABLE nucleo_financiero.politica_billetera
  ADD CONSTRAINT fk_politica_billetera_aprobada_por
  FOREIGN KEY (aprobada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.regla_antifraude DROP CONSTRAINT IF EXISTS fk_regla_antifraude_aprobada_por;
ALTER TABLE nucleo_financiero.regla_antifraude
  ADD CONSTRAINT fk_regla_antifraude_aprobada_por
  FOREIGN KEY (aprobada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.respuesta_idempotente DROP CONSTRAINT IF EXISTS fk_respuesta_idempotente_usuario_id;
ALTER TABLE nucleo_financiero.respuesta_idempotente
  ADD CONSTRAINT fk_respuesta_idempotente_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.retencion_saldo DROP CONSTRAINT IF EXISTS fk_retencion_saldo_cuenta_billetera_id;
ALTER TABLE nucleo_financiero.retencion_saldo
  ADD CONSTRAINT fk_retencion_saldo_cuenta_billetera_id
  FOREIGN KEY (cuenta_billetera_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.retencion_saldo DROP CONSTRAINT IF EXISTS fk_retencion_saldo_liberada_por;
ALTER TABLE nucleo_financiero.retencion_saldo
  ADD CONSTRAINT fk_retencion_saldo_liberada_por
  FOREIGN KEY (liberada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.retencion_saldo DROP CONSTRAINT IF EXISTS fk_retencion_saldo_transaccion_origen_id;
ALTER TABLE nucleo_financiero.retencion_saldo
  ADD CONSTRAINT fk_retencion_saldo_transaccion_origen_id
  FOREIGN KEY (transaccion_origen_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.reverso_transaccion DROP CONSTRAINT IF EXISTS fk_reverso_transaccion_autorizada_por;
ALTER TABLE nucleo_financiero.reverso_transaccion
  ADD CONSTRAINT fk_reverso_transaccion_autorizada_por
  FOREIGN KEY (autorizada_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.reverso_transaccion DROP CONSTRAINT IF EXISTS fk_reverso_transaccion_transaccion_original_id;
ALTER TABLE nucleo_financiero.reverso_transaccion
  ADD CONSTRAINT fk_reverso_transaccion_transaccion_original_id
  FOREIGN KEY (transaccion_original_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.reverso_transaccion DROP CONSTRAINT IF EXISTS fk_reverso_transaccion_transaccion_reverso_id;
ALTER TABLE nucleo_financiero.reverso_transaccion
  ADD CONSTRAINT fk_reverso_transaccion_transaccion_reverso_id
  FOREIGN KEY (transaccion_reverso_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.saldo_diario_billetera DROP CONSTRAINT IF EXISTS fk_saldo_diario_billetera_cuenta_billetera_id;
ALTER TABLE nucleo_financiero.saldo_diario_billetera
  ADD CONSTRAINT fk_saldo_diario_billetera_cuenta_billetera_id
  FOREIGN KEY (cuenta_billetera_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.solicitud_cierre_billetera DROP CONSTRAINT IF EXISTS fk_solicitud_cierre_billetera_aprobada_por;
ALTER TABLE nucleo_financiero.solicitud_cierre_billetera
  ADD CONSTRAINT fk_solicitud_cierre_billetera_aprobada_por
  FOREIGN KEY (aprobada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.solicitud_cierre_billetera DROP CONSTRAINT IF EXISTS fk_solicitud_cierre_billetera_cuenta_billetera_id;
ALTER TABLE nucleo_financiero.solicitud_cierre_billetera
  ADD CONSTRAINT fk_solicitud_cierre_billetera_cuenta_billetera_id
  FOREIGN KEY (cuenta_billetera_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.solicitud_cierre_billetera DROP CONSTRAINT IF EXISTS fk_solicitud_cierre_billetera_orden_retiro_id;
ALTER TABLE nucleo_financiero.solicitud_cierre_billetera
  ADD CONSTRAINT fk_solicitud_cierre_billetera_orden_retiro_id
  FOREIGN KEY (orden_retiro_id) REFERENCES nucleo_financiero.orden_retiro (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.transaccion_billetera DROP CONSTRAINT IF EXISTS fk_transaccion_billetera_asiento_contable_id;
ALTER TABLE nucleo_financiero.transaccion_billetera
  ADD CONSTRAINT fk_transaccion_billetera_asiento_contable_id
  FOREIGN KEY (asiento_contable_id) REFERENCES nucleo_financiero.asiento_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.transaccion_billetera DROP CONSTRAINT IF EXISTS fk_transaccion_billetera_dispositivo_id;
ALTER TABLE nucleo_financiero.transaccion_billetera
  ADD CONSTRAINT fk_transaccion_billetera_dispositivo_id
  FOREIGN KEY (dispositivo_id) REFERENCES identidad.dispositivo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.transaccion_billetera DROP CONSTRAINT IF EXISTS fk_transaccion_billetera_grupo_id;
ALTER TABLE nucleo_financiero.transaccion_billetera
  ADD CONSTRAINT fk_transaccion_billetera_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.transaccion_billetera DROP CONSTRAINT IF EXISTS fk_transaccion_billetera_iniciada_por;
ALTER TABLE nucleo_financiero.transaccion_billetera
  ADD CONSTRAINT fk_transaccion_billetera_iniciada_por
  FOREIGN KEY (iniciada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.transaccion_billetera DROP CONSTRAINT IF EXISTS fk_transaccion_billetera_sesion_id;
ALTER TABLE nucleo_financiero.transaccion_billetera
  ADD CONSTRAINT fk_transaccion_billetera_sesion_id
  FOREIGN KEY (sesion_id) REFERENCES identidad.sesion (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.transferencia_p2p DROP CONSTRAINT IF EXISTS fk_transferencia_p2p_cuenta_billetera_destino_id;
ALTER TABLE nucleo_financiero.transferencia_p2p
  ADD CONSTRAINT fk_transferencia_p2p_cuenta_billetera_destino_id
  FOREIGN KEY (cuenta_billetera_destino_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.transferencia_p2p DROP CONSTRAINT IF EXISTS fk_transferencia_p2p_cuenta_billetera_origen_id;
ALTER TABLE nucleo_financiero.transferencia_p2p
  ADD CONSTRAINT fk_transferencia_p2p_cuenta_billetera_origen_id
  FOREIGN KEY (cuenta_billetera_origen_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.transferencia_p2p DROP CONSTRAINT IF EXISTS fk_transferencia_p2p_grupo_id;
ALTER TABLE nucleo_financiero.transferencia_p2p
  ADD CONSTRAINT fk_transferencia_p2p_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.transferencia_p2p DROP CONSTRAINT IF EXISTS fk_transferencia_p2p_obligacion_id;
ALTER TABLE nucleo_financiero.transferencia_p2p
  ADD CONSTRAINT fk_transferencia_p2p_obligacion_id
  FOREIGN KEY (obligacion_id) REFERENCES aportes.obligacion_aporte (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE nucleo_financiero.transferencia_p2p DROP CONSTRAINT IF EXISTS fk_transferencia_p2p_transaccion_id;
ALTER TABLE nucleo_financiero.transferencia_p2p
  ADD CONSTRAINT fk_transferencia_p2p_transaccion_id
  FOREIGN KEY (transaccion_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;
