-- cobro_cuenta_por_cobrar · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: CobroCuentaPorCobrar
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.cobro_cuenta_por_cobrar (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cuenta_por_cobrar_id               UUID NOT NULL,
  monto                              NUMERIC(14,2) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  fecha_cobro                        TIMESTAMPTZ NOT NULL,
  forma_cobro                        VARCHAR(20) NOT NULL,
  asiento_contable_id                UUID,
  CONSTRAINT pk_cobro_cuenta_por_cobrar PRIMARY KEY (id),
  CONSTRAINT ck_cobro_cuenta_por_cobrar_monto CHECK (monto > 0),
  CONSTRAINT ck_cobro_cuenta_por_cobrar_forma_cobro CHECK (forma_cobro IN ('EFECTIVO', 'QR', 'TARJETA', 'TRANSFERENCIA'))
);

COMMENT ON TABLE erp.cobro_cuenta_por_cobrar IS 'Módulo 13 — Contabilidad Financiera y ERP. [append-only] Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.cobro_cuenta_por_cobrar.id IS 'PK';
COMMENT ON COLUMN erp.cobro_cuenta_por_cobrar.cuenta_por_cobrar_id IS 'FK, IDX';
COMMENT ON COLUMN erp.cobro_cuenta_por_cobrar.monto IS 'CK: > 0';
COMMENT ON COLUMN erp.cobro_cuenta_por_cobrar.forma_cobro IS 'CK';
COMMENT ON COLUMN erp.cobro_cuenta_por_cobrar.asiento_contable_id IS 'FK, NULL, M3';
