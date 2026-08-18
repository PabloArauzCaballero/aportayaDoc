-- orden_retiro · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: OrdenRetiro
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.orden_retiro (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cuenta_billetera_id                UUID NOT NULL,
  instrumento_destino_id             UUID NOT NULL,
  retencion_id                       UUID,
  transaccion_id                     UUID,
  solicitada_por                     UUID NOT NULL,
  aprobada_por                       UUID,
  proveedor_id                       UUID,
  monto_solicitado                   NUMERIC(16,2) DEFAULT 0 NOT NULL,
  costo_retiro                       NUMERIC(10,2) DEFAULT 0 NOT NULL,
  monto_neto                         NUMERIC(16,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  estado                             VARCHAR(20) NOT NULL,
  mfa_verificado                     BOOLEAN DEFAULT FALSE NOT NULL,
  requiere_doble_aprobacion          BOOLEAN DEFAULT FALSE NOT NULL,
  ventana_enfriamiento_hasta         TIMESTAMPTZ,
  referencia_proveedor               VARCHAR(80),
  clave_idempotencia                 VARCHAR(100) NOT NULL,
  solicitada_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  pagada_en                          TIMESTAMPTZ,
  CONSTRAINT pk_orden_retiro PRIMARY KEY (id),
  CONSTRAINT ck_orden_retiro_monto_solicitado CHECK (monto_solicitado > 0),
  CONSTRAINT ck_orden_retiro_estado CHECK (estado IN ('AUTORIZADA', 'BORRADOR', 'EN_PROCESO', 'EN_REVISION', 'PAGADA', 'PENDIENTE', 'RECHAZADA', 'REVERSADA'))
);

COMMENT ON TABLE nucleo_financiero.orden_retiro IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.orden_retiro.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.orden_retiro.cuenta_billetera_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.orden_retiro.instrumento_destino_id IS 'FK';
COMMENT ON COLUMN nucleo_financiero.orden_retiro.retencion_id IS 'FK, NULL, UQ';
COMMENT ON COLUMN nucleo_financiero.orden_retiro.transaccion_id IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.orden_retiro.solicitada_por IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.orden_retiro.aprobada_por IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.orden_retiro.proveedor_id IS 'FK, NULL, M3';
COMMENT ON COLUMN nucleo_financiero.orden_retiro.monto_solicitado IS 'CK: > 0';
COMMENT ON COLUMN nucleo_financiero.orden_retiro.estado IS 'CK, IDX';
COMMENT ON COLUMN nucleo_financiero.orden_retiro.ventana_enfriamiento_hasta IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.orden_retiro.referencia_proveedor IS 'UQ, NULL';
COMMENT ON COLUMN nucleo_financiero.orden_retiro.pagada_en IS 'NULL';
