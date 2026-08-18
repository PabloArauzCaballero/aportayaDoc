-- turno · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- clase de dominio: Turno
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.turno (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  periodo_id                         UUID NOT NULL,
  cupo_id                            UUID NOT NULL,
  orden_asignado                     SMALLINT NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  criterio_asignacion                VARCHAR(25) NOT NULL,
  monto_estimado_cobro               NUMERIC(14,2) DEFAULT 0 NOT NULL,
  descuento_subasta                  NUMERIC(14,2),
  permutado_con_turno_id             UUID,
  confirmado_en                      TIMESTAMPTZ,
  CONSTRAINT pk_turno PRIMARY KEY (id),
  CONSTRAINT ck_turno_estado CHECK (estado IN ('ANULADO', 'COBRADO', 'CONFIRMADO', 'DIFERIDO', 'EN_CURSO', 'PERMUTADO', 'PROGRAMADO'))
);

COMMENT ON TABLE grupos.turno IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.turno.id IS 'PK';
COMMENT ON COLUMN grupos.turno.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN grupos.turno.periodo_id IS 'FK';
COMMENT ON COLUMN grupos.turno.cupo_id IS 'FK';
COMMENT ON COLUMN grupos.turno.orden_asignado IS 'UQ+grupo_id';
COMMENT ON COLUMN grupos.turno.estado IS 'CK';
COMMENT ON COLUMN grupos.turno.descuento_subasta IS 'NULL';
COMMENT ON COLUMN grupos.turno.permutado_con_turno_id IS 'FK, NULL';
COMMENT ON COLUMN grupos.turno.confirmado_en IS 'NULL';
