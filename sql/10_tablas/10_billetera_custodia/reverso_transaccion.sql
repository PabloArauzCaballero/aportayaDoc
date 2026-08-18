-- reverso_transaccion · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: ReversoTransaccion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.reverso_transaccion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  transaccion_original_id            UUID NOT NULL,
  transaccion_reverso_id             UUID,
  autorizada_por                     UUID NOT NULL,
  tipo                               VARCHAR(25) NOT NULL,
  motivo                             VARCHAR(300) NOT NULL,
  monto_reversado                    NUMERIC(16,2) DEFAULT 0 NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  solicitada_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  ejecutada_en                       TIMESTAMPTZ,
  CONSTRAINT pk_reverso_transaccion PRIMARY KEY (id),
  CONSTRAINT ck_reverso_transaccion_tipo CHECK (tipo IN ('ANULACION', 'CONTRACARGO', 'ERROR_OPERATIVO', 'ORDEN_AUTORIDAD')),
  CONSTRAINT ck_reverso_transaccion_estado CHECK (estado IN ('AUTORIZADO', 'EJECUTADO', 'RECHAZADO', 'SOLICITADO'))
);

COMMENT ON TABLE nucleo_financiero.reverso_transaccion IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.reverso_transaccion.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.reverso_transaccion.transaccion_original_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.reverso_transaccion.transaccion_reverso_id IS 'FK, NULL, UQ';
COMMENT ON COLUMN nucleo_financiero.reverso_transaccion.autorizada_por IS 'FK';
COMMENT ON COLUMN nucleo_financiero.reverso_transaccion.tipo IS 'CK';
COMMENT ON COLUMN nucleo_financiero.reverso_transaccion.estado IS 'CK';
COMMENT ON COLUMN nucleo_financiero.reverso_transaccion.ejecutada_en IS 'NULL';
