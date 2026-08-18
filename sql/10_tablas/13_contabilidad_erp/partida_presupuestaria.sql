-- partida_presupuestaria · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: PartidaPresupuestaria
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.partida_presupuestaria (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  presupuesto_id                     UUID NOT NULL,
  cuenta_contable_id                 UUID NOT NULL,
  periodo_contable_id                UUID NOT NULL,
  monto_presupuestado                NUMERIC(14,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  monto_ejecutado                    NUMERIC(14,2) DEFAULT 0 NOT NULL,
  CONSTRAINT pk_partida_presupuestaria PRIMARY KEY (id),
  CONSTRAINT ck_partida_presupuestaria_monto_presupuestado CHECK (monto_presupuestado > 0)
);

COMMENT ON TABLE erp.partida_presupuestaria IS 'Módulo 13 — Contabilidad Financiera y ERP. Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.partida_presupuestaria.id IS 'PK';
COMMENT ON COLUMN erp.partida_presupuestaria.presupuesto_id IS 'FK, IDX';
COMMENT ON COLUMN erp.partida_presupuestaria.cuenta_contable_id IS 'FK, IDX, M3';
COMMENT ON COLUMN erp.partida_presupuestaria.periodo_contable_id IS 'FK, IDX';
COMMENT ON COLUMN erp.partida_presupuestaria.monto_presupuestado IS 'CK: > 0';
