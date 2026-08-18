-- lista_supresion · módulo 05 — Notificaciones y Comunicaciones
-- clase de dominio: ListaSupresion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS notificaciones.lista_supresion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  identificador                      VARCHAR(150) NOT NULL,
  canal                              VARCHAR(15) NOT NULL,
  motivo                             VARCHAR(25) NOT NULL,
  categoria                          VARCHAR(20) NOT NULL,
  activa                             BOOLEAN DEFAULT FALSE NOT NULL,
  agregado_en                        TIMESTAMPTZ NOT NULL,
  permanente                         BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_lista_supresion PRIMARY KEY (id),
  CONSTRAINT ck_lista_supresion_canal CHECK (canal IN ('CORREO', 'IN_APP', 'LLAMADA_VOZ', 'PUSH', 'SMS', 'WHATSAPP')),
  CONSTRAINT ck_lista_supresion_motivo CHECK (motivo IN ('QUEJA_SPAM', 'REBOTE_DURO', 'SOLICITUD_LEGAL')),
  CONSTRAINT ck_lista_supresion_categoria CHECK (categoria IN ('COBRANZA', 'COMERCIAL', 'REGULATORIA', 'SEGURIDAD', 'SOPORTE', 'TODAS', 'TRANSACCIONAL'))
);

COMMENT ON TABLE notificaciones.lista_supresion IS 'Módulo 05 — Notificaciones y Comunicaciones. WhatsApp como canal real de cobro, sin spam ni doble aviso';
COMMENT ON COLUMN notificaciones.lista_supresion.id IS 'PK';
COMMENT ON COLUMN notificaciones.lista_supresion.identificador IS 'UQ+canal';
COMMENT ON COLUMN notificaciones.lista_supresion.canal IS 'CK';
COMMENT ON COLUMN notificaciones.lista_supresion.motivo IS 'CK';
COMMENT ON COLUMN notificaciones.lista_supresion.categoria IS 'CK, IDX';
COMMENT ON COLUMN notificaciones.lista_supresion.activa IS 'IDX';
