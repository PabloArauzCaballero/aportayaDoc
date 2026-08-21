-- orden_recarga · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: OrdenRecarga
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.orden_recarga (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cuenta_billetera_id                UUID NOT NULL,
  instrumento_fondeo_id              UUID,
  proveedor_id                       UUID,
  pago_id                            UUID,
  transaccion_id                     UUID,
  monto_bruto                        NUMERIC(16,2) DEFAULT 0 NOT NULL,
  costo_proveedor                    NUMERIC(10,2) DEFAULT 0 NOT NULL,
  monto_acreditado                   NUMERIC(16,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  referencia_externa                 VARCHAR(80),
  clave_idempotencia                 VARCHAR(100) NOT NULL,
  solicitada_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  acreditada_en                      TIMESTAMPTZ,
  expira_en                          TIMESTAMPTZ,
  CONSTRAINT pk_orden_recarga PRIMARY KEY (id),
  CONSTRAINT ck_orden_recarga_monto_bruto CHECK (monto_bruto > 0),
  CONSTRAINT ck_orden_recarga_estado CHECK (estado IN ('ACREDITADA', 'EXPIRADA', 'PENDIENTE', 'RECHAZADA', 'REVERSADA'))
);

COMMENT ON TABLE nucleo_financiero.orden_recarga IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.orden_recarga.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.orden_recarga.cuenta_billetera_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.orden_recarga.instrumento_fondeo_id IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.orden_recarga.proveedor_id IS 'FK, NULL, M3';
COMMENT ON COLUMN nucleo_financiero.orden_recarga.pago_id IS 'FK, NULL, M3';
COMMENT ON COLUMN nucleo_financiero.orden_recarga.transaccion_id IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.orden_recarga.monto_bruto IS 'CK: > 0';
COMMENT ON COLUMN nucleo_financiero.orden_recarga.estado IS 'CK, IDX';
COMMENT ON COLUMN nucleo_financiero.orden_recarga.referencia_externa IS 'UQ, NULL';
COMMENT ON COLUMN nucleo_financiero.orden_recarga.acreditada_en IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.orden_recarga.expira_en IS 'NULL';
