-- conversion_anuncio · módulo 14 — Publicidad y Campañas
-- clase de dominio: ConversionAnuncio
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS publicidad.conversion_anuncio (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  clic_id                            UUID,
  impresion_id                       UUID,
  tipo                               VARCHAR(25) NOT NULL,
  referencia_id                      UUID,
  ocurrida_en                        TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_conversion_anuncio PRIMARY KEY (id),
  CONSTRAINT ck_conversion_anuncio_tipo CHECK (tipo IN ('DESCARGA_APP', 'POSTULACION_GRUPO', 'REGISTRO'))
);

COMMENT ON TABLE publicidad.conversion_anuncio IS 'Módulo 14 — Publicidad y Campañas. [append-only] Que un partner se anuncie dentro de la app sin inventar un segundo cobro';
COMMENT ON COLUMN publicidad.conversion_anuncio.id IS 'PK';
COMMENT ON COLUMN publicidad.conversion_anuncio.clic_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN publicidad.conversion_anuncio.impresion_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN publicidad.conversion_anuncio.tipo IS 'CK';
COMMENT ON COLUMN publicidad.conversion_anuncio.referencia_id IS 'NULL, IDX, polimorfica';
