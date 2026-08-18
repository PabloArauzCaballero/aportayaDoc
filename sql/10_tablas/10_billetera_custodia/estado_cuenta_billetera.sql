-- estado_cuenta_billetera · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: EstadoCuentaBilletera
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.estado_cuenta_billetera (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cuenta_billetera_id                UUID NOT NULL,
  periodo_desde                      DATE NOT NULL,
  periodo_hasta                      DATE NOT NULL,
  saldo_inicial                      NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_creditos                     NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_debitos                      NUMERIC(16,2) DEFAULT 0 NOT NULL,
  saldo_final                        NUMERIC(16,2) DEFAULT 0 NOT NULL,
  cantidad_movimientos               INTEGER DEFAULT 0 NOT NULL,
  url_archivo                        VARCHAR(255) NOT NULL,
  hash_archivo                       VARCHAR(64) NOT NULL,
  emitido_en                         TIMESTAMPTZ DEFAULT now() NOT NULL,
  entregado_en                       TIMESTAMPTZ,
  CONSTRAINT pk_estado_cuenta_billetera PRIMARY KEY (id)
);

COMMENT ON TABLE nucleo_financiero.estado_cuenta_billetera IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.estado_cuenta_billetera.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.estado_cuenta_billetera.cuenta_billetera_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.estado_cuenta_billetera.periodo_desde IS 'UQ+cuenta_billetera_id+periodo_hasta';
COMMENT ON COLUMN nucleo_financiero.estado_cuenta_billetera.entregado_en IS 'NULL';
