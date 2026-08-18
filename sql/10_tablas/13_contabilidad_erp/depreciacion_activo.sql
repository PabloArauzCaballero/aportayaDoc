-- depreciacion_activo · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: DepreciacionActivo
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.depreciacion_activo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  activo_fijo_id                     UUID NOT NULL,
  periodo_contable_id                UUID NOT NULL,
  monto                              NUMERIC(14,2) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  asiento_contable_id                UUID,
  calculada_en                       TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_depreciacion_activo PRIMARY KEY (id),
  CONSTRAINT ck_depreciacion_activo_monto CHECK (monto > 0)
);

COMMENT ON TABLE erp.depreciacion_activo IS 'Módulo 13 — Contabilidad Financiera y ERP. [append-only] Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.depreciacion_activo.id IS 'PK';
COMMENT ON COLUMN erp.depreciacion_activo.activo_fijo_id IS 'FK, IDX';
COMMENT ON COLUMN erp.depreciacion_activo.periodo_contable_id IS 'FK, IDX';
COMMENT ON COLUMN erp.depreciacion_activo.monto IS 'CK: > 0';
COMMENT ON COLUMN erp.depreciacion_activo.asiento_contable_id IS 'FK, NULL, M3';
