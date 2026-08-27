-- sorteo_turnos · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- clase de dominio: SorteoTurnos
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.sorteo_turnos (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  algoritmo                          VARCHAR(30) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  hash_semilla_previo                VARCHAR(64) NOT NULL,
  aportes_entropia                   JSONB,
  fecha_compromiso                   TIMESTAMPTZ NOT NULL,
  fecha_revelado_prevista            TIMESTAMPTZ,
  semilla_servidor                   VARCHAR(128),
  semilla_publica                    VARCHAR(128) NOT NULL,
  resultado                          JSONB NOT NULL,
  ejecutado_por                      UUID NOT NULL,
  fecha_ejecucion                    TIMESTAMPTZ NOT NULL,
  anulado_en                         TIMESTAMPTZ,
  CONSTRAINT pk_sorteo_turnos PRIMARY KEY (id),
  CONSTRAINT ck_sorteo_turnos_estado CHECK (estado IN ('ANULADO', 'COMPROMETIDO', 'REVELADO'))
);

COMMENT ON TABLE grupos.sorteo_turnos IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.sorteo_turnos.id IS 'PK';
COMMENT ON COLUMN grupos.sorteo_turnos.grupo_id IS 'FK parcial';
COMMENT ON COLUMN grupos.sorteo_turnos.estado IS 'CK, IDX';
COMMENT ON COLUMN grupos.sorteo_turnos.aportes_entropia IS 'NULL';
COMMENT ON COLUMN grupos.sorteo_turnos.fecha_revelado_prevista IS 'NULL';
COMMENT ON COLUMN grupos.sorteo_turnos.semilla_servidor IS 'NULL hasta revelar';
COMMENT ON COLUMN grupos.sorteo_turnos.ejecutado_por IS 'FK';
COMMENT ON COLUMN grupos.sorteo_turnos.anulado_en IS 'NULL';
