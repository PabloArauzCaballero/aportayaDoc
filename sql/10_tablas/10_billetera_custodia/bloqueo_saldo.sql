-- bloqueo_saldo · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: BloqueoSaldo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.bloqueo_saldo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cuenta_billetera_id                UUID NOT NULL,
  retencion_id                       UUID,
  levantada_por                      UUID,
  autoridad                          VARCHAR(20) NOT NULL,
  tipo_orden                         VARCHAR(30) NOT NULL,
  numero_oficio                      VARCHAR(60) NOT NULL,
  monto_bloqueado                    NUMERIC(16,2),
  alcance                            VARCHAR(10) NOT NULL,
  documento_url                      VARCHAR(255) NOT NULL,
  hash_documento                     VARCHAR(64) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  recibido_en                        TIMESTAMPTZ DEFAULT now() NOT NULL,
  vence_en                           TIMESTAMPTZ,
  levantado_en                       TIMESTAMPTZ,
  CONSTRAINT pk_bloqueo_saldo PRIMARY KEY (id),
  CONSTRAINT ck_bloqueo_saldo_autoridad CHECK (autoridad IN ('ASFI', 'FISCALIA', 'INTERNO', 'JUZGADO', 'UIF')),
  CONSTRAINT ck_bloqueo_saldo_tipo_orden CHECK (tipo_orden IN ('CONGELAMIENTO', 'EMBARGO', 'INFORMATIVO', 'INMOVILIZACION', 'RETENCION')),
  CONSTRAINT ck_bloqueo_saldo_alcance CHECK (alcance IN ('PARCIAL', 'TOTAL')),
  CONSTRAINT ck_bloqueo_saldo_estado CHECK (estado IN ('LEVANTADO', 'VENCIDO', 'VIGENTE'))
);

COMMENT ON TABLE nucleo_financiero.bloqueo_saldo IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.bloqueo_saldo.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.bloqueo_saldo.cuenta_billetera_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.bloqueo_saldo.retencion_id IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.bloqueo_saldo.levantada_por IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.bloqueo_saldo.autoridad IS 'CK';
COMMENT ON COLUMN nucleo_financiero.bloqueo_saldo.tipo_orden IS 'CK';
COMMENT ON COLUMN nucleo_financiero.bloqueo_saldo.numero_oficio IS 'UQ';
COMMENT ON COLUMN nucleo_financiero.bloqueo_saldo.monto_bloqueado IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.bloqueo_saldo.alcance IS 'CK';
COMMENT ON COLUMN nucleo_financiero.bloqueo_saldo.estado IS 'CK, IDX';
COMMENT ON COLUMN nucleo_financiero.bloqueo_saldo.vence_en IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.bloqueo_saldo.levantado_en IS 'NULL';
