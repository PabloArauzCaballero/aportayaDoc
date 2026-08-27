-- indicador_kpi · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: IndicadorKPI
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.indicador_kpi (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  definicion_indicador_id            UUID NOT NULL,
  codigo                             VARCHAR(40) NOT NULL,
  nombre                             VARCHAR(80) NOT NULL,
  valor                              NUMERIC(16,4) NOT NULL,
  unidad                             VARCHAR(15) NOT NULL,
  dimension                          VARCHAR(20) NOT NULL,
  dimension_id                       UUID,
  periodo                            VARCHAR(10) NOT NULL,
  meta                               NUMERIC(16,4),
  variacion_periodo_anterior         NUMERIC(8,4),
  provisorio                         BOOLEAN DEFAULT FALSE NOT NULL,
  casos                              INTEGER,
  calculado_en                       TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_indicador_kpi PRIMARY KEY (id),
  CONSTRAINT ck_indicador_kpi_dimension CHECK (dimension IN ('GLOBAL', 'POR_GRUPO', 'POR_ORGANIZADOR'))
);

COMMENT ON TABLE auditoria.indicador_kpi IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. [append-only] Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.indicador_kpi.id IS 'PK';
COMMENT ON COLUMN auditoria.indicador_kpi.definicion_indicador_id IS 'FK, IDX';
COMMENT ON COLUMN auditoria.indicador_kpi.codigo IS 'UQ+dimension+dimension_id+periodo+definicion_indicador_id';
COMMENT ON COLUMN auditoria.indicador_kpi.dimension IS 'CK';
COMMENT ON COLUMN auditoria.indicador_kpi.dimension_id IS 'NULL';
COMMENT ON COLUMN auditoria.indicador_kpi.meta IS 'NULL';
COMMENT ON COLUMN auditoria.indicador_kpi.variacion_periodo_anterior IS 'NULL';
COMMENT ON COLUMN auditoria.indicador_kpi.casos IS 'NULL';
