-- intento_autenticacion · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: IntentoAutenticacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.intento_autenticacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID,
  identificador_usado                VARCHAR(150) NOT NULL,
  fecha_hora                         TIMESTAMPTZ NOT NULL,
  exitoso                            BOOLEAN DEFAULT FALSE NOT NULL,
  motivo_fallo                       VARCHAR(60),
  ip_origen                          INET NOT NULL,
  agente_usuario                     VARCHAR(255) NOT NULL,
  huella_dispositivo                 VARCHAR(128),
  puntaje_riesgo                     NUMERIC(5,2) NOT NULL,
  CONSTRAINT pk_intento_autenticacion PRIMARY KEY (id)
);

COMMENT ON TABLE identidad.intento_autenticacion IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.intento_autenticacion.id IS 'PK';
COMMENT ON COLUMN identidad.intento_autenticacion.usuario_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN identidad.intento_autenticacion.identificador_usado IS 'IDX';
COMMENT ON COLUMN identidad.intento_autenticacion.fecha_hora IS 'IDX';
COMMENT ON COLUMN identidad.intento_autenticacion.motivo_fallo IS 'NULL';
COMMENT ON COLUMN identidad.intento_autenticacion.ip_origen IS 'IDX';
COMMENT ON COLUMN identidad.intento_autenticacion.huella_dispositivo IS 'NULL';
