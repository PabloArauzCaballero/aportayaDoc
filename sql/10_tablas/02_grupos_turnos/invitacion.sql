-- invitacion · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- clase de dominio: Invitacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.invitacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  telefono_invitado                  VARCHAR(20) NOT NULL,
  nombre_sugerido                    VARCHAR(80),
  emisor_id                          UUID NOT NULL,
  token_id                           UUID NOT NULL,
  canal                              VARCHAR(15) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  envios_realizados                  SMALLINT NOT NULL,
  fecha_envio                        TIMESTAMPTZ NOT NULL,
  fecha_expiracion                   TIMESTAMPTZ NOT NULL,
  fecha_respuesta                    TIMESTAMPTZ,
  CONSTRAINT pk_invitacion PRIMARY KEY (id),
  CONSTRAINT ck_invitacion_canal CHECK (canal IN ('ENLACE', 'SMS', 'WHATSAPP')),
  CONSTRAINT ck_invitacion_estado CHECK (estado IN ('ACEPTADA', 'ENVIADA', 'EXPIRADA', 'PENDIENTE', 'RECHAZADA', 'REVOCADA'))
);

COMMENT ON TABLE grupos.invitacion IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.invitacion.id IS 'PK';
COMMENT ON COLUMN grupos.invitacion.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN grupos.invitacion.telefono_invitado IS 'IDX';
COMMENT ON COLUMN grupos.invitacion.nombre_sugerido IS 'NULL';
COMMENT ON COLUMN grupos.invitacion.emisor_id IS 'FK';
COMMENT ON COLUMN grupos.invitacion.token_id IS 'FK, UQ, M1';
COMMENT ON COLUMN grupos.invitacion.canal IS 'CK';
COMMENT ON COLUMN grupos.invitacion.estado IS 'CK';
COMMENT ON COLUMN grupos.invitacion.fecha_respuesta IS 'NULL';
