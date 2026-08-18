-- factor_riesgo_evaluado · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: FactorRiesgoEvaluado
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.factor_riesgo_evaluado (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  matriz_riesgo_id                   UUID NOT NULL,
  dimension                          VARCHAR(20) NOT NULL,
  factor                             VARCHAR(60) NOT NULL,
  valor_observado                    VARCHAR(120) NOT NULL,
  puntaje                            NUMERIC(6,2) NOT NULL,
  evaluado_en                        TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_factor_riesgo_evaluado PRIMARY KEY (id)
);

COMMENT ON TABLE cumplimiento.factor_riesgo_evaluado IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.factor_riesgo_evaluado.id IS 'PK';
COMMENT ON COLUMN cumplimiento.factor_riesgo_evaluado.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.factor_riesgo_evaluado.matriz_riesgo_id IS 'FK';
