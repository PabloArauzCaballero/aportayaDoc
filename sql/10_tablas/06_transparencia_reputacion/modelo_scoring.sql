-- modelo_scoring · módulo 06 — Transparencia y Reputación
-- clase de dominio: ModeloScoring
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS transparencia.modelo_scoring (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  version                            VARCHAR(20) NOT NULL,
  descripcion                        VARCHAR(200) NOT NULL,
  puntaje_base                       NUMERIC(6,2) NOT NULL,
  puntaje_minimo                     NUMERIC(6,2) NOT NULL,
  puntaje_maximo                     NUMERIC(6,2) NOT NULL,
  factor_decaimiento_mensual         NUMERIC(5,4) NOT NULL,
  ventana_historica_meses            SMALLINT NOT NULL,
  min_eventos_para_score             SMALLINT NOT NULL,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  es_produccion                      BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_modelo_scoring PRIMARY KEY (id)
);

COMMENT ON TABLE transparencia.modelo_scoring IS 'Módulo 06 — Transparencia y Reputación. Que nadie tenga que "creerle" al organizador';
COMMENT ON COLUMN transparencia.modelo_scoring.id IS 'PK';
COMMENT ON COLUMN transparencia.modelo_scoring.version IS 'UQ';
COMMENT ON COLUMN transparencia.modelo_scoring.vigente_hasta IS 'NULL';
