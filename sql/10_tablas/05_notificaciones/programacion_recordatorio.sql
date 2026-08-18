-- programacion_recordatorio · módulo 05 — Notificaciones y Comunicaciones
-- clase de dominio: ProgramacionRecordatorio
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS notificaciones.programacion_recordatorio (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID,
  evento_id                          UUID NOT NULL,
  desfase_dias                       SMALLINT NOT NULL,
  hora_envio                         TIME NOT NULL,
  repetir_cada                       SMALLINT,
  max_repeticiones                   SMALLINT NOT NULL,
  condicion                          VARCHAR(200) NOT NULL,
  activa                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_programacion_recordatorio PRIMARY KEY (id)
);

COMMENT ON TABLE notificaciones.programacion_recordatorio IS 'Módulo 05 — Notificaciones y Comunicaciones. WhatsApp como canal real de cobro, sin spam ni doble aviso';
COMMENT ON COLUMN notificaciones.programacion_recordatorio.id IS 'PK';
COMMENT ON COLUMN notificaciones.programacion_recordatorio.grupo_id IS 'FK, NULL';
COMMENT ON COLUMN notificaciones.programacion_recordatorio.evento_id IS 'FK';
COMMENT ON COLUMN notificaciones.programacion_recordatorio.repetir_cada IS 'NULL';
