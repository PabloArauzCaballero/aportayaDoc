-- conjunto_anuncios · módulo 14 — Publicidad y Campañas
-- clase de dominio: ConjuntoAnuncios
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS publicidad.conjunto_anuncios (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  campana_publicitaria_id            UUID NOT NULL,
  segmento_audiencia_id              UUID NOT NULL,
  espacio_publicitario_id            UUID NOT NULL,
  nombre                             VARCHAR(120) NOT NULL,
  presupuesto_diario                 NUMERIC(12,2) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  puja_maxima                        NUMERIC(10,2) NOT NULL,
  modelo_puja                        VARCHAR(10) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  CONSTRAINT pk_conjunto_anuncios PRIMARY KEY (id),
  CONSTRAINT ck_conjunto_anuncios_presupuesto_diario CHECK (presupuesto_diario > 0),
  CONSTRAINT ck_conjunto_anuncios_puja_maxima CHECK (puja_maxima > 0),
  CONSTRAINT ck_conjunto_anuncios_modelo_puja CHECK (modelo_puja IN ('CPC', 'CPM')),
  CONSTRAINT ck_conjunto_anuncios_estado CHECK (estado IN ('ACTIVO', 'AGOTADO', 'FINALIZADO', 'PAUSADO'))
);

COMMENT ON TABLE publicidad.conjunto_anuncios IS 'Módulo 14 — Publicidad y Campañas. Que un partner se anuncie dentro de la app sin inventar un segundo cobro';
COMMENT ON COLUMN publicidad.conjunto_anuncios.id IS 'PK';
COMMENT ON COLUMN publicidad.conjunto_anuncios.campana_publicitaria_id IS 'FK, IDX';
COMMENT ON COLUMN publicidad.conjunto_anuncios.segmento_audiencia_id IS 'FK, IDX';
COMMENT ON COLUMN publicidad.conjunto_anuncios.espacio_publicitario_id IS 'FK, IDX';
COMMENT ON COLUMN publicidad.conjunto_anuncios.presupuesto_diario IS 'CK: > 0';
COMMENT ON COLUMN publicidad.conjunto_anuncios.puja_maxima IS 'CK: > 0';
COMMENT ON COLUMN publicidad.conjunto_anuncios.modelo_puja IS 'CK';
COMMENT ON COLUMN publicidad.conjunto_anuncios.estado IS 'CK, IDX';
