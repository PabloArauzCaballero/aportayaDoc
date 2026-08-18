-- politica_cobertura · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: PoliticaCobertura
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.politica_cobertura (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID,
  porcentaje_constitucion            NUMERIC(5,2) NOT NULL,
  dias_mora_para_activar             SMALLINT NOT NULL,
  porcentaje_maximo_cobertura_por_aporte NUMERIC(5,2) NOT NULL,
  tope_cobertura_por_participante    NUMERIC(14,2) NOT NULL,
  tope_cobertura_por_periodo         NUMERIC(14,2) NOT NULL,
  max_coberturas_por_participante    SMALLINT NOT NULL,
  exige_aval_previo                  BOOLEAN DEFAULT FALSE NOT NULL,
  requiere_aprobacion_manual_desde   NUMERIC(14,2) NOT NULL,
  plazo_recuperacion_dias            SMALLINT NOT NULL,
  tasa_recargo_recuperacion          NUMERIC(5,2) NOT NULL,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_politica_cobertura PRIMARY KEY (id)
);

COMMENT ON TABLE garantia.politica_cobertura IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.politica_cobertura.id IS 'PK';
COMMENT ON COLUMN garantia.politica_cobertura.grupo_id IS 'FK, NULL';
