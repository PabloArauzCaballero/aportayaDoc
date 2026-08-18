-- respuesta_idempotente · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.respuesta_idempotente (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  operacion                          VARCHAR(40) NOT NULL,
  clave_idempotencia                 VARCHAR(100) NOT NULL,
  hash_solicitud                     VARCHAR(64) NOT NULL,
  codigo_http                        SMALLINT NOT NULL,
  cuerpo_respuesta                   JSONB NOT NULL,
  registrada_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  expira_en                          TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_respuesta_idempotente PRIMARY KEY (id)
);

COMMENT ON TABLE nucleo_financiero.respuesta_idempotente IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.respuesta_idempotente.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.respuesta_idempotente.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.respuesta_idempotente.operacion IS 'UQ+usuario_id+clave_idempotencia';
COMMENT ON COLUMN nucleo_financiero.respuesta_idempotente.expira_en IS 'IDX';
