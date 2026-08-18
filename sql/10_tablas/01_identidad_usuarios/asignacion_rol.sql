-- asignacion_rol · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: AsignacionRol
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.asignacion_rol (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  rol_id                             UUID NOT NULL,
  ambito                             VARCHAR(15) NOT NULL,
  ambito_id                          UUID,
  otorgada_por                       UUID NOT NULL,
  otorgada_en                        TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  revocada_en                        TIMESTAMPTZ,
  motivo_revocacion                  VARCHAR(120),
  CONSTRAINT pk_asignacion_rol PRIMARY KEY (id),
  CONSTRAINT ck_asignacion_rol_ambito CHECK (ambito IN ('GLOBAL', 'GRUPO', 'ORGANIZACION'))
);

COMMENT ON TABLE identidad.asignacion_rol IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.asignacion_rol.id IS 'PK';
COMMENT ON COLUMN identidad.asignacion_rol.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN identidad.asignacion_rol.rol_id IS 'FK';
COMMENT ON COLUMN identidad.asignacion_rol.ambito IS 'CK';
COMMENT ON COLUMN identidad.asignacion_rol.ambito_id IS 'NULL, grupo_id (M2)';
COMMENT ON COLUMN identidad.asignacion_rol.otorgada_por IS 'FK';
COMMENT ON COLUMN identidad.asignacion_rol.vigente_hasta IS 'NULL';
COMMENT ON COLUMN identidad.asignacion_rol.revocada_en IS 'NULL';
COMMENT ON COLUMN identidad.asignacion_rol.motivo_revocacion IS 'NULL';
