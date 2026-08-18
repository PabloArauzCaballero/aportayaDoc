-- retencion_saldo · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: RetencionSaldo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.retencion_saldo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cuenta_billetera_id                UUID NOT NULL,
  transaccion_origen_id              UUID,
  liberada_por                       UUID,
  motivo                             VARCHAR(30) NOT NULL,
  referencia_tipo                    VARCHAR(30),
  referencia_id                      UUID,
  monto                              NUMERIC(16,2) NOT NULL,
  estado                             VARCHAR(12) NOT NULL,
  expira_en                          TIMESTAMPTZ,
  creada_en                          TIMESTAMPTZ DEFAULT now() NOT NULL,
  liberada_en                        TIMESTAMPTZ,
  CONSTRAINT pk_retencion_saldo PRIMARY KEY (id),
  CONSTRAINT ck_retencion_saldo_motivo CHECK (motivo IN ('ANTIFRAUDE', 'APORTE_PROGRAMADO', 'COMISION_PENDIENTE', 'DISPUTA', 'ENTREGA_EN_CURSO', 'ORDEN_AUTORIDAD')),
  CONSTRAINT ck_retencion_saldo_monto CHECK (monto > 0),
  CONSTRAINT ck_retencion_saldo_estado CHECK (estado IN ('EJECUTADA', 'LIBERADA', 'VENCIDA', 'VIGENTE'))
);

COMMENT ON TABLE nucleo_financiero.retencion_saldo IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.retencion_saldo.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.retencion_saldo.cuenta_billetera_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.retencion_saldo.transaccion_origen_id IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.retencion_saldo.liberada_por IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.retencion_saldo.motivo IS 'CK, IDX';
COMMENT ON COLUMN nucleo_financiero.retencion_saldo.referencia_tipo IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.retencion_saldo.referencia_id IS 'NULL, polimorfica';
COMMENT ON COLUMN nucleo_financiero.retencion_saldo.monto IS 'CK: > 0';
COMMENT ON COLUMN nucleo_financiero.retencion_saldo.estado IS 'CK, IDX';
COMMENT ON COLUMN nucleo_financiero.retencion_saldo.expira_en IS 'NULL, IDX';
COMMENT ON COLUMN nucleo_financiero.retencion_saldo.liberada_en IS 'NULL';
