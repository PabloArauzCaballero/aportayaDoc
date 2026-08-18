-- propuesta_postulacion · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.propuesta_postulacion (
  propuesta_id                       UUID DEFAULT gen_random_uuid() NOT NULL,
  postulacion_id                     UUID DEFAULT gen_random_uuid() NOT NULL,
  acepto                             BOOLEAN DEFAULT FALSE NOT NULL,
  respondido_en                      TIMESTAMPTZ,
  CONSTRAINT pk_propuesta_postulacion PRIMARY KEY (propuesta_id, postulacion_id)
);

COMMENT ON TABLE grupos.propuesta_postulacion IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.propuesta_postulacion.propuesta_id IS 'PK, FK';
COMMENT ON COLUMN grupos.propuesta_postulacion.postulacion_id IS 'PK, FK';
COMMENT ON COLUMN grupos.propuesta_postulacion.respondido_en IS 'NULL';
