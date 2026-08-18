-- Índices y restricciones de unicidad del módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogo_hecho_generador_codigo
  ON tarifas.catalogo_hecho_generador (codigo);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tarifario_version_codigo
  ON catalogo.tarifario (version, codigo);

CREATE INDEX IF NOT EXISTS ix_tarifario_estado
  ON catalogo.tarifario (estado);

CREATE INDEX IF NOT EXISTS ix_tarifario_vigente_desde
  ON catalogo.tarifario (vigente_desde);

CREATE UNIQUE INDEX IF NOT EXISTS uq_politica_redondeo_codigo
  ON tarifas.politica_redondeo (codigo);

CREATE INDEX IF NOT EXISTS ix_concepto_tarifa_tarifario_id
  ON tarifas.concepto_tarifa (tarifario_id);

CREATE INDEX IF NOT EXISTS ix_concepto_tarifa_hecho_generador_id
  ON tarifas.concepto_tarifa (hecho_generador_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_concepto_tarifa_tarifario_id_codigo
  ON tarifas.concepto_tarifa (tarifario_id, codigo);

CREATE INDEX IF NOT EXISTS ix_concepto_tarifa_activo
  ON tarifas.concepto_tarifa (activo);

CREATE INDEX IF NOT EXISTS ix_regla_tarifa_concepto_tarifa_id
  ON tarifas.regla_tarifa (concepto_tarifa_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_segmento_comercial_codigo
  ON tarifas.segmento_comercial (codigo);

CREATE INDEX IF NOT EXISTS ix_asignacion_tarifario_tarifario_id
  ON tarifas.asignacion_tarifario (tarifario_id);

CREATE INDEX IF NOT EXISTS ix_asignacion_tarifario_ambito
  ON tarifas.asignacion_tarifario (ambito);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tarifa_congelada_grupo_grupo_id
  ON tarifas.tarifa_congelada_grupo (grupo_id);

CREATE INDEX IF NOT EXISTS ix_simulacion_tarifa_tarifario_id
  ON tarifas.simulacion_tarifa (tarifario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cambio_tarifario_tarifario_nuevo_id
  ON tarifas.cambio_tarifario (tarifario_nuevo_id);

CREATE INDEX IF NOT EXISTS ix_cotizacion_comision_concepto_tarifa_id
  ON tarifas.cotizacion_comision (concepto_tarifa_id);

CREATE INDEX IF NOT EXISTS ix_cotizacion_comision_referencia_id
  ON tarifas.cotizacion_comision (referencia_id);

CREATE INDEX IF NOT EXISTS ix_devengo_comision_concepto_tarifa_id
  ON tarifas.devengo_comision (concepto_tarifa_id);

CREATE INDEX IF NOT EXISTS ix_devengo_comision_tarifario_id
  ON tarifas.devengo_comision (tarifario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_devengo_comision_cotizacion_id
  ON tarifas.devengo_comision (cotizacion_id);

CREATE INDEX IF NOT EXISTS ix_devengo_comision_grupo_id
  ON tarifas.devengo_comision (grupo_id);

CREATE INDEX IF NOT EXISTS ix_devengo_comision_usuario_obligado_id
  ON tarifas.devengo_comision (usuario_obligado_id);

CREATE INDEX IF NOT EXISTS ix_devengo_comision_referencia_id
  ON tarifas.devengo_comision (referencia_id);

CREATE INDEX IF NOT EXISTS ix_devengo_comision_estado
  ON tarifas.devengo_comision (estado);

CREATE INDEX IF NOT EXISTS ix_devengo_comision_fecha_devengo
  ON tarifas.devengo_comision (fecha_devengo);

CREATE INDEX IF NOT EXISTS ix_devengo_comision_periodo_contable
  ON tarifas.devengo_comision (periodo_contable);

CREATE INDEX IF NOT EXISTS ix_cargo_comision_devengo_id
  ON tarifas.cargo_comision (devengo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cargo_comision_deduccion_entrega_id
  ON tarifas.cargo_comision (deduccion_entrega_id);

CREATE INDEX IF NOT EXISTS ix_cargo_comision_estado
  ON tarifas.cargo_comision (estado);

CREATE INDEX IF NOT EXISTS ix_exencion_comision_alcance
  ON tarifas.exencion_comision (alcance);

CREATE INDEX IF NOT EXISTS ix_exencion_comision_activa
  ON tarifas.exencion_comision (activa);

CREATE UNIQUE INDEX IF NOT EXISTS uq_campana_promocional_codigo
  ON tarifas.campana_promocional (codigo);

CREATE INDEX IF NOT EXISTS ix_campana_promocional_estado
  ON tarifas.campana_promocional (estado);

CREATE INDEX IF NOT EXISTS ix_aplicacion_promocion_campana_id
  ON tarifas.aplicacion_promocion (campana_id);

CREATE INDEX IF NOT EXISTS ix_aplicacion_promocion_devengo_id
  ON tarifas.aplicacion_promocion (devengo_id);

CREATE INDEX IF NOT EXISTS ix_devolucion_comision_devengo_id
  ON tarifas.devolucion_comision (devengo_id);

CREATE INDEX IF NOT EXISTS ix_devolucion_comision_estado
  ON tarifas.devolucion_comision (estado);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cuenta_por_cobrar_comision_devengo_id
  ON tarifas.cuenta_por_cobrar_comision (devengo_id);

CREATE INDEX IF NOT EXISTS ix_cuenta_por_cobrar_comision_usuario_id
  ON tarifas.cuenta_por_cobrar_comision (usuario_id);

CREATE INDEX IF NOT EXISTS ix_cuenta_por_cobrar_comision_dias_vencido
  ON tarifas.cuenta_por_cobrar_comision (dias_vencido);

CREATE INDEX IF NOT EXISTS ix_cuenta_por_cobrar_comision_estado
  ON tarifas.cuenta_por_cobrar_comision (estado);

CREATE UNIQUE INDEX IF NOT EXISTS uq_impuesto_vigente_desde_codigo
  ON catalogo.impuesto (vigente_desde, codigo);

CREATE INDEX IF NOT EXISTS ix_calculo_impuesto_devengo_id
  ON tarifas.calculo_impuesto (devengo_id);

CREATE INDEX IF NOT EXISTS ix_calculo_impuesto_periodo_fiscal
  ON tarifas.calculo_impuesto (periodo_fiscal);

CREATE INDEX IF NOT EXISTS ix_datos_facturacion_usuario_id
  ON tarifas.datos_facturacion (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_datos_facturacion_usuario_id_numero_documento
  ON tarifas.datos_facturacion (usuario_id, numero_documento);

CREATE INDEX IF NOT EXISTS ix_lote_envio_sin_fecha_envio
  ON tarifas.lote_envio_sin (fecha_envio);

CREATE UNIQUE INDEX IF NOT EXISTS uq_lote_envio_sin_codigo_recepcion
  ON tarifas.lote_envio_sin (codigo_recepcion);

CREATE INDEX IF NOT EXISTS ix_lote_envio_sin_estado
  ON tarifas.lote_envio_sin (estado);

CREATE INDEX IF NOT EXISTS ix_evento_significativo_sin_codigo_evento
  ON tarifas.evento_significativo_sin (codigo_evento);

CREATE INDEX IF NOT EXISTS ix_evento_significativo_sin_fecha_inicio
  ON tarifas.evento_significativo_sin (fecha_inicio);

CREATE INDEX IF NOT EXISTS ix_evento_significativo_sin_plazo_registro
  ON tarifas.evento_significativo_sin (plazo_registro);

CREATE UNIQUE INDEX IF NOT EXISTS uq_evento_significativo_sin_codigo_recepcion_evento
  ON tarifas.evento_significativo_sin (codigo_recepcion_evento);

CREATE INDEX IF NOT EXISTS ix_evento_significativo_sin_estado
  ON tarifas.evento_significativo_sin (estado);

CREATE INDEX IF NOT EXISTS ix_factura_electronica_devengo_id
  ON tarifas.factura_electronica (devengo_id);

CREATE INDEX IF NOT EXISTS ix_factura_electronica_usuario_id
  ON tarifas.factura_electronica (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_factura_electronica_sucursal_punto_venta_numero_factura
  ON tarifas.factura_electronica (sucursal, punto_venta, numero_factura);

CREATE UNIQUE INDEX IF NOT EXISTS uq_factura_electronica_cuf
  ON tarifas.factura_electronica (cuf);

CREATE INDEX IF NOT EXISTS ix_factura_electronica_fecha_emision
  ON tarifas.factura_electronica (fecha_emision);

CREATE INDEX IF NOT EXISTS ix_factura_electronica_estado_fiscal
  ON tarifas.factura_electronica (estado_fiscal);

CREATE INDEX IF NOT EXISTS ix_nota_credito_debito_factura_id
  ON tarifas.nota_credito_debito (factura_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nota_credito_debito_cuf
  ON tarifas.nota_credito_debito (cuf);

CREATE UNIQUE INDEX IF NOT EXISTS uq_liquidacion_ingresos_periodo
  ON tarifas.liquidacion_ingresos (periodo);

CREATE INDEX IF NOT EXISTS ix_liquidacion_ingresos_estado
  ON tarifas.liquidacion_ingresos (estado);

CREATE INDEX IF NOT EXISTS ix_costo_proveedor_operacion_proveedor_id
  ON tarifas.costo_proveedor_operacion (proveedor_id);

CREATE INDEX IF NOT EXISTS ix_costo_proveedor_operacion_periodo
  ON tarifas.costo_proveedor_operacion (periodo);
