-- reglamento_grupo · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- clase de dominio: ReglamentoGrupo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.reglamento_grupo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  version                            SMALLINT DEFAULT 0 NOT NULL,
  contenido                          TEXT NOT NULL,
  hash_contenido                     VARCHAR(64) NOT NULL,
  clausulas_mora                     TEXT NOT NULL,
  clausulas_abandono                 TEXT NOT NULL,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  redactado_por                      UUID NOT NULL,
  CONSTRAINT pk_reglamento_grupo PRIMARY KEY (id)
);

COMMENT ON TABLE grupos.reglamento_grupo IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.reglamento_grupo.id IS 'PK';
COMMENT ON COLUMN grupos.reglamento_grupo.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN grupos.reglamento_grupo.version IS 'UQ+grupo_id';
COMMENT ON COLUMN grupos.reglamento_grupo.vigente_hasta IS 'NULL';
COMMENT ON COLUMN grupos.reglamento_grupo.redactado_por IS 'FK';
