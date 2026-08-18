-- clic_anuncio · módulo 14 — Publicidad y Campañas
-- clase de dominio: ClicAnuncio
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS publicidad.clic_anuncio (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  impresion_id                       UUID NOT NULL,
  usuario_id                         UUID,
  clic_en                            TIMESTAMPTZ NOT NULL,
  costo                              NUMERIC(10,4) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  CONSTRAINT pk_clic_anuncio PRIMARY KEY (id)
);

COMMENT ON TABLE publicidad.clic_anuncio IS 'Módulo 14 — Publicidad y Campañas. [append-only] Que un partner se anuncie dentro de la app sin inventar un segundo cobro';
COMMENT ON COLUMN publicidad.clic_anuncio.id IS 'PK';
COMMENT ON COLUMN publicidad.clic_anuncio.impresion_id IS 'FK, IDX';
COMMENT ON COLUMN publicidad.clic_anuncio.usuario_id IS 'FK, NULL, IDX, M1';
COMMENT ON COLUMN publicidad.clic_anuncio.clic_en IS 'IDX';
