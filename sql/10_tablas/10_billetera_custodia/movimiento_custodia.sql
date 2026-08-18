-- movimiento_custodia · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: MovimientoCustodia
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.movimiento_custodia (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cuenta_custodia_id                 UUID NOT NULL,
  movimiento_bancario_id             UUID,
  fecha_valor                        DATE NOT NULL,
  tipo                               VARCHAR(20) NOT NULL,
  sentido                            VARCHAR(7) NOT NULL,
  monto                              NUMERIC(18,2) NOT NULL,
  referencia_bancaria                VARCHAR(80) NOT NULL,
  glosa                              VARCHAR(200) NOT NULL,
  conciliado                         BOOLEAN DEFAULT FALSE NOT NULL,
  registrado_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_movimiento_custodia PRIMARY KEY (id),
  CONSTRAINT ck_movimiento_custodia_tipo CHECK (tipo IN ('COSTO_BANCARIO', 'EGRESO', 'INGRESO', 'RENDIMIENTO')),
  CONSTRAINT ck_movimiento_custodia_sentido CHECK (sentido IN ('CREDITO', 'DEBITO')),
  CONSTRAINT ck_movimiento_custodia_monto CHECK (monto > 0)
);

COMMENT ON TABLE nucleo_financiero.movimiento_custodia IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. [append-only] El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.movimiento_custodia.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.movimiento_custodia.cuenta_custodia_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.movimiento_custodia.movimiento_bancario_id IS 'FK, NULL, UQ, M3';
COMMENT ON COLUMN nucleo_financiero.movimiento_custodia.fecha_valor IS 'IDX';
COMMENT ON COLUMN nucleo_financiero.movimiento_custodia.tipo IS 'CK';
COMMENT ON COLUMN nucleo_financiero.movimiento_custodia.sentido IS 'CK';
COMMENT ON COLUMN nucleo_financiero.movimiento_custodia.monto IS 'CK: > 0';
COMMENT ON COLUMN nucleo_financiero.movimiento_custodia.referencia_bancaria IS 'UQ';
COMMENT ON COLUMN nucleo_financiero.movimiento_custodia.conciliado IS 'IDX';
