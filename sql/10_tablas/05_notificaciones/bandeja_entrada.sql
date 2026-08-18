-- bandeja_entrada · módulo 05 — Notificaciones y Comunicaciones
-- clase de dominio: BandejaEntrada
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS notificaciones.bandeja_entrada (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  notificacion_id                    UUID NOT NULL,
  titulo                             VARCHAR(120) NOT NULL,
  resumen                            VARCHAR(300) NOT NULL,
  url_accion                         VARCHAR(255),
  leida                              BOOLEAN DEFAULT FALSE NOT NULL,
  leida_en                           TIMESTAMPTZ,
  archivada                          BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_bandeja_entrada PRIMARY KEY (id)
);

COMMENT ON TABLE notificaciones.bandeja_entrada IS 'Módulo 05 — Notificaciones y Comunicaciones. WhatsApp como canal real de cobro, sin spam ni doble aviso';
COMMENT ON COLUMN notificaciones.bandeja_entrada.id IS 'PK';
COMMENT ON COLUMN notificaciones.bandeja_entrada.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN notificaciones.bandeja_entrada.notificacion_id IS 'FK, UQ';
COMMENT ON COLUMN notificaciones.bandeja_entrada.url_accion IS 'NULL';
COMMENT ON COLUMN notificaciones.bandeja_entrada.leida IS 'IDX';
COMMENT ON COLUMN notificaciones.bandeja_entrada.leida_en IS 'NULL';
