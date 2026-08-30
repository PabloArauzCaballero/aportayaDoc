-- Claves foráneas del módulo 13 — Contabilidad Financiera y ERP
-- Generado por scripts/generar_ddl.py — no editar a mano.
-- Se aplican después de crear todas las tablas: el modelo tiene
-- referencias circulares entre módulos.
--
-- Cada una se borra si existe antes de crearse: PostgreSQL no tiene
-- ADD CONSTRAINT IF NOT EXISTS, y sql/aplicar.sql se aplica también
-- sobre una base que ya lo tiene. Borrar y volver a crear —en vez de
-- saltear si ya está— es lo que hace que un ON DELETE cambiado en el
-- modelo quede corregido al reaplicar.

ALTER TABLE erp.activo_fijo DROP CONSTRAINT IF EXISTS fk_activo_fijo_categoria_activo_fijo_id;
ALTER TABLE erp.activo_fijo
  ADD CONSTRAINT fk_activo_fijo_categoria_activo_fijo_id
  FOREIGN KEY (categoria_activo_fijo_id) REFERENCES erp.categoria_activo_fijo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.activo_fijo DROP CONSTRAINT IF EXISTS fk_activo_fijo_centro_costo_id;
ALTER TABLE erp.activo_fijo
  ADD CONSTRAINT fk_activo_fijo_centro_costo_id
  FOREIGN KEY (centro_costo_id) REFERENCES erp.centro_costo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.activo_fijo DROP CONSTRAINT IF EXISTS fk_activo_fijo_factura_proveedor_id;
