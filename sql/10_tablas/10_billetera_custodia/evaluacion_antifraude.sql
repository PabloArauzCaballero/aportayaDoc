-- evaluacion_antifraude · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: EvaluacionAntifraude
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.evaluacion_antifraude (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  transaccion_id                     UUID,
  cuenta_billetera_id                UUID NOT NULL,
  revisada_por                       UUID,
  motor_version                      VARCHAR(20) NOT NULL,
  puntaje_riesgo                     NUMERIC(5,2) NOT NULL,
  decision                           VARCHAR(20) NOT NULL,
  reglas_disparadas                  JSONB NOT NULL,
  latencia_ms                        INTEGER NOT NULL,
  evaluada_en                        TIMESTAMPTZ DEFAULT now() NOT NULL,
  revisada_en                        TIMESTAMPTZ,
  CONSTRAINT pk_evaluacion_antifraude PRIMARY KEY (id),
  CONSTRAINT ck_evaluacion_antifraude_decision CHECK (decision IN ('DESAFIAR_MFA', 'PERMITIR', 'RECHAZAR', 'REVISAR'))
);

COMMENT ON TABLE nucleo_financiero.evaluacion_antifraude IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.evaluacion_antifraude.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.evaluacion_antifraude.transaccion_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN nucleo_financiero.evaluacion_antifraude.cuenta_billetera_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.evaluacion_antifraude.revisada_por IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.evaluacion_antifraude.puntaje_riesgo IS 'IDX';
COMMENT ON COLUMN nucleo_financiero.evaluacion_antifraude.decision IS 'CK, IDX';
COMMENT ON COLUMN nucleo_financiero.evaluacion_antifraude.evaluada_en IS 'IDX';
COMMENT ON COLUMN nucleo_financiero.evaluacion_antifraude.revisada_en IS 'NULL';
