-- plantilla_mensaje · módulo 05 — Notificaciones y Comunicaciones
-- clase de dominio: PlantillaMensaje
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS notificaciones.plantilla_mensaje (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(50) NOT NULL,
  evento_id                          UUID NOT NULL,
  canal                              VARCHAR(15) NOT NULL,
  descripcion                        VARCHAR(200) NOT NULL,
  categoria_proveedor                VARCHAR(20) NOT NULL,
  estado_aprobacion                  VARCHAR(15) NOT NULL,
  id_plantilla_proveedor             VARCHAR(80),
  activa                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_plantilla_mensaje PRIMARY KEY (id),
  CONSTRAINT ck_plantilla_mensaje_canal CHECK (canal IN ('CORREO', 'IN_APP', 'LLAMADA_VOZ', 'PUSH', 'SMS', 'WHATSAPP')),
  CONSTRAINT ck_plantilla_mensaje_categoria_proveedor CHECK (categoria_proveedor IN ('AUTHENTICATION', 'MARKETING', 'UTILITY')),
  CONSTRAINT ck_plantilla_mensaje_estado_aprobacion CHECK (estado_aprobacion IN ('APROBADA', 'BORRADOR', 'ENVIADA', 'PAUSADA', 'RECHAZADA'))
);

COMMENT ON TABLE notificaciones.plantilla_mensaje IS 'Módulo 05 — Notificaciones y Comunicaciones. WhatsApp como canal real de cobro, sin spam ni doble aviso';
COMMENT ON COLUMN notificaciones.plantilla_mensaje.id IS 'PK';
COMMENT ON COLUMN notificaciones.plantilla_mensaje.codigo IS 'UQ';
COMMENT ON COLUMN notificaciones.plantilla_mensaje.evento_id IS 'FK, IDX';
COMMENT ON COLUMN notificaciones.plantilla_mensaje.canal IS 'CK';
COMMENT ON COLUMN notificaciones.plantilla_mensaje.categoria_proveedor IS 'CK';
COMMENT ON COLUMN notificaciones.plantilla_mensaje.estado_aprobacion IS 'CK';
COMMENT ON COLUMN notificaciones.plantilla_mensaje.id_plantilla_proveedor IS 'NULL';
