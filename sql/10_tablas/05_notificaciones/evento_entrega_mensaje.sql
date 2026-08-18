-- evento_entrega_mensaje · módulo 05 — Notificaciones y Comunicaciones
-- clase de dominio: EventoEntregaMensaje
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS notificaciones.evento_entrega_mensaje (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  envio_id                           UUID NOT NULL,
  tipo_evento                        VARCHAR(20) NOT NULL,
  fecha_hora_proveedor               TIMESTAMPTZ NOT NULL,
  recibido_en                        TIMESTAMPTZ DEFAULT now() NOT NULL,
  payload_crudo                      JSONB NOT NULL,
  codigo_error                       VARCHAR(40),
  clave_idempotencia                 VARCHAR(120) NOT NULL,
  CONSTRAINT pk_evento_entrega_mensaje PRIMARY KEY (id),
  CONSTRAINT ck_evento_entrega_mensaje_tipo_evento CHECK (tipo_evento IN ('ENTREGADO', 'ENVIADO', 'EXPIRADO', 'FALLIDO', 'LEIDO', 'RECHAZADO'))
);

COMMENT ON TABLE notificaciones.evento_entrega_mensaje IS 'Módulo 05 — Notificaciones y Comunicaciones. WhatsApp como canal real de cobro, sin spam ni doble aviso';
COMMENT ON COLUMN notificaciones.evento_entrega_mensaje.id IS 'PK';
COMMENT ON COLUMN notificaciones.evento_entrega_mensaje.envio_id IS 'FK, IDX';
COMMENT ON COLUMN notificaciones.evento_entrega_mensaje.tipo_evento IS 'CK';
COMMENT ON COLUMN notificaciones.evento_entrega_mensaje.codigo_error IS 'NULL';
