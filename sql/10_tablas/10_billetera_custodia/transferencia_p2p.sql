-- transferencia_p2p · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: TransferenciaP2P
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.transferencia_p2p (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  transaccion_id                     UUID NOT NULL,
  cuenta_billetera_origen_id         UUID NOT NULL,
  cuenta_billetera_destino_id        UUID NOT NULL,
  grupo_id                           UUID,
  obligacion_id                      UUID,
  monto                              NUMERIC(16,2) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  concepto                           VARCHAR(140) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  ejecutada_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_transferencia_p2p PRIMARY KEY (id),
  CONSTRAINT ck_transferencia_p2p_monto CHECK (monto > 0),
  CONSTRAINT ck_transferencia_p2p_estado CHECK (estado IN ('EJECUTADA', 'PENDIENTE', 'RECHAZADA', 'REVERSADA'))
);

COMMENT ON TABLE nucleo_financiero.transferencia_p2p IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.transferencia_p2p.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.transferencia_p2p.transaccion_id IS 'FK, UQ';
COMMENT ON COLUMN nucleo_financiero.transferencia_p2p.cuenta_billetera_origen_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.transferencia_p2p.cuenta_billetera_destino_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.transferencia_p2p.grupo_id IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.transferencia_p2p.obligacion_id IS 'FK, NULL, M3';
COMMENT ON COLUMN nucleo_financiero.transferencia_p2p.monto IS 'CK: > 0';
COMMENT ON COLUMN nucleo_financiero.transferencia_p2p.estado IS 'CK';
