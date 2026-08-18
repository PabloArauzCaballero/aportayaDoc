-- notificacion · módulo 05 — Notificaciones y Comunicaciones
-- clase de dominio: Notificacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS notificaciones.notificacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  evento_id                          UUID NOT NULL,
  prioridad                          VARCHAR(10) NOT NULL,
  contexto                           JSONB NOT NULL,
  clave_deduplicacion                VARCHAR(120) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  programada_para                    TIMESTAMPTZ NOT NULL,
  creada_en                          TIMESTAMPTZ DEFAULT now() NOT NULL,
  finalizada_en                      TIMESTAMPTZ,
  correlation_id                     UUID NOT NULL,
  CONSTRAINT pk_notificacion PRIMARY KEY (id),
  CONSTRAINT ck_notificacion_prioridad CHECK (prioridad IN ('ALTA', 'BAJA', 'CRITICA', 'NORMAL')),
  CONSTRAINT ck_notificacion_estado CHECK (estado IN ('CANCELADA', 'CREADA', 'ENTREGADA', 'ENVIADA', 'EN_COLA', 'FALLIDA', 'LEIDA', 'PROGRAMADA', 'SUPRIMIDA'))
);

COMMENT ON TABLE notificaciones.notificacion IS 'Módulo 05 — Notificaciones y Comunicaciones. WhatsApp como canal real de cobro, sin spam ni doble aviso';
COMMENT ON COLUMN notificaciones.notificacion.id IS 'PK';
COMMENT ON COLUMN notificaciones.notificacion.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN notificaciones.notificacion.evento_id IS 'FK';
COMMENT ON COLUMN notificaciones.notificacion.prioridad IS 'CK';
COMMENT ON COLUMN notificaciones.notificacion.clave_deduplicacion IS 'UQ parcial';
COMMENT ON COLUMN notificaciones.notificacion.estado IS 'CK, IDX';
COMMENT ON COLUMN notificaciones.notificacion.programada_para IS 'IDX';
COMMENT ON COLUMN notificaciones.notificacion.creada_en IS 'IDX, particion';
COMMENT ON COLUMN notificaciones.notificacion.finalizada_en IS 'NULL';
COMMENT ON COLUMN notificaciones.notificacion.correlation_id IS 'IDX';
