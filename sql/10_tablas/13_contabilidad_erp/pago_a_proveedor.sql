-- pago_a_proveedor · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: PagoAProveedor
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.pago_a_proveedor (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  factura_proveedor_id               UUID NOT NULL,
  monto                              NUMERIC(14,2) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  fecha_pago                         TIMESTAMPTZ NOT NULL,
  forma_pago                         VARCHAR(20) NOT NULL,
  autorizado_por                     UUID NOT NULL,
  asiento_contable_id                UUID,
  CONSTRAINT pk_pago_a_proveedor PRIMARY KEY (id),
  CONSTRAINT ck_pago_a_proveedor_monto CHECK (monto > 0),
  CONSTRAINT ck_pago_a_proveedor_forma_pago CHECK (forma_pago IN ('CHEQUE', 'EFECTIVO', 'TRANSFERENCIA'))
);

COMMENT ON TABLE erp.pago_a_proveedor IS 'Módulo 13 — Contabilidad Financiera y ERP. [append-only] Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.pago_a_proveedor.id IS 'PK';
COMMENT ON COLUMN erp.pago_a_proveedor.factura_proveedor_id IS 'FK, IDX';
COMMENT ON COLUMN erp.pago_a_proveedor.monto IS 'CK: > 0';
COMMENT ON COLUMN erp.pago_a_proveedor.forma_pago IS 'CK';
COMMENT ON COLUMN erp.pago_a_proveedor.autorizado_por IS 'FK';
COMMENT ON COLUMN erp.pago_a_proveedor.asiento_contable_id IS 'FK, NULL, M3';
