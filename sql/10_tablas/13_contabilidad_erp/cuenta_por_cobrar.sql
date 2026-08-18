-- cuenta_por_cobrar · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: CuentaPorCobrar
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.cuenta_por_cobrar (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  origen_tipo                        VARCHAR(20) NOT NULL,
  origen_id                          UUID NOT NULL,
  tercero_comercial_id               UUID,
  monto                              NUMERIC(14,2) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  monto_cobrado                      NUMERIC(14,2) DEFAULT 0 NOT NULL,
  saldo_pendiente                    NUMERIC(14,2),
  fecha_vencimiento                  DATE NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  CONSTRAINT pk_cuenta_por_cobrar PRIMARY KEY (id),
  CONSTRAINT ck_cuenta_por_cobrar_origen_tipo CHECK (origen_tipo IN ('FACTURA_PUBLICIDAD', 'OTRO', 'VENTA_SERVICIO')),
  CONSTRAINT ck_cuenta_por_cobrar_monto CHECK (monto > 0),
  CONSTRAINT ck_cuenta_por_cobrar_estado CHECK (estado IN ('COBRADA', 'COBRADA_PARCIAL', 'INCOBRABLE', 'PENDIENTE'))
);

COMMENT ON TABLE erp.cuenta_por_cobrar IS 'Módulo 13 — Contabilidad Financiera y ERP. [append-only] Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.cuenta_por_cobrar.id IS 'PK';
COMMENT ON COLUMN erp.cuenta_por_cobrar.origen_tipo IS 'CK, IDX';
COMMENT ON COLUMN erp.cuenta_por_cobrar.origen_id IS 'IDX, polimorfica';
COMMENT ON COLUMN erp.cuenta_por_cobrar.tercero_comercial_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN erp.cuenta_por_cobrar.monto IS 'CK: > 0';
COMMENT ON COLUMN erp.cuenta_por_cobrar.saldo_pendiente IS 'GENERATED';
COMMENT ON COLUMN erp.cuenta_por_cobrar.estado IS 'CK, IDX';
