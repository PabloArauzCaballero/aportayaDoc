-- regla_antifraude · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: ReglaAntifraude
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.regla_antifraude (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(40) NOT NULL,
  descripcion                        VARCHAR(300) NOT NULL,
  expresion                          JSONB NOT NULL,
  accion                             VARCHAR(20) NOT NULL,
  umbral_puntaje                     NUMERIC(5,2) NOT NULL,
  prioridad                          SMALLINT NOT NULL,
  activa                             BOOLEAN DEFAULT FALSE NOT NULL,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  aprobada_por                       UUID,
  CONSTRAINT pk_regla_antifraude PRIMARY KEY (id),
  CONSTRAINT ck_regla_antifraude_accion CHECK (accion IN ('DESAFIAR_MFA', 'PERMITIR', 'RECHAZAR', 'REVISAR'))
);

COMMENT ON TABLE nucleo_financiero.regla_antifraude IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.regla_antifraude.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.regla_antifraude.codigo IS 'UQ';
COMMENT ON COLUMN nucleo_financiero.regla_antifraude.accion IS 'CK';
COMMENT ON COLUMN nucleo_financiero.regla_antifraude.activa IS 'IDX';
COMMENT ON COLUMN nucleo_financiero.regla_antifraude.aprobada_por IS 'FK, NULL';
