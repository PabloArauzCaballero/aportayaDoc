-- credencial_acceso · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: CredencialAcceso
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.credencial_acceso (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  hash_contrasena                    VARCHAR(255) NOT NULL,
  algoritmo                          VARCHAR(20) NOT NULL,
  parametros_kdf                     JSONB NOT NULL,
  requiere_cambio                    BOOLEAN DEFAULT FALSE NOT NULL,
  cambiada_en                        TIMESTAMPTZ NOT NULL,
  expira_en                          TIMESTAMPTZ,
  CONSTRAINT pk_credencial_acceso PRIMARY KEY (id)
);

COMMENT ON TABLE identidad.credencial_acceso IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.credencial_acceso.id IS 'PK';
COMMENT ON COLUMN identidad.credencial_acceso.usuario_id IS 'FK, UQ';
COMMENT ON COLUMN identidad.credencial_acceso.expira_en IS 'NULL';