ALTER TABLE erp.activo_fijo
  ADD CONSTRAINT fk_activo_fijo_factura_proveedor_id
  FOREIGN KEY (factura_proveedor_id) REFERENCES erp.factura_proveedor (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.asiento_plantilla DROP CONSTRAINT IF EXISTS fk_asiento_plantilla_creada_por;
ALTER TABLE erp.asiento_plantilla
  ADD CONSTRAINT fk_asiento_plantilla_creada_por
  FOREIGN KEY (creada_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.categoria_activo_fijo DROP CONSTRAINT IF EXISTS fk_categoria_activo_fijo_cuenta_activo_id;
ALTER TABLE erp.categoria_activo_fijo
  ADD CONSTRAINT fk_categoria_activo_fijo_cuenta_activo_id
  FOREIGN KEY (cuenta_activo_id) REFERENCES nucleo_financiero.cuenta_contable (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.categoria_activo_fijo DROP CONSTRAINT IF EXISTS fk_categoria_activo_fijo_cuenta_depreciacion_id;
ALTER TABLE erp.categoria_activo_fijo
  ADD CONSTRAINT fk_categoria_activo_fijo_cuenta_depreciacion_id
  FOREIGN KEY (cuenta_depreciacion_id) REFERENCES nucleo_financiero.cuenta_contable (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.categoria_activo_fijo DROP CONSTRAINT IF EXISTS fk_categoria_activo_fijo_cuenta_gasto_depreciacion_id;
ALTER TABLE erp.categoria_activo_fijo
  ADD CONSTRAINT fk_categoria_activo_fijo_cuenta_gasto_depreciacion_id
  FOREIGN KEY (cuenta_gasto_depreciacion_id) REFERENCES nucleo_financiero.cuenta_contable (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.cierre_periodo_contable DROP CONSTRAINT IF EXISTS fk_cierre_periodo_contable_cerrado_por;
ALTER TABLE erp.cierre_periodo_contable
  ADD CONSTRAINT fk_cierre_periodo_contable_cerrado_por
  FOREIGN KEY (cerrado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.cierre_periodo_contable DROP CONSTRAINT IF EXISTS fk_cierre_periodo_contable_periodo_contable_id;
ALTER TABLE erp.cierre_periodo_contable
  ADD CONSTRAINT fk_cierre_periodo_contable_periodo_contable_id
  FOREIGN KEY (periodo_contable_id) REFERENCES erp.periodo_contable (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.cobro_cuenta_por_cobrar DROP CONSTRAINT IF EXISTS fk_cobro_cuenta_por_cobrar_asiento_contable_id;
ALTER TABLE erp.cobro_cuenta_por_cobrar
  ADD CONSTRAINT fk_cobro_cuenta_por_cobrar_asiento_contable_id
  FOREIGN KEY (asiento_contable_id) REFERENCES nucleo_financiero.asiento_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.cobro_cuenta_por_cobrar DROP CONSTRAINT IF EXISTS fk_cobro_cuenta_por_cobrar_cuenta_por_cobrar_id;
ALTER TABLE erp.cobro_cuenta_por_cobrar
  ADD CONSTRAINT fk_cobro_cuenta_por_cobrar_cuenta_por_cobrar_id
  FOREIGN KEY (cuenta_por_cobrar_id) REFERENCES erp.cuenta_por_cobrar (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.cuenta_por_cobrar DROP CONSTRAINT IF EXISTS fk_cuenta_por_cobrar_tercero_comercial_id;
ALTER TABLE erp.cuenta_por_cobrar
  ADD CONSTRAINT fk_cuenta_por_cobrar_tercero_comercial_id
  FOREIGN KEY (tercero_comercial_id) REFERENCES erp.tercero_comercial (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.depreciacion_activo DROP CONSTRAINT IF EXISTS fk_depreciacion_activo_activo_fijo_id;
ALTER TABLE erp.depreciacion_activo
  ADD CONSTRAINT fk_depreciacion_activo_activo_fijo_id
  FOREIGN KEY (activo_fijo_id) REFERENCES erp.activo_fijo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.depreciacion_activo DROP CONSTRAINT IF EXISTS fk_depreciacion_activo_asiento_contable_id;
ALTER TABLE erp.depreciacion_activo
  ADD CONSTRAINT fk_depreciacion_activo_asiento_contable_id
  FOREIGN KEY (asiento_contable_id) REFERENCES nucleo_financiero.asiento_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.depreciacion_activo DROP CONSTRAINT IF EXISTS fk_depreciacion_activo_periodo_contable_id;
ALTER TABLE erp.depreciacion_activo
  ADD CONSTRAINT fk_depreciacion_activo_periodo_contable_id
  FOREIGN KEY (periodo_contable_id) REFERENCES erp.periodo_contable (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.ejercicio_fiscal DROP CONSTRAINT IF EXISTS fk_ejercicio_fiscal_cerrado_por;
ALTER TABLE erp.ejercicio_fiscal
  ADD CONSTRAINT fk_ejercicio_fiscal_cerrado_por
  FOREIGN KEY (cerrado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.estado_financiero_generado DROP CONSTRAINT IF EXISTS fk_estado_financiero_generado_generado_por;
ALTER TABLE erp.estado_financiero_generado
  ADD CONSTRAINT fk_estado_financiero_generado_generado_por
  FOREIGN KEY (generado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.estado_financiero_generado DROP CONSTRAINT IF EXISTS fk_estado_financiero_generado_periodo_contable_id;
ALTER TABLE erp.estado_financiero_generado
  ADD CONSTRAINT fk_estado_financiero_generado_periodo_contable_id
  FOREIGN KEY (periodo_contable_id) REFERENCES erp.periodo_contable (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.factura_proveedor DROP CONSTRAINT IF EXISTS fk_factura_proveedor_aprobada_por;
ALTER TABLE erp.factura_proveedor
  ADD CONSTRAINT fk_factura_proveedor_aprobada_por
  FOREIGN KEY (aprobada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.factura_proveedor DROP CONSTRAINT IF EXISTS fk_factura_proveedor_asiento_contable_id;
ALTER TABLE erp.factura_proveedor
  ADD CONSTRAINT fk_factura_proveedor_asiento_contable_id
  FOREIGN KEY (asiento_contable_id) REFERENCES nucleo_financiero.asiento_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.factura_proveedor DROP CONSTRAINT IF EXISTS fk_factura_proveedor_centro_costo_id;
ALTER TABLE erp.factura_proveedor
  ADD CONSTRAINT fk_factura_proveedor_centro_costo_id
  FOREIGN KEY (centro_costo_id) REFERENCES erp.centro_costo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.factura_proveedor DROP CONSTRAINT IF EXISTS fk_factura_proveedor_orden_compra_id;
ALTER TABLE erp.factura_proveedor
  ADD CONSTRAINT fk_factura_proveedor_orden_compra_id
  FOREIGN KEY (orden_compra_id) REFERENCES erp.orden_compra (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.factura_proveedor DROP CONSTRAINT IF EXISTS fk_factura_proveedor_tercero_comercial_id;
ALTER TABLE erp.factura_proveedor
  ADD CONSTRAINT fk_factura_proveedor_tercero_comercial_id
  FOREIGN KEY (tercero_comercial_id) REFERENCES erp.tercero_comercial (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.linea_plantilla_asiento DROP CONSTRAINT IF EXISTS fk_linea_plantilla_asiento_cuenta_contable_id;
ALTER TABLE erp.linea_plantilla_asiento
  ADD CONSTRAINT fk_linea_plantilla_asiento_cuenta_contable_id
  FOREIGN KEY (cuenta_contable_id) REFERENCES nucleo_financiero.cuenta_contable (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.linea_plantilla_asiento DROP CONSTRAINT IF EXISTS fk_linea_plantilla_asiento_plantilla_id;
ALTER TABLE erp.linea_plantilla_asiento
  ADD CONSTRAINT fk_linea_plantilla_asiento_plantilla_id
  FOREIGN KEY (plantilla_id) REFERENCES erp.asiento_plantilla (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.orden_compra DROP CONSTRAINT IF EXISTS fk_orden_compra_aprobada_por;
ALTER TABLE erp.orden_compra
  ADD CONSTRAINT fk_orden_compra_aprobada_por
  FOREIGN KEY (aprobada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.orden_compra DROP CONSTRAINT IF EXISTS fk_orden_compra_centro_costo_id;
ALTER TABLE erp.orden_compra
  ADD CONSTRAINT fk_orden_compra_centro_costo_id
  FOREIGN KEY (centro_costo_id) REFERENCES erp.centro_costo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.orden_compra DROP CONSTRAINT IF EXISTS fk_orden_compra_tercero_comercial_id;
ALTER TABLE erp.orden_compra
  ADD CONSTRAINT fk_orden_compra_tercero_comercial_id
  FOREIGN KEY (tercero_comercial_id) REFERENCES erp.tercero_comercial (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.pago_a_proveedor DROP CONSTRAINT IF EXISTS fk_pago_a_proveedor_asiento_contable_id;
ALTER TABLE erp.pago_a_proveedor
  ADD CONSTRAINT fk_pago_a_proveedor_asiento_contable_id
  FOREIGN KEY (asiento_contable_id) REFERENCES nucleo_financiero.asiento_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.pago_a_proveedor DROP CONSTRAINT IF EXISTS fk_pago_a_proveedor_autorizado_por;
ALTER TABLE erp.pago_a_proveedor
  ADD CONSTRAINT fk_pago_a_proveedor_autorizado_por
  FOREIGN KEY (autorizado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.pago_a_proveedor DROP CONSTRAINT IF EXISTS fk_pago_a_proveedor_factura_proveedor_id;
ALTER TABLE erp.pago_a_proveedor
  ADD CONSTRAINT fk_pago_a_proveedor_factura_proveedor_id
  FOREIGN KEY (factura_proveedor_id) REFERENCES erp.factura_proveedor (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.partida_presupuestaria DROP CONSTRAINT IF EXISTS fk_partida_presupuestaria_cuenta_contable_id;
ALTER TABLE erp.partida_presupuestaria
  ADD CONSTRAINT fk_partida_presupuestaria_cuenta_contable_id
  FOREIGN KEY (cuenta_contable_id) REFERENCES nucleo_financiero.cuenta_contable (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.partida_presupuestaria DROP CONSTRAINT IF EXISTS fk_partida_presupuestaria_periodo_contable_id;
ALTER TABLE erp.partida_presupuestaria
  ADD CONSTRAINT fk_partida_presupuestaria_periodo_contable_id
  FOREIGN KEY (periodo_contable_id) REFERENCES erp.periodo_contable (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.partida_presupuestaria DROP CONSTRAINT IF EXISTS fk_partida_presupuestaria_presupuesto_id;
ALTER TABLE erp.partida_presupuestaria
  ADD CONSTRAINT fk_partida_presupuestaria_presupuesto_id
  FOREIGN KEY (presupuesto_id) REFERENCES erp.presupuesto (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.periodo_contable DROP CONSTRAINT IF EXISTS fk_periodo_contable_ejercicio_fiscal_id;
ALTER TABLE erp.periodo_contable
  ADD CONSTRAINT fk_periodo_contable_ejercicio_fiscal_id
  FOREIGN KEY (ejercicio_fiscal_id) REFERENCES erp.ejercicio_fiscal (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.presupuesto DROP CONSTRAINT IF EXISTS fk_presupuesto_aprobado_por;
ALTER TABLE erp.presupuesto
  ADD CONSTRAINT fk_presupuesto_aprobado_por
  FOREIGN KEY (aprobado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE erp.presupuesto DROP CONSTRAINT IF EXISTS fk_presupuesto_centro_costo_id;
ALTER TABLE erp.presupuesto
  ADD CONSTRAINT fk_presupuesto_centro_costo_id
  FOREIGN KEY (centro_costo_id) REFERENCES erp.centro_costo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.presupuesto DROP CONSTRAINT IF EXISTS fk_presupuesto_ejercicio_fiscal_id;
ALTER TABLE erp.presupuesto
  ADD CONSTRAINT fk_presupuesto_ejercicio_fiscal_id
  FOREIGN KEY (ejercicio_fiscal_id) REFERENCES erp.ejercicio_fiscal (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE erp.tercero_comercial DROP CONSTRAINT IF EXISTS fk_tercero_comercial_cuenta_contable_id;
ALTER TABLE erp.tercero_comercial
  ADD CONSTRAINT fk_tercero_comercial_cuenta_contable_id
  FOREIGN KEY (cuenta_contable_id) REFERENCES nucleo_financiero.cuenta_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;
