-- webhook_pasarela · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: WebhookPasarela
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.webhook_pasarela (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  proveedor_id                       UUID NOT NULL,
  evento                             VARCHAR(60) NOT NULL,
  payload_crudo                      JSONB NOT NULL,
  firma                              VARCHAR(255) NOT NULL,
  firma_valida                       BOOLEAN DEFAULT FALSE NOT NULL,
  recibido_en                        TIMESTAMPTZ DEFAULT now() NOT NULL,
  procesado_en                       TIMESTAMPTZ,
  intentos_procesamiento             SMALLINT DEFAULT 0 NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  clave_idempotencia                 VARCHAR(120) NOT NULL,
  error_procesamiento                TEXT,
  pago_id                            UUID,
  CONSTRAINT pk_webhook_pasarela PRIMARY KEY (id),
  CONSTRAINT ck_webhook_pasarela_estado CHECK (estado IN ('DESCARTADO', 'DUPLICADO', 'FALLIDO', 'PROCESADO', 'RECIBIDO'))
);

COMMENT ON TABLE aportes.webhook_pasarela IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.webhook_pasarela.id IS 'PK';
COMMENT ON COLUMN aportes.webhook_pasarela.proveedor_id IS 'FK, IDX';
COMMENT ON COLUMN aportes.webhook_pasarela.recibido_en IS 'IDX';
COMMENT ON COLUMN aportes.webhook_pasarela.procesado_en IS 'NULL';
COMMENT ON COLUMN aportes.webhook_pasarela.estado IS 'CK, IDX';
COMMENT ON COLUMN aportes.webhook_pasarela.error_procesamiento IS 'NULL';
COMMENT ON COLUMN aportes.webhook_pasarela.pago_id IS 'FK, NULL';
