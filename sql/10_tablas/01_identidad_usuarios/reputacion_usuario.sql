-- reputacion_usuario · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: ReputacionUsuario
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.reputacion_usuario (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  puntaje                            NUMERIC(6,2) NOT NULL,
  indice_puntualidad                 NUMERIC(5,2) NOT NULL,
  total_obligaciones                 INTEGER DEFAULT 0 NOT NULL,
  obligaciones_cumplidas             INTEGER NOT NULL,
  obligaciones_en_mora               INTEGER NOT NULL,
  incumplimientos_graves             INTEGER NOT NULL,
  grupos_completados                 INTEGER NOT NULL,
  version_modelo                     VARCHAR(20) NOT NULL,
  calculado_en                       TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_reputacion_usuario PRIMARY KEY (id)
);

COMMENT ON TABLE identidad.reputacion_usuario IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.reputacion_usuario.id IS 'PK';
COMMENT ON COLUMN identidad.reputacion_usuario.usuario_id IS 'FK, UQ';
