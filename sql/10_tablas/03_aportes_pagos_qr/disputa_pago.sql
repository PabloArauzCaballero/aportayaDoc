-- disputa_pago · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: DisputaPago
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.disputa_pago (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  pago_id                            UUID NOT NULL,
  tipo                               VARCHAR(25) NOT NULL,
  descripcion                        TEXT NOT NULL,
  monto_disputado                    NUMERIC(14,2) DEFAULT 0 NOT NULL,
  estado                             VARCHAR(25) NOT NULL,
  evidencias                         JSONB NOT NULL,
  abierta_en                         TIMESTAMPTZ DEFAULT now() NOT NULL,
  fecha_limite_respuesta             TIMESTAMPTZ NOT NULL,
  resuelta_en                        TIMESTAMPTZ,
  CONSTRAINT pk_disputa_pago PRIMARY KEY (id),
  CONSTRAINT ck_disputa_pago_tipo CHECK (tipo IN ('CONTRACARGO', 'DESCONOCIMIENTO', 'MONTO_INCORRECTO')),
  CONSTRAINT ck_disputa_pago_estado CHECK (estado IN ('ABIERTA', 'EN_ANALISIS', 'RESUELTA_A_FAVOR', 'RESUELTA_EN_CONTRA'))
);

COMMENT ON TABLE aportes.disputa_pago IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.disputa_pago.id IS 'PK';
COMMENT ON COLUMN aportes.disputa_pago.pago_id IS 'FK, IDX';
COMMENT ON COLUMN aportes.disputa_pago.tipo IS 'CK';
COMMENT ON COLUMN aportes.disputa_pago.estado IS 'CK';
COMMENT ON COLUMN aportes.disputa_pago.resuelta_en IS 'NULL';
