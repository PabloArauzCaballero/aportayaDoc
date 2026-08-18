-- envio_notificacion · módulo 05 — Notificaciones y Comunicaciones
-- clase de dominio: EnvioNotificacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS notificaciones.envio_notificacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  notificacion_id                    UUID NOT NULL,
  proveedor_id                       UUID NOT NULL,
  version_plantilla_id               UUID NOT NULL,
  canal_vinculado_id                 UUID,
  canal                              VARCHAR(15) NOT NULL,
  destinatario                       VARCHAR(150) NOT NULL,
  clave_idempotencia                 VARCHAR(120) NOT NULL,
  encolado_en                        TIMESTAMPTZ NOT NULL,
  contenido_enviado                  TEXT NOT NULL,
  estado                             VARCHAR(25) NOT NULL,
  id_mensaje_proveedor               VARCHAR(120),
  orden                              SMALLINT NOT NULL,
  intentos                           SMALLINT DEFAULT 0 NOT NULL,
  max_intentos                       SMALLINT NOT NULL,
  costo                              NUMERIC(10,4) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  codigo_error                       VARCHAR(40),
  enviado_en                         TIMESTAMPTZ,
  entregado_en                       TIMESTAMPTZ,
  leido_en                           TIMESTAMPTZ,
  proximo_reintento_en               TIMESTAMPTZ,
  CONSTRAINT pk_envio_notificacion PRIMARY KEY (id),
  CONSTRAINT ck_envio_notificacion_canal CHECK (canal IN ('CORREO', 'IN_APP', 'LLAMADA_VOZ', 'PUSH', 'SMS', 'WHATSAPP')),
  CONSTRAINT ck_envio_notificacion_estado CHECK (estado IN ('ACEPTADO_POR_PROVEEDOR', 'ENTREGADO', 'ENVIADO', 'EXPIRADO', 'FALLIDO', 'LEIDO', 'PENDIENTE', 'RECHAZADO'))
);

COMMENT ON TABLE notificaciones.envio_notificacion IS 'Módulo 05 — Notificaciones y Comunicaciones. WhatsApp como canal real de cobro, sin spam ni doble aviso';
COMMENT ON COLUMN notificaciones.envio_notificacion.id IS 'PK';
COMMENT ON COLUMN notificaciones.envio_notificacion.notificacion_id IS 'FK, IDX';
COMMENT ON COLUMN notificaciones.envio_notificacion.proveedor_id IS 'FK';
COMMENT ON COLUMN notificaciones.envio_notificacion.version_plantilla_id IS 'FK';
COMMENT ON COLUMN notificaciones.envio_notificacion.canal_vinculado_id IS 'FK, NULL';
COMMENT ON COLUMN notificaciones.envio_notificacion.canal IS 'CK';
COMMENT ON COLUMN notificaciones.envio_notificacion.estado IS 'CK, IDX';
COMMENT ON COLUMN notificaciones.envio_notificacion.id_mensaje_proveedor IS 'UQ, NULL';
COMMENT ON COLUMN notificaciones.envio_notificacion.codigo_error IS 'NULL';
COMMENT ON COLUMN notificaciones.envio_notificacion.enviado_en IS 'NULL';
COMMENT ON COLUMN notificaciones.envio_notificacion.entregado_en IS 'NULL';
COMMENT ON COLUMN notificaciones.envio_notificacion.leido_en IS 'NULL';
COMMENT ON COLUMN notificaciones.envio_notificacion.proximo_reintento_en IS 'NULL, IDX';
