-- cola_muerta · módulo 05 — Notificaciones y Comunicaciones
-- clase de dominio: ColaMuerta
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS notificaciones.cola_muerta (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  envio_id                           UUID NOT NULL,
  motivo                             VARCHAR(200) NOT NULL,
  payload                            JSONB NOT NULL,
  fecha                              TIMESTAMPTZ NOT NULL,
  reprocesado_en                     TIMESTAMPTZ,
  CONSTRAINT pk_cola_muerta PRIMARY KEY (id)
);

COMMENT ON TABLE notificaciones.cola_muerta IS 'Módulo 05 — Notificaciones y Comunicaciones. WhatsApp como canal real de cobro, sin spam ni doble aviso';
COMMENT ON COLUMN notificaciones.cola_muerta.id IS 'PK';
COMMENT ON COLUMN notificaciones.cola_muerta.envio_id IS 'FK, IDX';
COMMENT ON COLUMN notificaciones.cola_muerta.reprocesado_en IS 'NULL';
