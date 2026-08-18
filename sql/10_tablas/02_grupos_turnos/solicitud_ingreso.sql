-- solicitud_ingreso · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- clase de dominio: SolicitudIngreso
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.solicitud_ingreso (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  usuario_id                         UUID NOT NULL,
  cupos_solicitados                  SMALLINT NOT NULL,
  mensaje                            VARCHAR(300),
  estado                             VARCHAR(15) NOT NULL,
  puntaje_compatibilidad             NUMERIC(5,2),
  revisada_por                       UUID,
  fecha_solicitud                    TIMESTAMPTZ NOT NULL,
  fecha_resolucion                   TIMESTAMPTZ,
  CONSTRAINT pk_solicitud_ingreso PRIMARY KEY (id),
  CONSTRAINT ck_solicitud_ingreso_estado CHECK (estado IN ('APROBADA', 'EXPIRADA', 'PENDIENTE', 'RECHAZADA'))
);

COMMENT ON TABLE grupos.solicitud_ingreso IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.solicitud_ingreso.id IS 'PK';
COMMENT ON COLUMN grupos.solicitud_ingreso.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN grupos.solicitud_ingreso.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN grupos.solicitud_ingreso.mensaje IS 'NULL';
COMMENT ON COLUMN grupos.solicitud_ingreso.estado IS 'CK';
COMMENT ON COLUMN grupos.solicitud_ingreso.puntaje_compatibilidad IS 'NULL';
COMMENT ON COLUMN grupos.solicitud_ingreso.revisada_por IS 'FK, NULL';
COMMENT ON COLUMN grupos.solicitud_ingreso.fecha_resolucion IS 'NULL';
