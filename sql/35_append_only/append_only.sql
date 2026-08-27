-- R-AUD-01 · tablas append-only: sin UPDATE ni DELETE.
-- Generado por scripts/generar_ddl.py desde la lista APPEND_ONLY del modelo:
-- agregar una tabla a esa lista alcanza para que quede sellada.

CREATE OR REPLACE FUNCTION fn_aud_bloquear_mutacion() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'R-AUD-01: % es append-only; corrija con el movimiento inverso',
        TG_TABLE_NAME;
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tg_abono_recuperacion_append_only ON garantia.abono_recuperacion;
CREATE TRIGGER tg_abono_recuperacion_append_only
  BEFORE UPDATE OR DELETE ON garantia.abono_recuperacion
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_acta_comite_append_only ON cumplimiento.acta_comite;
CREATE TRIGGER tg_acta_comite_append_only
  BEFORE UPDATE OR DELETE ON cumplimiento.acta_comite
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_asiento_contable_append_only ON nucleo_financiero.asiento_contable;
CREATE TRIGGER tg_asiento_contable_append_only
  BEFORE UPDATE OR DELETE ON nucleo_financiero.asiento_contable
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_bitacora_evento_append_only ON comun.bitacora_evento;
CREATE TRIGGER tg_bitacora_evento_append_only
  BEFORE UPDATE OR DELETE ON comun.bitacora_evento
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_cierre_periodo_contable_append_only ON erp.cierre_periodo_contable;
CREATE TRIGGER tg_cierre_periodo_contable_append_only
  BEFORE UPDATE OR DELETE ON erp.cierre_periodo_contable
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_clic_anuncio_append_only ON publicidad.clic_anuncio;
CREATE TRIGGER tg_clic_anuncio_append_only
  BEFORE UPDATE OR DELETE ON publicidad.clic_anuncio
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_cobro_cuenta_por_cobrar_append_only ON erp.cobro_cuenta_por_cobrar;
CREATE TRIGGER tg_cobro_cuenta_por_cobrar_append_only
  BEFORE UPDATE OR DELETE ON erp.cobro_cuenta_por_cobrar
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_conversion_anuncio_append_only ON publicidad.conversion_anuncio;
CREATE TRIGGER tg_conversion_anuncio_append_only
  BEFORE UPDATE OR DELETE ON publicidad.conversion_anuncio
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_cuenta_por_cobrar_append_only ON erp.cuenta_por_cobrar;
CREATE TRIGGER tg_cuenta_por_cobrar_append_only
  BEFORE UPDATE OR DELETE ON erp.cuenta_por_cobrar
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_depreciacion_activo_append_only ON erp.depreciacion_activo;
CREATE TRIGGER tg_depreciacion_activo_append_only
  BEFORE UPDATE OR DELETE ON erp.depreciacion_activo
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_devengo_comision_append_only ON tarifas.devengo_comision;
CREATE TRIGGER tg_devengo_comision_append_only
  BEFORE UPDATE OR DELETE ON tarifas.devengo_comision
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_estado_financiero_generado_append_only ON erp.estado_financiero_generado;
CREATE TRIGGER tg_estado_financiero_generado_append_only
  BEFORE UPDATE OR DELETE ON erp.estado_financiero_generado
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_evento_reputacion_append_only ON transparencia.evento_reputacion;
CREATE TRIGGER tg_evento_reputacion_append_only
  BEFORE UPDATE OR DELETE ON transparencia.evento_reputacion
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_evento_riesgo_operativo_append_only ON cumplimiento.evento_riesgo_operativo;
CREATE TRIGGER tg_evento_riesgo_operativo_append_only
  BEFORE UPDATE OR DELETE ON cumplimiento.evento_riesgo_operativo
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_factura_proveedor_append_only ON erp.factura_proveedor;
CREATE TRIGGER tg_factura_proveedor_append_only
  BEFORE UPDATE OR DELETE ON erp.factura_proveedor
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_factura_publicidad_append_only ON publicidad.factura_publicidad;
CREATE TRIGGER tg_factura_publicidad_append_only
  BEFORE UPDATE OR DELETE ON publicidad.factura_publicidad
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_historial_estado_incumplimiento_append_only ON garantia.historial_estado_incumplimiento;
CREATE TRIGGER tg_historial_estado_incumplimiento_append_only
  BEFORE UPDATE OR DELETE ON garantia.historial_estado_incumplimiento
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_impresion_anuncio_append_only ON publicidad.impresion_anuncio;
CREATE TRIGGER tg_impresion_anuncio_append_only
  BEFORE UPDATE OR DELETE ON publicidad.impresion_anuncio
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_indicador_kpi_append_only ON auditoria.indicador_kpi;
CREATE TRIGGER tg_indicador_kpi_append_only
  BEFORE UPDATE OR DELETE ON auditoria.indicador_kpi
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_movimiento_billetera_append_only ON nucleo_financiero.movimiento_billetera;
CREATE TRIGGER tg_movimiento_billetera_append_only
  BEFORE UPDATE OR DELETE ON nucleo_financiero.movimiento_billetera
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_movimiento_contable_append_only ON nucleo_financiero.movimiento_contable;
CREATE TRIGGER tg_movimiento_contable_append_only
  BEFORE UPDATE OR DELETE ON nucleo_financiero.movimiento_contable
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_movimiento_custodia_append_only ON nucleo_financiero.movimiento_custodia;
CREATE TRIGGER tg_movimiento_custodia_append_only
  BEFORE UPDATE OR DELETE ON nucleo_financiero.movimiento_custodia
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_movimiento_fondo_append_only ON garantia.movimiento_fondo;
CREATE TRIGGER tg_movimiento_fondo_append_only
  BEFORE UPDATE OR DELETE ON garantia.movimiento_fondo
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_pago_a_proveedor_append_only ON erp.pago_a_proveedor;
CREATE TRIGGER tg_pago_a_proveedor_append_only
  BEFORE UPDATE OR DELETE ON erp.pago_a_proveedor
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_registro_acceso_datos_append_only ON comun.registro_acceso_datos;
CREATE TRIGGER tg_registro_acceso_datos_append_only
  BEFORE UPDATE OR DELETE ON comun.registro_acceso_datos
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_registro_incumplimiento_append_only ON garantia.registro_incumplimiento;
CREATE TRIGGER tg_registro_incumplimiento_append_only
  BEFORE UPDATE OR DELETE ON garantia.registro_incumplimiento
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_registro_operacion_relevante_append_only ON cumplimiento.registro_operacion_relevante;
CREATE TRIGGER tg_registro_operacion_relevante_append_only
  BEFORE UPDATE OR DELETE ON cumplimiento.registro_operacion_relevante
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_registro_sellado_append_only ON transparencia.registro_sellado;
CREATE TRIGGER tg_registro_sellado_append_only
  BEFORE UPDATE OR DELETE ON transparencia.registro_sellado
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_saldo_diario_billetera_append_only ON nucleo_financiero.saldo_diario_billetera;
CREATE TRIGGER tg_saldo_diario_billetera_append_only
  BEFORE UPDATE OR DELETE ON nucleo_financiero.saldo_diario_billetera
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();

DROP TRIGGER IF EXISTS tg_transaccion_billetera_append_only ON nucleo_financiero.transaccion_billetera;
CREATE TRIGGER tg_transaccion_billetera_append_only
  BEFORE UPDATE OR DELETE ON nucleo_financiero.transaccion_billetera
  FOR EACH ROW EXECUTE FUNCTION fn_aud_bloquear_mutacion();
