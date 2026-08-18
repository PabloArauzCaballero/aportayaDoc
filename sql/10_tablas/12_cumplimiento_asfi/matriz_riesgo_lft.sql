-- matriz_riesgo_lft · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: MatrizRiesgoLft
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.matriz_riesgo_lft (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  version                            SMALLINT DEFAULT 0 NOT NULL,
  dimension                          VARCHAR(20) NOT NULL,
  factor                             VARCHAR(60) NOT NULL,
  ponderacion                        NUMERIC(5,2) NOT NULL,
  escala                             JSONB NOT NULL,
  base_normativa                     VARCHAR(120) NOT NULL,
  vigente_desde                      DATE NOT NULL,
  vigente_hasta                      DATE,
  aprobada_por                       UUID,
  CONSTRAINT pk_matriz_riesgo_lft PRIMARY KEY (id),
  CONSTRAINT ck_matriz_riesgo_lft_dimension CHECK (dimension IN ('CANAL', 'CLIENTE', 'PRODUCTO', 'ZONA_GEOGRAFICA'))
);

COMMENT ON TABLE cumplimiento.matriz_riesgo_lft IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.matriz_riesgo_lft.id IS 'PK';
COMMENT ON COLUMN cumplimiento.matriz_riesgo_lft.version IS 'UQ+dimension+factor';
COMMENT ON COLUMN cumplimiento.matriz_riesgo_lft.dimension IS 'CK';
COMMENT ON COLUMN cumplimiento.matriz_riesgo_lft.vigente_hasta IS 'NULL';
COMMENT ON COLUMN cumplimiento.matriz_riesgo_lft.aprobada_por IS 'FK, NULL';
