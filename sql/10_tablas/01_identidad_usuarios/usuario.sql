-- usuario · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: Usuario
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.usuario (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo_publico                     VARCHAR(12) NOT NULL,
  nombres                            VARCHAR(80) NOT NULL,
  apellidos                          VARCHAR(80) NOT NULL,
  telefono_e164                      VARCHAR(20) NOT NULL,
  correo                             VARCHAR(150),
  fecha_nacimiento                   DATE NOT NULL,
  estado                             VARCHAR(25) NOT NULL,
  nivel_kyc                          VARCHAR(15) NOT NULL,
  idioma                             VARCHAR(10) NOT NULL,
  zona_horaria                       VARCHAR(40) NOT NULL,
  url_avatar                         VARCHAR(255),
  telefono_verificado_en             TIMESTAMPTZ,
  correo_verificado_en               TIMESTAMPTZ,
  ultimo_acceso_en                   TIMESTAMPTZ,
  fecha_registro                     TIMESTAMPTZ NOT NULL,
  eliminado_en                       TIMESTAMPTZ,
  version                            INTEGER DEFAULT 0 NOT NULL,
  CONSTRAINT pk_usuario PRIMARY KEY (id),
  CONSTRAINT ck_usuario_estado CHECK (estado IN ('ACTIVO', 'BAJA_VOLUNTARIA', 'BLOQUEADO_SEGURIDAD', 'ELIMINADO', 'INCOMPLETO_KYC', 'PENDIENTE_VERIFICACION', 'PRE_REGISTRO', 'SUSPENDIDO')),
  CONSTRAINT ck_usuario_nivel_kyc CHECK (nivel_kyc IN ('BASICO', 'COMPLETO', 'INTERMEDIO', 'NINGUNO'))
);

COMMENT ON TABLE identidad.usuario IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.usuario.id IS 'PK';
COMMENT ON COLUMN identidad.usuario.codigo_publico IS 'UQ';
COMMENT ON COLUMN identidad.usuario.telefono_e164 IS 'UQ, IDX';
COMMENT ON COLUMN identidad.usuario.correo IS 'UQ, NULL';
COMMENT ON COLUMN identidad.usuario.estado IS 'CK';
COMMENT ON COLUMN identidad.usuario.nivel_kyc IS 'CK';
COMMENT ON COLUMN identidad.usuario.url_avatar IS 'NULL';
COMMENT ON COLUMN identidad.usuario.telefono_verificado_en IS 'NULL';
COMMENT ON COLUMN identidad.usuario.correo_verificado_en IS 'NULL';
COMMENT ON COLUMN identidad.usuario.ultimo_acceso_en IS 'NULL';
COMMENT ON COLUMN identidad.usuario.eliminado_en IS 'NULL';
