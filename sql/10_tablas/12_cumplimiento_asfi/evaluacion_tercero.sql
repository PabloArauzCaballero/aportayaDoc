-- evaluacion_tercero · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: EvaluacionTercero
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.evaluacion_tercero (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  contrato_tercero_id                UUID NOT NULL,
  evaluado_por                       UUID NOT NULL,
  periodo                            CHAR(7) NOT NULL,
  cumplimiento_sla                   NUMERIC(5,2) NOT NULL,
  incidentes_atribuibles             SMALLINT NOT NULL,
  resultado                          VARCHAR(15) NOT NULL,
  acciones_requeridas                VARCHAR(400),
  evaluada_en                        TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_evaluacion_tercero PRIMARY KEY (id),
  CONSTRAINT ck_evaluacion_tercero_resultado CHECK (resultado IN ('CON_OBSERVACIONES', 'INSATISFACTORIO', 'SATISFACTORIO'))
);

COMMENT ON TABLE cumplimiento.evaluacion_tercero IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.evaluacion_tercero.id IS 'PK';
COMMENT ON COLUMN cumplimiento.evaluacion_tercero.contrato_tercero_id IS 'FK, IDX';
COMMENT ON COLUMN cumplimiento.evaluacion_tercero.evaluado_por IS 'FK';
COMMENT ON COLUMN cumplimiento.evaluacion_tercero.periodo IS 'UQ+contrato_tercero_id';
COMMENT ON COLUMN cumplimiento.evaluacion_tercero.resultado IS 'CK';
COMMENT ON COLUMN cumplimiento.evaluacion_tercero.acciones_requeridas IS 'NULL';
