-- factura_electronica · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: FacturaElectronica
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.factura_electronica (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  devengo_id                         UUID,
  usuario_id                         UUID NOT NULL,
  datos_facturacion_id               UUID NOT NULL,
  lote_envio_sin_id                  UUID,
  evento_significativo_id            UUID,
  nit_emisor                         VARCHAR(20) NOT NULL,
  sucursal                           SMALLINT NOT NULL,
  punto_venta                        SMALLINT NOT NULL,
  numero_factura                     BIGINT NOT NULL,
  cuf                                VARCHAR(80) NOT NULL,
  cufd                               VARCHAR(120) NOT NULL,
  codigo_control                     VARCHAR(20),
  fecha_emision                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  monto_total                        NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_iva                          NUMERIC(12,2) DEFAULT 0 NOT NULL,
  monto_no_sujeto                    NUMERIC(12,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  estado_fiscal                      VARCHAR(20) NOT NULL,
  url_pdf                            VARCHAR(255),
  url_xml                            VARCHAR(255),
  hash_documento                     VARCHAR(64) NOT NULL,
  qr_verificacion                    VARCHAR(255),
  leyenda                            VARCHAR(200),
  anulada_en                         TIMESTAMPTZ,
  motivo_anulacion                   VARCHAR(200),
  CONSTRAINT pk_factura_electronica PRIMARY KEY (id),
  CONSTRAINT ck_factura_electronica_estado_fiscal CHECK (estado_fiscal IN ('ANULADA', 'EMITIDA_OFFLINE', 'PENDIENTE', 'RECHAZADA', 'VALIDADA'))
);

COMMENT ON TABLE tarifas.factura_electronica IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.factura_electronica.id IS 'PK';
COMMENT ON COLUMN tarifas.factura_electronica.devengo_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN tarifas.factura_electronica.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN tarifas.factura_electronica.datos_facturacion_id IS 'FK';
COMMENT ON COLUMN tarifas.factura_electronica.lote_envio_sin_id IS 'FK, NULL';
COMMENT ON COLUMN tarifas.factura_electronica.evento_significativo_id IS 'FK, NULL';
COMMENT ON COLUMN tarifas.factura_electronica.numero_factura IS 'UQ+sucursal+punto_venta';
COMMENT ON COLUMN tarifas.factura_electronica.codigo_control IS 'NULL';
COMMENT ON COLUMN tarifas.factura_electronica.fecha_emision IS 'IDX';
COMMENT ON COLUMN tarifas.factura_electronica.estado_fiscal IS 'CK, IDX';
COMMENT ON COLUMN tarifas.factura_electronica.url_pdf IS 'NULL';
COMMENT ON COLUMN tarifas.factura_electronica.url_xml IS 'NULL';
COMMENT ON COLUMN tarifas.factura_electronica.qr_verificacion IS 'NULL';
COMMENT ON COLUMN tarifas.factura_electronica.leyenda IS 'NULL';
COMMENT ON COLUMN tarifas.factura_electronica.anulada_en IS 'NULL';
COMMENT ON COLUMN tarifas.factura_electronica.motivo_anulacion IS 'NULL';
