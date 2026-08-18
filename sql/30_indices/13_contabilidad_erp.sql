-- Índices y restricciones de unicidad del módulo 13 — Contabilidad Financiera y ERP
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE UNIQUE INDEX IF NOT EXISTS uq_ejercicio_fiscal_anio
  ON erp.ejercicio_fiscal (anio);

CREATE INDEX IF NOT EXISTS ix_ejercicio_fiscal_estado
  ON erp.ejercicio_fiscal (estado);

CREATE INDEX IF NOT EXISTS ix_periodo_contable_ejercicio_fiscal_id
  ON erp.periodo_contable (ejercicio_fiscal_id);

CREATE INDEX IF NOT EXISTS ix_periodo_contable_estado
  ON erp.periodo_contable (estado);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cierre_periodo_contable_periodo_contable_id
  ON erp.cierre_periodo_contable (periodo_contable_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_centro_costo_codigo
  ON erp.centro_costo (codigo);

CREATE INDEX IF NOT EXISTS ix_presupuesto_centro_costo_id
  ON erp.presupuesto (centro_costo_id);

CREATE INDEX IF NOT EXISTS ix_presupuesto_ejercicio_fiscal_id
  ON erp.presupuesto (ejercicio_fiscal_id);

CREATE INDEX IF NOT EXISTS ix_presupuesto_estado
  ON erp.presupuesto (estado);

CREATE INDEX IF NOT EXISTS ix_partida_presupuestaria_presupuesto_id
  ON erp.partida_presupuestaria (presupuesto_id);

CREATE INDEX IF NOT EXISTS ix_partida_presupuestaria_cuenta_contable_id
  ON erp.partida_presupuestaria (cuenta_contable_id);

CREATE INDEX IF NOT EXISTS ix_partida_presupuestaria_periodo_contable_id
  ON erp.partida_presupuestaria (periodo_contable_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tercero_comercial_numero_documento
  ON erp.tercero_comercial (numero_documento);

CREATE INDEX IF NOT EXISTS ix_tercero_comercial_cuenta_contable_id
  ON erp.tercero_comercial (cuenta_contable_id);

CREATE INDEX IF NOT EXISTS ix_tercero_comercial_estado
  ON erp.tercero_comercial (estado);

CREATE INDEX IF NOT EXISTS ix_orden_compra_tercero_comercial_id
  ON erp.orden_compra (tercero_comercial_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_orden_compra_numero
  ON erp.orden_compra (numero);

CREATE INDEX IF NOT EXISTS ix_orden_compra_estado
  ON erp.orden_compra (estado);

CREATE INDEX IF NOT EXISTS ix_factura_proveedor_tercero_comercial_id
  ON erp.factura_proveedor (tercero_comercial_id);

CREATE INDEX IF NOT EXISTS ix_factura_proveedor_orden_compra_id
  ON erp.factura_proveedor (orden_compra_id);

CREATE INDEX IF NOT EXISTS ix_factura_proveedor_estado
  ON erp.factura_proveedor (estado);

CREATE INDEX IF NOT EXISTS ix_pago_a_proveedor_factura_proveedor_id
  ON erp.pago_a_proveedor (factura_proveedor_id);

CREATE INDEX IF NOT EXISTS ix_cuenta_por_cobrar_origen_tipo
  ON erp.cuenta_por_cobrar (origen_tipo);

CREATE INDEX IF NOT EXISTS ix_cuenta_por_cobrar_origen_id
  ON erp.cuenta_por_cobrar (origen_id);

CREATE INDEX IF NOT EXISTS ix_cuenta_por_cobrar_tercero_comercial_id
  ON erp.cuenta_por_cobrar (tercero_comercial_id);

CREATE INDEX IF NOT EXISTS ix_cuenta_por_cobrar_estado
  ON erp.cuenta_por_cobrar (estado);

CREATE INDEX IF NOT EXISTS ix_cobro_cuenta_por_cobrar_cuenta_por_cobrar_id
  ON erp.cobro_cuenta_por_cobrar (cuenta_por_cobrar_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_categoria_activo_fijo_codigo
  ON erp.categoria_activo_fijo (codigo);

CREATE INDEX IF NOT EXISTS ix_activo_fijo_categoria_activo_fijo_id
  ON erp.activo_fijo (categoria_activo_fijo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_activo_fijo_codigo_inventario
  ON erp.activo_fijo (codigo_inventario);

CREATE INDEX IF NOT EXISTS ix_activo_fijo_estado
  ON erp.activo_fijo (estado);

CREATE INDEX IF NOT EXISTS ix_depreciacion_activo_activo_fijo_id
  ON erp.depreciacion_activo (activo_fijo_id);

CREATE INDEX IF NOT EXISTS ix_depreciacion_activo_periodo_contable_id
  ON erp.depreciacion_activo (periodo_contable_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_asiento_plantilla_codigo
  ON erp.asiento_plantilla (codigo);

CREATE INDEX IF NOT EXISTS ix_linea_plantilla_asiento_plantilla_id
  ON erp.linea_plantilla_asiento (plantilla_id);

CREATE INDEX IF NOT EXISTS ix_linea_plantilla_asiento_cuenta_contable_id
  ON erp.linea_plantilla_asiento (cuenta_contable_id);

CREATE INDEX IF NOT EXISTS ix_estado_financiero_generado_periodo_contable_id
  ON erp.estado_financiero_generado (periodo_contable_id);
