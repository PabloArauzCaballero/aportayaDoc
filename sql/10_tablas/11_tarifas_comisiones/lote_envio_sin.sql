-- lote_envio_sin · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: LoteEnvioSin
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.lote_envio_sin (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  tipo_envio                         VARCHAR(25) NOT NULL,
  cantidad_documentos                INTEGER DEFAULT 0 NOT NULL,
  fecha_envio                        TIMESTAMPTZ NOT NULL,
  codigo_recepcion                   VARCHAR(60),
  estado                             VARCHAR(15) NOT NULL,
  respuesta                          JSONB,
  reintentos                         SMALLINT DEFAULT 0 NOT NULL,
  CONSTRAINT pk_lote_envio_sin PRIMARY KEY (id),
  CONSTRAINT ck_lote_envio_sin_tipo_envio CHECK (tipo_envio IN ('ANULACIONES', 'EVENTOS', 'FACTURAS', 'MASIVO', 'NOTAS')),
  CONSTRAINT ck_lote_envio_sin_estado CHECK (estado IN ('ACEPTADO', 'ENVIADO', 'PARCIAL', 'PENDIENTE', 'RECHAZADO'))
);

COMMENT ON TABLE tarifas.lote_envio_sin IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.lote_envio_sin.id IS 'PK';
COMMENT ON COLUMN tarifas.lote_envio_sin.tipo_envio IS 'CK';
COMMENT ON COLUMN tarifas.lote_envio_sin.fecha_envio IS 'IDX';
COMMENT ON COLUMN tarifas.lote_envio_sin.codigo_recepcion IS 'UQ, NULL';
COMMENT ON COLUMN tarifas.lote_envio_sin.estado IS 'CK, IDX';
COMMENT ON COLUMN tarifas.lote_envio_sin.respuesta IS 'NULL';
