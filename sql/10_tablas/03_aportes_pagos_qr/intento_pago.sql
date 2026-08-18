-- intento_pago · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: IntentoPago
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.intento_pago (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  orden_cobro_id                     UUID NOT NULL,
  numero_intento                     SMALLINT NOT NULL,
  canal                              VARCHAR(30) NOT NULL,
  iniciado_en                        TIMESTAMPTZ NOT NULL,
  finalizado_en                      TIMESTAMPTZ,
  estado                             VARCHAR(15) NOT NULL,
  codigo_error                       VARCHAR(40),
  mensaje_proveedor                  VARCHAR(255),
  clave_idempotencia                 VARCHAR(80) NOT NULL,
  CONSTRAINT pk_intento_pago PRIMARY KEY (id),
  CONSTRAINT ck_intento_pago_canal CHECK (canal IN ('BILLETERA_MOVIL', 'COMPENSACION_INTERNA', 'EFECTIVO_AL_ORGANIZADOR', 'FONDO_GARANTIA', 'QR_INTEROPERABLE', 'TARJETA', 'TRANSFERENCIA_BANCARIA')),
  CONSTRAINT ck_intento_pago_estado CHECK (estado IN ('APROBADA', 'DESCONOCIDA', 'EXPIRADA', 'PENDIENTE', 'RECHAZADA'))
);

COMMENT ON TABLE aportes.intento_pago IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.intento_pago.id IS 'PK';
COMMENT ON COLUMN aportes.intento_pago.orden_cobro_id IS 'FK, IDX';
COMMENT ON COLUMN aportes.intento_pago.canal IS 'CK';
COMMENT ON COLUMN aportes.intento_pago.finalizado_en IS 'NULL';
COMMENT ON COLUMN aportes.intento_pago.estado IS 'CK';
COMMENT ON COLUMN aportes.intento_pago.codigo_error IS 'NULL';
COMMENT ON COLUMN aportes.intento_pago.mensaje_proveedor IS 'NULL';
