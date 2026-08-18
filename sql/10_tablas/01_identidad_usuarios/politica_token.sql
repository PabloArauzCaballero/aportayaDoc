-- politica_token · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: PoliticaToken
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.politica_token (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  proposito                          VARCHAR(35) NOT NULL,
  ttl_segundos                       INTEGER NOT NULL,
  longitud_codigo                    SMALLINT NOT NULL,
  max_intentos_validacion            SMALLINT NOT NULL,
  max_reenvios_por_hora              SMALLINT NOT NULL,
  cooldown_reenvio_segundos          INTEGER NOT NULL,
  max_emisiones_por_dia              SMALLINT NOT NULL,
  canales_permitidos                 VARCHAR(120) NOT NULL,
  exige_dispositivo_conocido         BOOLEAN DEFAULT FALSE NOT NULL,
  invalida_anteriores                BOOLEAN DEFAULT FALSE NOT NULL,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_politica_token PRIMARY KEY (id)
);

COMMENT ON TABLE identidad.politica_token IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.politica_token.id IS 'PK';
COMMENT ON COLUMN identidad.politica_token.proposito IS 'UQ+vigente_desde';
