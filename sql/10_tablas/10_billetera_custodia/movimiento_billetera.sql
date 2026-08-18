-- movimiento_billetera · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: MovimientoBilletera
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.movimiento_billetera (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  transaccion_id                     UUID NOT NULL,
  cuenta_billetera_id                UUID NOT NULL,
  orden                              SMALLINT NOT NULL,
  sentido                            VARCHAR(7) NOT NULL,
  monto                              NUMERIC(16,2) NOT NULL,
  saldo_disponible_posterior         NUMERIC(16,2) DEFAULT 0 NOT NULL,
  saldo_retenido_posterior           NUMERIC(16,2) DEFAULT 0 NOT NULL,
  glosa                              VARCHAR(160) NOT NULL,
  registrado_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_movimiento_billetera PRIMARY KEY (id),
  CONSTRAINT ck_movimiento_billetera_sentido CHECK (sentido IN ('DEBITO', 'CREDITO')),
  CONSTRAINT ck_movimiento_billetera_monto CHECK (monto > 0)
);

COMMENT ON TABLE nucleo_financiero.movimiento_billetera IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. [append-only] El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.movimiento_billetera.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.movimiento_billetera.transaccion_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.movimiento_billetera.cuenta_billetera_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.movimiento_billetera.sentido IS 'CK: DEBITO|CREDITO';
COMMENT ON COLUMN nucleo_financiero.movimiento_billetera.monto IS 'CK: > 0';
COMMENT ON COLUMN nucleo_financiero.movimiento_billetera.registrado_en IS 'IDX';
