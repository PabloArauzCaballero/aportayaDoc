-- orden_cobro · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: OrdenCobro
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.orden_cobro (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  obligacion_id                      UUID NOT NULL,
  proveedor_id                       UUID NOT NULL,
  monto_exacto                       NUMERIC(14,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  permite_monto_abierto              BOOLEAN DEFAULT FALSE NOT NULL,
  referencia_unica                   VARCHAR(60) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  emitida_en                         TIMESTAMPTZ NOT NULL,
  expira_en                          TIMESTAMPTZ NOT NULL,
  clave_idempotencia                 VARCHAR(80) NOT NULL,
  CONSTRAINT pk_orden_cobro PRIMARY KEY (id),
  CONSTRAINT ck_orden_cobro_estado CHECK (estado IN ('ANULADA', 'ENTREGADA', 'ESCANEADA', 'EXPIRADA', 'GENERADA', 'PAGADA'))
);

COMMENT ON TABLE aportes.orden_cobro IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.orden_cobro.id IS 'PK';
COMMENT ON COLUMN aportes.orden_cobro.obligacion_id IS 'FK, IDX';
COMMENT ON COLUMN aportes.orden_cobro.proveedor_id IS 'FK';
COMMENT ON COLUMN aportes.orden_cobro.referencia_unica IS 'UQ';
COMMENT ON COLUMN aportes.orden_cobro.estado IS 'CK, IDX';
COMMENT ON COLUMN aportes.orden_cobro.expira_en IS 'IDX';
