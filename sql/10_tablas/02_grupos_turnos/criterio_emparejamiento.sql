-- criterio_emparejamiento · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- clase de dominio: CriterioEmparejamiento
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.criterio_emparejamiento (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  peso_reputacion                    NUMERIC(4,3) NOT NULL,
  peso_monto                         NUMERIC(4,3) NOT NULL,
  peso_geografia                     NUMERIC(4,3) NOT NULL,
  peso_historial_comun               NUMERIC(4,3) NOT NULL,
  reputacion_minima                  NUMERIC(6,2) NOT NULL,
  max_morosos_por_grupo              SMALLINT NOT NULL,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_criterio_emparejamiento PRIMARY KEY (id)
);

COMMENT ON TABLE grupos.criterio_emparejamiento IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.criterio_emparejamiento.id IS 'PK';
