-- periodo_contable · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: PeriodoContable
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.periodo_contable (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  ejercicio_fiscal_id                UUID NOT NULL,
  mes                                SMALLINT NOT NULL,
  fecha_inicio                       DATE NOT NULL,
  fecha_fin                          DATE NOT NULL,
  estado                             VARCHAR(10) NOT NULL,
  CONSTRAINT pk_periodo_contable PRIMARY KEY (id),
  CONSTRAINT ck_periodo_contable_mes CHECK (mes BETWEEN 1 AND 12),
  CONSTRAINT ck_periodo_contable_estado CHECK (estado IN ('ABIERTO', 'CERRADO'))
);

COMMENT ON TABLE erp.periodo_contable IS 'Módulo 13 — Contabilidad Financiera y ERP. Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.periodo_contable.id IS 'PK';
COMMENT ON COLUMN erp.periodo_contable.ejercicio_fiscal_id IS 'FK, IDX';
COMMENT ON COLUMN erp.periodo_contable.mes IS 'CK: 1-12';
COMMENT ON COLUMN erp.periodo_contable.estado IS 'CK, IDX';
