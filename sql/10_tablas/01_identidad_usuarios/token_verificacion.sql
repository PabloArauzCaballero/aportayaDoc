-- token_verificacion · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: TokenVerificacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.token_verificacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID,
  politica_id                        UUID NOT NULL,
  dispositivo_id                     UUID,
  tipo_token                         VARCHAR(20) NOT NULL,
  proposito                          VARCHAR(35) NOT NULL,
  hash_token                         VARCHAR(128) NOT NULL,
  algoritmo_hash                     VARCHAR(20) NOT NULL,
  canal_entrega                      VARCHAR(20) NOT NULL,
  destino_enmascarado                VARCHAR(40) NOT NULL,
  estado                             VARCHAR(25) NOT NULL,
  emitido_en                         TIMESTAMPTZ DEFAULT now() NOT NULL,
  expira_en                          TIMESTAMPTZ NOT NULL,
  enviado_en                         TIMESTAMPTZ,
  consumido_en                       TIMESTAMPTZ,
  invalidado_en                      TIMESTAMPTZ,
  motivo_invalidacion                VARCHAR(120),
  intentos_fallidos                  SMALLINT DEFAULT 0 NOT NULL,
  max_intentos                       SMALLINT NOT NULL,
  reenvios                           SMALLINT NOT NULL,
  ip_origen                          INET NOT NULL,
  agente_usuario                     VARCHAR(255) NOT NULL,
  correlation_id                     UUID NOT NULL,
  clave_idempotencia                 VARCHAR(80) NOT NULL,
  longitud                           SMALLINT,
  url_destino                        VARCHAR(255),
  firma_hmac                         VARCHAR(128),
  uso_unico                          BOOLEAN,
  clicks                             SMALLINT,
  familia_id                         UUID,
  rotado_de_id                       UUID,
  CONSTRAINT pk_token_verificacion PRIMARY KEY (id),
  CONSTRAINT ck_token_verificacion_tipo_token CHECK (tipo_token IN ('OTP', 'ENLACE', 'REFRESCO'))
);

COMMENT ON TABLE identidad.token_verificacion IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.token_verificacion.id IS 'PK';
COMMENT ON COLUMN identidad.token_verificacion.usuario_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN identidad.token_verificacion.politica_id IS 'FK';
COMMENT ON COLUMN identidad.token_verificacion.dispositivo_id IS 'FK, NULL';
COMMENT ON COLUMN identidad.token_verificacion.tipo_token IS 'CK: OTP|ENLACE|REFRESCO';
COMMENT ON COLUMN identidad.token_verificacion.proposito IS 'IDX';
COMMENT ON COLUMN identidad.token_verificacion.hash_token IS 'UQ';
COMMENT ON COLUMN identidad.token_verificacion.estado IS 'IDX';
COMMENT ON COLUMN identidad.token_verificacion.expira_en IS 'IDX';
COMMENT ON COLUMN identidad.token_verificacion.enviado_en IS 'NULL';
COMMENT ON COLUMN identidad.token_verificacion.consumido_en IS 'NULL';
COMMENT ON COLUMN identidad.token_verificacion.invalidado_en IS 'NULL';
COMMENT ON COLUMN identidad.token_verificacion.motivo_invalidacion IS 'NULL';
COMMENT ON COLUMN identidad.token_verificacion.correlation_id IS 'IDX';
COMMENT ON COLUMN identidad.token_verificacion.longitud IS 'NULL, subtipo OTP';
COMMENT ON COLUMN identidad.token_verificacion.url_destino IS 'NULL, subtipo ENLACE';
COMMENT ON COLUMN identidad.token_verificacion.firma_hmac IS 'NULL, subtipo ENLACE';
COMMENT ON COLUMN identidad.token_verificacion.uso_unico IS 'NULL, subtipo ENLACE';
COMMENT ON COLUMN identidad.token_verificacion.clicks IS 'NULL, subtipo ENLACE';
COMMENT ON COLUMN identidad.token_verificacion.familia_id IS 'NULL, subtipo REFRESCO';
COMMENT ON COLUMN identidad.token_verificacion.rotado_de_id IS 'FK, NULL, subtipo REFRESCO';
