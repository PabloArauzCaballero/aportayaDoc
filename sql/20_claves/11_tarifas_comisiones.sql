-- Claves foráneas del módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- Generado por scripts/generar_ddl.py — no editar a mano.
-- Se aplican después de crear todas las tablas: el modelo tiene
-- referencias circulares entre módulos.

ALTER TABLE tarifas.aplicacion_promocion
  ADD CONSTRAINT fk_aplicacion_promocion_campana_id
  FOREIGN KEY (campana_id) REFERENCES tarifas.campana_promocional (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.aplicacion_promocion
  ADD CONSTRAINT fk_aplicacion_promocion_devengo_id
  FOREIGN KEY (devengo_id) REFERENCES tarifas.devengo_comision (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.asignacion_tarifario
  ADD CONSTRAINT fk_asignacion_tarifario_autorizado_por
  FOREIGN KEY (autorizado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.asignacion_tarifario
  ADD CONSTRAINT fk_asignacion_tarifario_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.asignacion_tarifario
  ADD CONSTRAINT fk_asignacion_tarifario_segmento_id
  FOREIGN KEY (segmento_id) REFERENCES tarifas.segmento_comercial (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.asignacion_tarifario
  ADD CONSTRAINT fk_asignacion_tarifario_tarifario_id
  FOREIGN KEY (tarifario_id) REFERENCES catalogo.tarifario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.asignacion_tarifario
  ADD CONSTRAINT fk_asignacion_tarifario_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.calculo_impuesto
  ADD CONSTRAINT fk_calculo_impuesto_devengo_id
  FOREIGN KEY (devengo_id) REFERENCES tarifas.devengo_comision (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.calculo_impuesto
  ADD CONSTRAINT fk_calculo_impuesto_impuesto_id
  FOREIGN KEY (impuesto_id) REFERENCES catalogo.impuesto (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.cambio_tarifario
  ADD CONSTRAINT fk_cambio_tarifario_aprobado_por
  FOREIGN KEY (aprobado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.cambio_tarifario
  ADD CONSTRAINT fk_cambio_tarifario_tarifario_anterior_id
  FOREIGN KEY (tarifario_anterior_id) REFERENCES catalogo.tarifario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.cambio_tarifario
  ADD CONSTRAINT fk_cambio_tarifario_tarifario_nuevo_id
  FOREIGN KEY (tarifario_nuevo_id) REFERENCES catalogo.tarifario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.campana_promocional
  ADD CONSTRAINT fk_campana_promocional_aprobada_por
  FOREIGN KEY (aprobada_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.cargo_comision
  ADD CONSTRAINT fk_cargo_comision_deduccion_entrega_id
  FOREIGN KEY (deduccion_entrega_id) REFERENCES entregas.deduccion_entrega (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.cargo_comision
  ADD CONSTRAINT fk_cargo_comision_devengo_id
  FOREIGN KEY (devengo_id) REFERENCES tarifas.devengo_comision (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.cargo_comision
  ADD CONSTRAINT fk_cargo_comision_obligacion_id
  FOREIGN KEY (obligacion_id) REFERENCES aportes.obligacion_aporte (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.cargo_comision
  ADD CONSTRAINT fk_cargo_comision_transaccion_id
  FOREIGN KEY (transaccion_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.concepto_tarifa
  ADD CONSTRAINT fk_concepto_tarifa_cuenta_ingreso_id
  FOREIGN KEY (cuenta_ingreso_id) REFERENCES nucleo_financiero.cuenta_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.concepto_tarifa
  ADD CONSTRAINT fk_concepto_tarifa_hecho_generador_id
  FOREIGN KEY (hecho_generador_id) REFERENCES tarifas.catalogo_hecho_generador (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.concepto_tarifa
  ADD CONSTRAINT fk_concepto_tarifa_politica_redondeo_id
  FOREIGN KEY (politica_redondeo_id) REFERENCES tarifas.politica_redondeo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.concepto_tarifa
  ADD CONSTRAINT fk_concepto_tarifa_tarifario_id
  FOREIGN KEY (tarifario_id) REFERENCES catalogo.tarifario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.costo_proveedor_operacion
  ADD CONSTRAINT fk_costo_proveedor_operacion_liquidacion_ingresos_id
  FOREIGN KEY (liquidacion_ingresos_id) REFERENCES tarifas.liquidacion_ingresos (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.costo_proveedor_operacion
  ADD CONSTRAINT fk_costo_proveedor_operacion_proveedor_id
  FOREIGN KEY (proveedor_id) REFERENCES aportes.proveedor_pago (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.costo_proveedor_operacion
  ADD CONSTRAINT fk_costo_proveedor_operacion_transaccion_id
  FOREIGN KEY (transaccion_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.cotizacion_comision
  ADD CONSTRAINT fk_cotizacion_comision_concepto_tarifa_id
  FOREIGN KEY (concepto_tarifa_id) REFERENCES tarifas.concepto_tarifa (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.cotizacion_comision
  ADD CONSTRAINT fk_cotizacion_comision_tarifario_id
  FOREIGN KEY (tarifario_id) REFERENCES catalogo.tarifario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.cuenta_por_cobrar_comision
  ADD CONSTRAINT fk_cuenta_por_cobrar_comision_devengo_id
  FOREIGN KEY (devengo_id) REFERENCES tarifas.devengo_comision (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.cuenta_por_cobrar_comision
  ADD CONSTRAINT fk_cuenta_por_cobrar_comision_gestion_cobranza_id
  FOREIGN KEY (gestion_cobranza_id) REFERENCES garantia.gestion_cobranza (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.cuenta_por_cobrar_comision
  ADD CONSTRAINT fk_cuenta_por_cobrar_comision_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.datos_facturacion
  ADD CONSTRAINT fk_datos_facturacion_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.devengo_comision
  ADD CONSTRAINT fk_devengo_comision_asiento_contable_id
  FOREIGN KEY (asiento_contable_id) REFERENCES nucleo_financiero.asiento_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.devengo_comision
  ADD CONSTRAINT fk_devengo_comision_concepto_tarifa_id
  FOREIGN KEY (concepto_tarifa_id) REFERENCES tarifas.concepto_tarifa (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.devengo_comision
  ADD CONSTRAINT fk_devengo_comision_cotizacion_id
  FOREIGN KEY (cotizacion_id) REFERENCES tarifas.cotizacion_comision (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.devengo_comision
  ADD CONSTRAINT fk_devengo_comision_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.devengo_comision
  ADD CONSTRAINT fk_devengo_comision_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.devengo_comision
  ADD CONSTRAINT fk_devengo_comision_tarifario_id
  FOREIGN KEY (tarifario_id) REFERENCES catalogo.tarifario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.devengo_comision
  ADD CONSTRAINT fk_devengo_comision_usuario_obligado_id
  FOREIGN KEY (usuario_obligado_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.devolucion_comision
  ADD CONSTRAINT fk_devolucion_comision_autorizada_por
  FOREIGN KEY (autorizada_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.devolucion_comision
  ADD CONSTRAINT fk_devolucion_comision_devengo_id
  FOREIGN KEY (devengo_id) REFERENCES tarifas.devengo_comision (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.devolucion_comision
  ADD CONSTRAINT fk_devolucion_comision_reclamo_id
  FOREIGN KEY (reclamo_id) REFERENCES cumplimiento.reclamo_cliente (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.devolucion_comision
  ADD CONSTRAINT fk_devolucion_comision_transaccion_id
  FOREIGN KEY (transaccion_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.evento_significativo_sin
  ADD CONSTRAINT fk_evento_significativo_sin_registrado_por
  FOREIGN KEY (registrado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.exencion_comision
  ADD CONSTRAINT fk_exencion_comision_autorizada_por
  FOREIGN KEY (autorizada_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.exencion_comision
  ADD CONSTRAINT fk_exencion_comision_concepto_tarifa_id
  FOREIGN KEY (concepto_tarifa_id) REFERENCES tarifas.concepto_tarifa (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.exencion_comision
  ADD CONSTRAINT fk_exencion_comision_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.exencion_comision
  ADD CONSTRAINT fk_exencion_comision_segmento_id
  FOREIGN KEY (segmento_id) REFERENCES tarifas.segmento_comercial (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.exencion_comision
  ADD CONSTRAINT fk_exencion_comision_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.factura_electronica
  ADD CONSTRAINT fk_factura_electronica_datos_facturacion_id
  FOREIGN KEY (datos_facturacion_id) REFERENCES tarifas.datos_facturacion (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.factura_electronica
  ADD CONSTRAINT fk_factura_electronica_devengo_id
  FOREIGN KEY (devengo_id) REFERENCES tarifas.devengo_comision (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.factura_electronica
  ADD CONSTRAINT fk_factura_electronica_evento_significativo_id
  FOREIGN KEY (evento_significativo_id) REFERENCES tarifas.evento_significativo_sin (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.factura_electronica
  ADD CONSTRAINT fk_factura_electronica_lote_envio_sin_id
  FOREIGN KEY (lote_envio_sin_id) REFERENCES tarifas.lote_envio_sin (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.factura_electronica
  ADD CONSTRAINT fk_factura_electronica_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE catalogo.impuesto
  ADD CONSTRAINT fk_impuesto_cuenta_contable_id
  FOREIGN KEY (cuenta_contable_id) REFERENCES nucleo_financiero.cuenta_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.liquidacion_ingresos
  ADD CONSTRAINT fk_liquidacion_ingresos_asiento_contable_id
  FOREIGN KEY (asiento_contable_id) REFERENCES nucleo_financiero.asiento_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.liquidacion_ingresos
  ADD CONSTRAINT fk_liquidacion_ingresos_cerrada_por
  FOREIGN KEY (cerrada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.nota_credito_debito
  ADD CONSTRAINT fk_nota_credito_debito_devolucion_comision_id
  FOREIGN KEY (devolucion_comision_id) REFERENCES tarifas.devolucion_comision (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.nota_credito_debito
  ADD CONSTRAINT fk_nota_credito_debito_factura_id
  FOREIGN KEY (factura_id) REFERENCES tarifas.factura_electronica (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.regla_tarifa
  ADD CONSTRAINT fk_regla_tarifa_concepto_tarifa_id
  FOREIGN KEY (concepto_tarifa_id) REFERENCES tarifas.concepto_tarifa (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.simulacion_tarifa
  ADD CONSTRAINT fk_simulacion_tarifa_ejecutada_por
  FOREIGN KEY (ejecutada_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.simulacion_tarifa
  ADD CONSTRAINT fk_simulacion_tarifa_tarifario_id
  FOREIGN KEY (tarifario_id) REFERENCES catalogo.tarifario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.tarifa_congelada_grupo
  ADD CONSTRAINT fk_tarifa_congelada_grupo_acuerdo_id
  FOREIGN KEY (acuerdo_id) REFERENCES grupos.acuerdo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE tarifas.tarifa_congelada_grupo
  ADD CONSTRAINT fk_tarifa_congelada_grupo_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE tarifas.tarifa_congelada_grupo
  ADD CONSTRAINT fk_tarifa_congelada_grupo_tarifario_id
  FOREIGN KEY (tarifario_id) REFERENCES catalogo.tarifario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE catalogo.tarifario
  ADD CONSTRAINT fk_tarifario_aprobado_por
  FOREIGN KEY (aprobado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE catalogo.tarifario
  ADD CONSTRAINT fk_tarifario_tarifario_anterior_id
  FOREIGN KEY (tarifario_anterior_id) REFERENCES catalogo.tarifario (id) ON DELETE SET NULL ON UPDATE CASCADE;
