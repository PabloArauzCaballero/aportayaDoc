-- estado_financiero_generado · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: EstadoFinancieroGenerado
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.estado_financiero_generado (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  periodo_contable_id                UUID NOT NULL,
  tipo                               VARCHAR(20) NOT NULL,
  generado_en                        TIMESTAMPTZ NOT NULL,
  generado_por                       UUID NOT NULL,
  datos                              JSONB NOT NULL,
  hash_contenido                     VARCHAR(64) NOT NULL,
  CONSTRAINT pk_estado_financiero_generado PRIMARY KEY (id),
  CONSTRAINT ck_estado_financiero_generado_tipo CHECK (tipo IN ('BALANCE_GENERAL', 'ESTADO_RESULTADOS'))
);

COMMENT ON TABLE erp.estado_financiero_generado IS 'Módulo 13 — Contabilidad Financiera y ERP. [append-only] Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.estado_financiero_generado.id IS 'PK';
COMMENT ON COLUMN erp.estado_financiero_generado.periodo_contable_id IS 'FK, IDX';
COMMENT ON COLUMN erp.estado_financiero_generado.tipo IS 'CK';
COMMENT ON COLUMN erp.estado_financiero_generado.generado_por IS 'FK';
