-- metrica_grupo · módulo 06 — Transparencia y Reputación
-- clase de dominio: MetricaGrupo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS transparencia.metrica_grupo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  periodo_id                         UUID,
  codigo                             VARCHAR(40) NOT NULL,
  valor                              NUMERIC(12,4) NOT NULL,
  unidad                             VARCHAR(15) NOT NULL,
  umbral_alerta                      NUMERIC(12,4),
  en_alerta                          BOOLEAN DEFAULT FALSE NOT NULL,
  calculada_en                       TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_metrica_grupo PRIMARY KEY (id)
);

COMMENT ON TABLE transparencia.metrica_grupo IS 'Módulo 06 — Transparencia y Reputación. Que nadie tenga que "creerle" al organizador';
COMMENT ON COLUMN transparencia.metrica_grupo.id IS 'PK';
COMMENT ON COLUMN transparencia.metrica_grupo.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN transparencia.metrica_grupo.periodo_id IS 'FK, NULL';
COMMENT ON COLUMN transparencia.metrica_grupo.codigo IS 'UQ+grupo_id+periodo_id';
COMMENT ON COLUMN transparencia.metrica_grupo.umbral_alerta IS 'NULL';
COMMENT ON COLUMN transparencia.metrica_grupo.en_alerta IS 'IDX';
