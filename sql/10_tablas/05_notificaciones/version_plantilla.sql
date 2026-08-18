-- version_plantilla · módulo 05 — Notificaciones y Comunicaciones
-- clase de dominio: VersionPlantilla
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS notificaciones.version_plantilla (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  plantilla_id                       UUID NOT NULL,
  version                            SMALLINT DEFAULT 0 NOT NULL,
  idioma                             VARCHAR(10) NOT NULL,
  asunto                             VARCHAR(160),
  cuerpo                             TEXT NOT NULL,
  variables                          JSONB NOT NULL,
  botones                            JSONB,
  url_encabezado_media               VARCHAR(255),
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  CONSTRAINT pk_version_plantilla PRIMARY KEY (id)
);

COMMENT ON TABLE notificaciones.version_plantilla IS 'Módulo 05 — Notificaciones y Comunicaciones. WhatsApp como canal real de cobro, sin spam ni doble aviso';
COMMENT ON COLUMN notificaciones.version_plantilla.id IS 'PK';
COMMENT ON COLUMN notificaciones.version_plantilla.plantilla_id IS 'FK, IDX';
COMMENT ON COLUMN notificaciones.version_plantilla.version IS 'UQ+plantilla_id+idioma';
COMMENT ON COLUMN notificaciones.version_plantilla.asunto IS 'NULL';
COMMENT ON COLUMN notificaciones.version_plantilla.botones IS 'NULL';
COMMENT ON COLUMN notificaciones.version_plantilla.url_encabezado_media IS 'NULL';
COMMENT ON COLUMN notificaciones.version_plantilla.vigente_hasta IS 'NULL';
