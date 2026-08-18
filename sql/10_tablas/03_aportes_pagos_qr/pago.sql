-- pago · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: Pago
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.pago (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  obligacion_id                      UUID NOT NULL,
  intento_pago_id                    UUID,
  proveedor_id                       UUID,
  monto                              NUMERIC(14,2) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  monto_comision_proveedor           NUMERIC(10,2) DEFAULT 0 NOT NULL,
  monto_neto_acreditado              NUMERIC(14,2) DEFAULT 0 NOT NULL,
  canal                              VARCHAR(30) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  fecha_hora_pago                    TIMESTAMPTZ NOT NULL,
  fecha_hora_acreditacion            TIMESTAMPTZ,
  referencia_proveedor               VARCHAR(80) NOT NULL,
  pagador_nombre                     VARCHAR(120),
  pagador_documento                  VARCHAR(30),
  cuenta_origen_enmascarada          VARCHAR(40),
  registrado_por                     UUID,
  es_manual                          BOOLEAN DEFAULT FALSE NOT NULL,
  clave_idempotencia                 VARCHAR(80) NOT NULL,
  CONSTRAINT pk_pago PRIMARY KEY (id),
  CONSTRAINT ck_pago_monto CHECK (monto > 0),
  CONSTRAINT ck_pago_canal CHECK (canal IN ('BILLETERA_MOVIL', 'COMPENSACION_INTERNA', 'EFECTIVO_AL_ORGANIZADOR', 'FONDO_GARANTIA', 'QR_INTEROPERABLE', 'TARJETA', 'TRANSFERENCIA_BANCARIA')),
  CONSTRAINT ck_pago_estado CHECK (estado IN ('ACREDITADO', 'ANULADO', 'EN_DISPUTA', 'EN_PROCESO', 'INICIADO', 'RECHAZADO', 'REVERSADO'))
);

COMMENT ON TABLE aportes.pago IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.pago.id IS 'PK';
COMMENT ON COLUMN aportes.pago.obligacion_id IS 'FK, IDX';
COMMENT ON COLUMN aportes.pago.intento_pago_id IS 'FK, NULL, UQ';
COMMENT ON COLUMN aportes.pago.proveedor_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN aportes.pago.monto IS 'CK: > 0';
COMMENT ON COLUMN aportes.pago.canal IS 'CK';
COMMENT ON COLUMN aportes.pago.estado IS 'CK, IDX';
COMMENT ON COLUMN aportes.pago.fecha_hora_pago IS 'IDX';
COMMENT ON COLUMN aportes.pago.fecha_hora_acreditacion IS 'NULL';
COMMENT ON COLUMN aportes.pago.referencia_proveedor IS 'UQ+proveedor_id';
COMMENT ON COLUMN aportes.pago.pagador_nombre IS 'NULL';
COMMENT ON COLUMN aportes.pago.pagador_documento IS 'NULL';
COMMENT ON COLUMN aportes.pago.cuenta_origen_enmascarada IS 'NULL';
COMMENT ON COLUMN aportes.pago.registrado_por IS 'FK, NULL';
