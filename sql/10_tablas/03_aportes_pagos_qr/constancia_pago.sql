-- constancia_pago · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: ConstanciaPago
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.constancia_pago (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  pago_id                            UUID NOT NULL,
  codigo_verificacion                VARCHAR(40) NOT NULL,
  hash_contenido                     VARCHAR(64) NOT NULL,
  url_publica                        VARCHAR(255) NOT NULL,
  url_pdf                            VARCHAR(255) NOT NULL,
  fecha_generacion                   TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_constancia_pago PRIMARY KEY (id)
);

COMMENT ON TABLE aportes.constancia_pago IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.constancia_pago.id IS 'PK';
COMMENT ON COLUMN aportes.constancia_pago.pago_id IS 'FK, UQ';
COMMENT ON COLUMN aportes.constancia_pago.codigo_verificacion IS 'UQ';
