-- intento_validacion_token · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: IntentoValidacionToken
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.intento_validacion_token (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  token_id                           UUID NOT NULL,
  fecha_hora                         TIMESTAMPTZ NOT NULL,
  resultado                          VARCHAR(30) NOT NULL,
  ip_origen                          INET NOT NULL,
  agente_usuario                     VARCHAR(255) NOT NULL,
  huella_dispositivo                 VARCHAR(128),
  CONSTRAINT pk_intento_validacion_token PRIMARY KEY (id),
  CONSTRAINT ck_intento_validacion_token_resultado CHECK (resultado IN ('BLOQUEADO_POR_INTENTOS', 'CANAL_NO_COINCIDE', 'CODIGO_INCORRECTO', 'DISPOSITIVO_NO_COINCIDE', 'EXPIRADO', 'NO_ENCONTRADO', 'VALIDO', 'YA_CONSUMIDO'))
);

COMMENT ON TABLE identidad.intento_validacion_token IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.intento_validacion_token.id IS 'PK';
COMMENT ON COLUMN identidad.intento_validacion_token.token_id IS 'FK, IDX';
COMMENT ON COLUMN identidad.intento_validacion_token.resultado IS 'CK';
COMMENT ON COLUMN identidad.intento_validacion_token.huella_dispositivo IS 'NULL';
