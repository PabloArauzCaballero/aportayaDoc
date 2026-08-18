-- segmento_audiencia · módulo 14 — Publicidad y Campañas
-- clase de dominio: SegmentoAudiencia
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS publicidad.segmento_audiencia (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  nombre                             VARCHAR(100) NOT NULL,
  criterios                          JSONB NOT NULL,
  reutilizable                       BOOLEAN DEFAULT FALSE NOT NULL,
  creado_por                         UUID NOT NULL,
  creado_en                          TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_segmento_audiencia PRIMARY KEY (id)
);

COMMENT ON TABLE publicidad.segmento_audiencia IS 'Módulo 14 — Publicidad y Campañas. Que un partner se anuncie dentro de la app sin inventar un segundo cobro';
COMMENT ON COLUMN publicidad.segmento_audiencia.id IS 'PK';
COMMENT ON COLUMN publicidad.segmento_audiencia.creado_por IS 'FK';
