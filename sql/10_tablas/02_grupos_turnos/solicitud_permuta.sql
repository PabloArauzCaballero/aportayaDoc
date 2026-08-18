-- solicitud_permuta · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- clase de dominio: SolicitudPermuta
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.solicitud_permuta (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  turno_origen_id                    UUID NOT NULL,
  turno_destino_id                   UUID NOT NULL,
  solicitante_id                     UUID NOT NULL,
  contraparte_id                     UUID NOT NULL,
  motivo                             VARCHAR(200) NOT NULL,
  compensacion_ofrecida              NUMERIC(14,2),
  estado                             VARCHAR(20) NOT NULL,
  aprobada_por_organizador           BOOLEAN DEFAULT FALSE NOT NULL,
  fecha_solicitud                    TIMESTAMPTZ NOT NULL,
  fecha_ejecucion                    TIMESTAMPTZ,
  CONSTRAINT pk_solicitud_permuta PRIMARY KEY (id),
  CONSTRAINT ck_solicitud_permuta_estado CHECK (estado IN ('ACEPTADA', 'APROBADA_ORG', 'EJECUTADA', 'PENDIENTE', 'RECHAZADA'))
);

COMMENT ON TABLE grupos.solicitud_permuta IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.solicitud_permuta.id IS 'PK';
COMMENT ON COLUMN grupos.solicitud_permuta.turno_origen_id IS 'FK, IDX';
COMMENT ON COLUMN grupos.solicitud_permuta.turno_destino_id IS 'FK';
COMMENT ON COLUMN grupos.solicitud_permuta.solicitante_id IS 'FK';
COMMENT ON COLUMN grupos.solicitud_permuta.contraparte_id IS 'FK';
COMMENT ON COLUMN grupos.solicitud_permuta.compensacion_ofrecida IS 'NULL';
COMMENT ON COLUMN grupos.solicitud_permuta.estado IS 'CK';
COMMENT ON COLUMN grupos.solicitud_permuta.fecha_ejecucion IS 'NULL';
