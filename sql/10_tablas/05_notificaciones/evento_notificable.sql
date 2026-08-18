-- evento_notificable · módulo 05 — Notificaciones y Comunicaciones
-- clase de dominio: EventoNotificable
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS notificaciones.evento_notificable (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  tipo                               VARCHAR(40) NOT NULL,
  descripcion                        VARCHAR(200) NOT NULL,
  categoria                          VARCHAR(20) NOT NULL,
  es_obligatorio                     BOOLEAN DEFAULT FALSE NOT NULL,
  prioridad                          VARCHAR(10) NOT NULL,
  es_transaccional                   BOOLEAN DEFAULT FALSE NOT NULL,
  permite_agrupacion                 BOOLEAN DEFAULT FALSE NOT NULL,
  ventana_deduplicacion_min          SMALLINT NOT NULL,
  canales_permitidos                 VARCHAR(120) NOT NULL,
  cadena_respaldo                    VARCHAR(120) NOT NULL,
  activo                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_evento_notificable PRIMARY KEY (id),
  CONSTRAINT ck_evento_notificable_categoria CHECK (categoria IN ('COBRANZA', 'COMERCIAL', 'REGULATORIA', 'SEGURIDAD', 'SOPORTE', 'TRANSACCIONAL')),
  CONSTRAINT ck_evento_notificable_prioridad CHECK (prioridad IN ('ALTA', 'BAJA', 'CRITICA', 'NORMAL'))
);

COMMENT ON TABLE notificaciones.evento_notificable IS 'Módulo 05 — Notificaciones y Comunicaciones. WhatsApp como canal real de cobro, sin spam ni doble aviso';
COMMENT ON COLUMN notificaciones.evento_notificable.id IS 'PK';
COMMENT ON COLUMN notificaciones.evento_notificable.tipo IS 'UQ';
COMMENT ON COLUMN notificaciones.evento_notificable.categoria IS 'CK, IDX';
COMMENT ON COLUMN notificaciones.evento_notificable.prioridad IS 'CK';
