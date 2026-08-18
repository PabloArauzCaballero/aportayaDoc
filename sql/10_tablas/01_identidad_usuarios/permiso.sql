-- permiso · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: Permiso
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.permiso (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(60) NOT NULL,
  descripcion                        VARCHAR(160) NOT NULL,
  recurso                            VARCHAR(40) NOT NULL,
  accion                             VARCHAR(30) NOT NULL,
  requiere_mfa                       BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_permiso PRIMARY KEY (id)
);

COMMENT ON TABLE identidad.permiso IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.permiso.id IS 'PK';
COMMENT ON COLUMN identidad.permiso.codigo IS 'UQ';
