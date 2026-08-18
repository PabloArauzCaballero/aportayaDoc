-- direccion_usuario · módulo 01 — Identidad, Usuarios y Seguridad
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.direccion_usuario (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  departamento                       VARCHAR(60) NOT NULL,
  ciudad                             VARCHAR(60) NOT NULL,
  zona                               VARCHAR(80) NOT NULL,
  detalle                            VARCHAR(160) NOT NULL,
  latitud                            NUMERIC(9,6),
  longitud                           NUMERIC(9,6),
  CONSTRAINT pk_direccion_usuario PRIMARY KEY (id)
);

COMMENT ON TABLE identidad.direccion_usuario IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.direccion_usuario.id IS 'PK';
COMMENT ON COLUMN identidad.direccion_usuario.usuario_id IS 'FK, UQ';
COMMENT ON COLUMN identidad.direccion_usuario.latitud IS 'NULL';
COMMENT ON COLUMN identidad.direccion_usuario.longitud IS 'NULL';
