-- saldo_diario_billetera · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: SaldoDiarioBilletera
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.saldo_diario_billetera (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cuenta_billetera_id                UUID NOT NULL,
  fecha                              DATE NOT NULL,
  saldo_disponible                   NUMERIC(16,2) DEFAULT 0 NOT NULL,
  saldo_retenido                     NUMERIC(16,2) DEFAULT 0 NOT NULL,
  cantidad_movimientos               INTEGER DEFAULT 0 NOT NULL,
  hash_registro                      VARCHAR(64) NOT NULL,
  hash_anterior                      VARCHAR(64),
  cerrado_en                         TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_saldo_diario_billetera PRIMARY KEY (id)
);

COMMENT ON TABLE nucleo_financiero.saldo_diario_billetera IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. [append-only] El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.saldo_diario_billetera.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.saldo_diario_billetera.cuenta_billetera_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.saldo_diario_billetera.hash_anterior IS 'NULL';
