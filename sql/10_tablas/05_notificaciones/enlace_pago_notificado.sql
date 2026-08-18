-- enlace_pago_notificado · módulo 05 — Notificaciones y Comunicaciones
-- clase de dominio: EnlacePagoNotificado
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS notificaciones.enlace_pago_notificado (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  notificacion_id                    UUID NOT NULL,
  orden_cobro_id                     UUID NOT NULL,
  token_id                           UUID NOT NULL,
  url_corta                          VARCHAR(60) NOT NULL,
  clicks                             SMALLINT DEFAULT 0 NOT NULL,
  primer_click_en                    TIMESTAMPTZ,
  convertido_en_pago                 BOOLEAN DEFAULT FALSE NOT NULL,
  expira_en                          TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_enlace_pago_notificado PRIMARY KEY (id)
);

COMMENT ON TABLE notificaciones.enlace_pago_notificado IS 'Módulo 05 — Notificaciones y Comunicaciones. WhatsApp como canal real de cobro, sin spam ni doble aviso';
COMMENT ON COLUMN notificaciones.enlace_pago_notificado.id IS 'PK';
COMMENT ON COLUMN notificaciones.enlace_pago_notificado.notificacion_id IS 'FK, UQ';
COMMENT ON COLUMN notificaciones.enlace_pago_notificado.orden_cobro_id IS 'FK, M3';
COMMENT ON COLUMN notificaciones.enlace_pago_notificado.token_id IS 'FK, UQ, M1';
COMMENT ON COLUMN notificaciones.enlace_pago_notificado.url_corta IS 'UQ';
COMMENT ON COLUMN notificaciones.enlace_pago_notificado.primer_click_en IS 'NULL';
