-- tarifario · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: Tarifario
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS catalogo.tarifario (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(30) NOT NULL,
  version                            SMALLINT DEFAULT 0 NOT NULL,
  nombre                             VARCHAR(120) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  moneda_base                        CHAR(3) NOT NULL,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  dias_preaviso                      SMALLINT NOT NULL,
  publicado_en                       TIMESTAMPTZ,
  url_publicacion                    VARCHAR(255),
  hash_documento                     VARCHAR(64),
  tarifario_anterior_id              UUID,
  aprobado_por                       UUID,
  acta_aprobacion                    VARCHAR(80),
  CONSTRAINT pk_tarifario PRIMARY KEY (id),
  CONSTRAINT ck_tarifario_estado CHECK (estado IN ('ARCHIVADO', 'BORRADOR', 'EN_PREAVISO', 'SUSTITUIDO', 'VIGENTE'))
);

COMMENT ON TABLE catalogo.tarifario IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN catalogo.tarifario.id IS 'PK';
COMMENT ON COLUMN catalogo.tarifario.codigo IS 'UQ+version';
COMMENT ON COLUMN catalogo.tarifario.estado IS 'CK, IDX';
COMMENT ON COLUMN catalogo.tarifario.vigente_desde IS 'IDX';
COMMENT ON COLUMN catalogo.tarifario.vigente_hasta IS 'NULL';
COMMENT ON COLUMN catalogo.tarifario.publicado_en IS 'NULL';
COMMENT ON COLUMN catalogo.tarifario.url_publicacion IS 'NULL';
COMMENT ON COLUMN catalogo.tarifario.hash_documento IS 'NULL';
COMMENT ON COLUMN catalogo.tarifario.tarifario_anterior_id IS 'FK, NULL';
COMMENT ON COLUMN catalogo.tarifario.aprobado_por IS 'FK, NULL';
COMMENT ON COLUMN catalogo.tarifario.acta_aprobacion IS 'NULL';
