-- rol_permiso · módulo 01 — Identidad, Usuarios y Seguridad
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.rol_permiso (
  rol_id                             UUID DEFAULT gen_random_uuid() NOT NULL,
  permiso_id                         UUID DEFAULT gen_random_uuid() NOT NULL,
  CONSTRAINT pk_rol_permiso PRIMARY KEY (rol_id, permiso_id)
);

COMMENT ON TABLE identidad.rol_permiso IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.rol_permiso.rol_id IS 'PK, FK';
COMMENT ON COLUMN identidad.rol_permiso.permiso_id IS 'PK, FK';
