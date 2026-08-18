-- anuncio · módulo 14 — Publicidad y Campañas
-- clase de dominio: Anuncio
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS publicidad.anuncio (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  conjunto_anuncios_id               UUID NOT NULL,
  pieza_creativa_id                  UUID NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  iniciado_en                        TIMESTAMPTZ,
  finalizado_en                      TIMESTAMPTZ,
  CONSTRAINT pk_anuncio PRIMARY KEY (id),
  CONSTRAINT ck_anuncio_estado CHECK (estado IN ('EN_ENTREGA', 'FINALIZADO', 'PAUSADO', 'PROGRAMADO', 'RECHAZADO'))
);

COMMENT ON TABLE publicidad.anuncio IS 'Módulo 14 — Publicidad y Campañas. Que un partner se anuncie dentro de la app sin inventar un segundo cobro';
COMMENT ON COLUMN publicidad.anuncio.id IS 'PK';
COMMENT ON COLUMN publicidad.anuncio.conjunto_anuncios_id IS 'FK, IDX';
COMMENT ON COLUMN publicidad.anuncio.pieza_creativa_id IS 'FK, IDX';
COMMENT ON COLUMN publicidad.anuncio.estado IS 'CK, IDX';
COMMENT ON COLUMN publicidad.anuncio.iniciado_en IS 'NULL';
COMMENT ON COLUMN publicidad.anuncio.finalizado_en IS 'NULL';
