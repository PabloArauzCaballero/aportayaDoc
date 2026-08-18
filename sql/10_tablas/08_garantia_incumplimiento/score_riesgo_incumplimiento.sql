-- score_riesgo_incumplimiento · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: ScoreRiesgoIncumplimiento
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.score_riesgo_incumplimiento (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  grupo_id                           UUID,
  probabilidad_incumplimiento        NUMERIC(5,4) NOT NULL,
  factores_principales               JSONB NOT NULL,
  nivel_riesgo                       VARCHAR(10) NOT NULL,
  accion_sugerida                    VARCHAR(160) NOT NULL,
  calculado_en                       TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_score_riesgo_incumplimiento PRIMARY KEY (id),
  CONSTRAINT ck_score_riesgo_incumplimiento_nivel_riesgo CHECK (nivel_riesgo IN ('ALTO', 'BAJO', 'MEDIO', 'MUY_ALTO'))
);

COMMENT ON TABLE garantia.score_riesgo_incumplimiento IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.score_riesgo_incumplimiento.id IS 'PK';
COMMENT ON COLUMN garantia.score_riesgo_incumplimiento.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.score_riesgo_incumplimiento.grupo_id IS 'FK, NULL';
COMMENT ON COLUMN garantia.score_riesgo_incumplimiento.nivel_riesgo IS 'CK, IDX';
