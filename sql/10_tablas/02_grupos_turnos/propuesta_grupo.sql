-- propuesta_grupo · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- clase de dominio: PropuestaGrupo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.propuesta_grupo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  criterio_id                        UUID NOT NULL,
  monto_aporte                       NUMERIC(14,2) DEFAULT 0 NOT NULL,
  periodicidad                       VARCHAR(15) NOT NULL,
  puntaje_cohesion                   NUMERIC(5,2) NOT NULL,
  riesgo_estimado                    NUMERIC(5,2) NOT NULL,
  estado                             VARCHAR(20) NOT NULL,
  aceptaciones_recibidas             SMALLINT NOT NULL,
  expira_en                          TIMESTAMPTZ NOT NULL,
  grupo_materializado_id             UUID,
  CONSTRAINT pk_propuesta_grupo PRIMARY KEY (id),
  CONSTRAINT ck_propuesta_grupo_estado CHECK (estado IN ('ACEPTADA_PARCIAL', 'CONFIRMADA', 'DESCARTADA', 'PROPUESTA'))
);

COMMENT ON TABLE grupos.propuesta_grupo IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.propuesta_grupo.id IS 'PK';
COMMENT ON COLUMN grupos.propuesta_grupo.criterio_id IS 'FK';
COMMENT ON COLUMN grupos.propuesta_grupo.estado IS 'CK';
COMMENT ON COLUMN grupos.propuesta_grupo.grupo_materializado_id IS 'FK, NULL';
