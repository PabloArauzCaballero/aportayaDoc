-- impresion_anuncio · módulo 14 — Publicidad y Campañas
-- clase de dominio: ImpresionAnuncio
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS publicidad.impresion_anuncio (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  anuncio_id                         UUID NOT NULL,
  usuario_id                         UUID,
  mostrada_en                        TIMESTAMPTZ NOT NULL,
  costo                              NUMERIC(10,4) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  CONSTRAINT pk_impresion_anuncio PRIMARY KEY (id)
);

COMMENT ON TABLE publicidad.impresion_anuncio IS 'Módulo 14 — Publicidad y Campañas. [append-only] Que un partner se anuncie dentro de la app sin inventar un segundo cobro';
COMMENT ON COLUMN publicidad.impresion_anuncio.id IS 'PK';
COMMENT ON COLUMN publicidad.impresion_anuncio.anuncio_id IS 'FK, IDX';
COMMENT ON COLUMN publicidad.impresion_anuncio.usuario_id IS 'FK, NULL, IDX, M1';
COMMENT ON COLUMN publicidad.impresion_anuncio.mostrada_en IS 'IDX';
