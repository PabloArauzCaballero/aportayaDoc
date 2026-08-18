-- dispositivo · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: Dispositivo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.dispositivo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  huella                             VARCHAR(128) NOT NULL,
  plataforma                         VARCHAR(15) NOT NULL,
  modelo                             VARCHAR(60) NOT NULL,
  version_app                        VARCHAR(20) NOT NULL,
  token_push                         VARCHAR(255),
  es_confiable                       BOOLEAN DEFAULT FALSE NOT NULL,
  autorizado_en                      TIMESTAMPTZ,
  ultimo_uso_en                      TIMESTAMPTZ NOT NULL,
  revocado_en                        TIMESTAMPTZ,
  CONSTRAINT pk_dispositivo PRIMARY KEY (id),
  CONSTRAINT ck_dispositivo_plataforma CHECK (plataforma IN ('ANDROID', 'IOS', 'WEB'))
);

COMMENT ON TABLE identidad.dispositivo IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.dispositivo.id IS 'PK';
COMMENT ON COLUMN identidad.dispositivo.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN identidad.dispositivo.huella IS 'UQ+usuario_id';
COMMENT ON COLUMN identidad.dispositivo.plataforma IS 'CK';
COMMENT ON COLUMN identidad.dispositivo.token_push IS 'NULL';
COMMENT ON COLUMN identidad.dispositivo.autorizado_en IS 'NULL';
COMMENT ON COLUMN identidad.dispositivo.revocado_en IS 'NULL';
