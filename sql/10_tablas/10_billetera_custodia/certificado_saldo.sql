-- certificado_saldo · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: CertificadoSaldo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.certificado_saldo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cuenta_billetera_id                UUID NOT NULL,
  solicitado_por                     UUID NOT NULL,
  folio                              VARCHAR(30) NOT NULL,
  motivo                             VARCHAR(120) NOT NULL,
  saldo_certificado                  NUMERIC(16,2) DEFAULT 0 NOT NULL,
  fecha_corte                        TIMESTAMPTZ NOT NULL,
  hash_documento                     VARCHAR(64) NOT NULL,
  url_documento                      VARCHAR(255) NOT NULL,
  emitido_en                         TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_certificado_saldo PRIMARY KEY (id)
);

COMMENT ON TABLE nucleo_financiero.certificado_saldo IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.certificado_saldo.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.certificado_saldo.cuenta_billetera_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.certificado_saldo.solicitado_por IS 'FK';
COMMENT ON COLUMN nucleo_financiero.certificado_saldo.folio IS 'UQ';
