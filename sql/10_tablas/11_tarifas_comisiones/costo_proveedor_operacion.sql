-- costo_proveedor_operacion · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: CostoProveedorOperacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.costo_proveedor_operacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  proveedor_id                       UUID NOT NULL,
  transaccion_id                     UUID,
  liquidacion_ingresos_id            UUID,
  tipo_operacion                     VARCHAR(25) NOT NULL,
  monto_operacion                    NUMERIC(14,2) DEFAULT 0 NOT NULL,
  costo_fijo                         NUMERIC(10,2) DEFAULT 0 NOT NULL,
  costo_porcentual                   NUMERIC(10,2) DEFAULT 0 NOT NULL,
  costo_total                        NUMERIC(10,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  periodo                            CHAR(7) NOT NULL,
  conciliado_con_factura             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_costo_proveedor_operacion PRIMARY KEY (id),
  CONSTRAINT ck_costo_proveedor_operacion_tipo_operacion CHECK (tipo_operacion IN ('COBRO_QR', 'DESEMBOLSO', 'MENSAJERIA', 'RECARGA', 'RETIRO', 'TRANSFERENCIA'))
);

COMMENT ON TABLE tarifas.costo_proveedor_operacion IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.costo_proveedor_operacion.id IS 'PK';
COMMENT ON COLUMN tarifas.costo_proveedor_operacion.proveedor_id IS 'FK, IDX, M3';
COMMENT ON COLUMN tarifas.costo_proveedor_operacion.transaccion_id IS 'FK, NULL, M10';
COMMENT ON COLUMN tarifas.costo_proveedor_operacion.liquidacion_ingresos_id IS 'FK, NULL';
COMMENT ON COLUMN tarifas.costo_proveedor_operacion.tipo_operacion IS 'CK';
COMMENT ON COLUMN tarifas.costo_proveedor_operacion.periodo IS 'IDX';
