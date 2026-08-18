-- acuerdo · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- clase de dominio: Acuerdo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.acuerdo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  tipo                               VARCHAR(30) NOT NULL,
  descripcion                        VARCHAR(400) NOT NULL,
  propuesto_por                      UUID NOT NULL,
  quorum_requerido                   NUMERIC(4,3) NOT NULL,
  votos_a_favor                      SMALLINT NOT NULL,
  votos_en_contra                    SMALLINT NOT NULL,
  abstenciones                       SMALLINT NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  referencia_afectada_id             UUID,
  abierto_en                         TIMESTAMPTZ DEFAULT now() NOT NULL,
  cierra_en                          TIMESTAMPTZ NOT NULL,
  resuelto_en                        TIMESTAMPTZ,
  ejecutado_en                       TIMESTAMPTZ,
  CONSTRAINT pk_acuerdo PRIMARY KEY (id),
  CONSTRAINT ck_acuerdo_tipo CHECK (tipo IN ('ADMISION_REEMPLAZO', 'CAMBIO_FECHA_COBRO', 'CAMBIO_MONTO', 'CAMBIO_REGLAMENTO', 'CONDONACION_MORA', 'DISOLUCION_ANTICIPADA', 'EXPULSION_PARTICIPANTE', 'PERMUTA_TURNOS')),
  CONSTRAINT ck_acuerdo_estado CHECK (estado IN ('ABIERTO', 'APROBADO', 'EJECUTADO', 'EXPIRADO', 'RECHAZADO'))
);

COMMENT ON TABLE grupos.acuerdo IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.acuerdo.id IS 'PK';
COMMENT ON COLUMN grupos.acuerdo.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN grupos.acuerdo.tipo IS 'CK';
COMMENT ON COLUMN grupos.acuerdo.propuesto_por IS 'FK';
COMMENT ON COLUMN grupos.acuerdo.estado IS 'CK';
COMMENT ON COLUMN grupos.acuerdo.referencia_afectada_id IS 'NULL, polimorfica';
COMMENT ON COLUMN grupos.acuerdo.resuelto_en IS 'NULL';
COMMENT ON COLUMN grupos.acuerdo.ejecutado_en IS 'NULL';
