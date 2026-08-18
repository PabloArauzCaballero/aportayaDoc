-- cierre_periodo_contable · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: CierrePeriodoContable
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.cierre_periodo_contable (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  periodo_contable_id                UUID NOT NULL,
  cerrado_en                         TIMESTAMPTZ NOT NULL,
  cerrado_por                        UUID NOT NULL,
  total_debe                         NUMERIC(18,2) DEFAULT 0 NOT NULL,
  total_haber                        NUMERIC(18,2) DEFAULT 0 NOT NULL,
  diferencia                         NUMERIC(18,2),
  observaciones                      VARCHAR(300),
  CONSTRAINT pk_cierre_periodo_contable PRIMARY KEY (id)
);

COMMENT ON TABLE erp.cierre_periodo_contable IS 'Módulo 13 — Contabilidad Financiera y ERP. [append-only] Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.cierre_periodo_contable.id IS 'PK';
COMMENT ON COLUMN erp.cierre_periodo_contable.periodo_contable_id IS 'FK, UQ';
COMMENT ON COLUMN erp.cierre_periodo_contable.cerrado_por IS 'FK';
COMMENT ON COLUMN erp.cierre_periodo_contable.diferencia IS 'GENERATED';
COMMENT ON COLUMN erp.cierre_periodo_contable.observaciones IS 'NULL';
