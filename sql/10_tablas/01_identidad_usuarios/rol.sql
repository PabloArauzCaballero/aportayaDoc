-- rol · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: Rol
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.rol (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(30) NOT NULL,
  nombre                             VARCHAR(60) NOT NULL,
  ambito                             VARCHAR(15) NOT NULL,
  es_sistema                         BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_rol PRIMARY KEY (id),
  CONSTRAINT ck_rol_ambito CHECK (ambito IN ('GLOBAL', 'GRUPO', 'ORGANIZACION'))
);

COMMENT ON TABLE identidad.rol IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.rol.id IS 'PK';
COMMENT ON COLUMN identidad.rol.codigo IS 'UQ';
COMMENT ON COLUMN identidad.rol.ambito IS 'CK';
