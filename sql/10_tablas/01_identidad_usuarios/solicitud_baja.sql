-- solicitud_baja · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: SolicitudBaja
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.solicitud_baja (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  motivo                             VARCHAR(160) NOT NULL,
  solicitada_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  fecha_efectiva                     TIMESTAMPTZ,
  bloqueada_por_obligaciones         BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_solicitud_baja PRIMARY KEY (id)
);

COMMENT ON TABLE identidad.solicitud_baja IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.solicitud_baja.id IS 'PK';
COMMENT ON COLUMN identidad.solicitud_baja.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN identidad.solicitud_baja.fecha_efectiva IS 'NULL';
