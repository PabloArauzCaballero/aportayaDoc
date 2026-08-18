-- sesion · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: Sesion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.sesion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  dispositivo_id                     UUID NOT NULL,
  refresco_familia_id                UUID,
  iniciada_en                        TIMESTAMPTZ NOT NULL,
  ultima_actividad_en                TIMESTAMPTZ NOT NULL,
  expira_en                          TIMESTAMPTZ NOT NULL,
  ip_origen                          INET NOT NULL,
  geolocalizacion_aprox              VARCHAR(80),
  revocada_en                        TIMESTAMPTZ,
  motivo_revocacion                  VARCHAR(80),
  CONSTRAINT pk_sesion PRIMARY KEY (id)
);

COMMENT ON TABLE identidad.sesion IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.sesion.id IS 'PK';
COMMENT ON COLUMN identidad.sesion.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN identidad.sesion.dispositivo_id IS 'FK';
COMMENT ON COLUMN identidad.sesion.refresco_familia_id IS 'NULL';
COMMENT ON COLUMN identidad.sesion.expira_en IS 'IDX';
COMMENT ON COLUMN identidad.sesion.geolocalizacion_aprox IS 'NULL';
COMMENT ON COLUMN identidad.sesion.revocada_en IS 'NULL';
COMMENT ON COLUMN identidad.sesion.motivo_revocacion IS 'NULL';
