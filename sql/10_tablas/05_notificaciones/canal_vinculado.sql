-- canal_vinculado · módulo 05 — Notificaciones y Comunicaciones
-- clase de dominio: CanalVinculado
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS notificaciones.canal_vinculado (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  tipo                               VARCHAR(15) NOT NULL,
  identificador                      VARCHAR(150) NOT NULL,
  verificado                         BOOLEAN DEFAULT FALSE NOT NULL,
  verificado_en                      TIMESTAMPTZ,
  opt_in_en                          TIMESTAMPTZ,
  opt_out_en                         TIMESTAMPTZ,
  motivo_opt_out                     VARCHAR(120),
  ventana_conversacion_hasta         TIMESTAMPTZ,
  rebotes_consecutivos               SMALLINT NOT NULL,
  estado                             VARCHAR(25) NOT NULL,
  CONSTRAINT pk_canal_vinculado PRIMARY KEY (id),
  CONSTRAINT ck_canal_vinculado_tipo CHECK (tipo IN ('CORREO', 'IN_APP', 'LLAMADA_VOZ', 'PUSH', 'SMS', 'WHATSAPP')),
  CONSTRAINT ck_canal_vinculado_estado CHECK (estado IN ('ACTIVO', 'BLOQUEADO_POR_USUARIO', 'DADO_DE_BAJA', 'NO_ALCANZABLE'))
);

COMMENT ON TABLE notificaciones.canal_vinculado IS 'Módulo 05 — Notificaciones y Comunicaciones. WhatsApp como canal real de cobro, sin spam ni doble aviso';
COMMENT ON COLUMN notificaciones.canal_vinculado.id IS 'PK';
COMMENT ON COLUMN notificaciones.canal_vinculado.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN notificaciones.canal_vinculado.tipo IS 'CK';
COMMENT ON COLUMN notificaciones.canal_vinculado.identificador IS 'UQ+tipo';
COMMENT ON COLUMN notificaciones.canal_vinculado.verificado_en IS 'NULL';
COMMENT ON COLUMN notificaciones.canal_vinculado.opt_in_en IS 'NULL';
COMMENT ON COLUMN notificaciones.canal_vinculado.opt_out_en IS 'NULL';
COMMENT ON COLUMN notificaciones.canal_vinculado.motivo_opt_out IS 'NULL';
COMMENT ON COLUMN notificaciones.canal_vinculado.ventana_conversacion_hasta IS 'NULL';
COMMENT ON COLUMN notificaciones.canal_vinculado.estado IS 'CK, IDX';
