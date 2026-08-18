-- solicitud_datos_personales · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: SolicitudDatosPersonales
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.solicitud_datos_personales (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  atendida_por                       UUID,
  tipo                               VARCHAR(20) NOT NULL,
  descripcion                        VARCHAR(400) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  fecha_limite_legal                 TIMESTAMPTZ NOT NULL,
  respuesta                          TEXT,
  recibida_en                        TIMESTAMPTZ NOT NULL,
  atendida_en                        TIMESTAMPTZ,
  CONSTRAINT pk_solicitud_datos_personales PRIMARY KEY (id),
  CONSTRAINT ck_solicitud_datos_personales_tipo CHECK (tipo IN ('ACCESO', 'CANCELACION', 'OPOSICION', 'PORTABILIDAD', 'RECTIFICACION')),
  CONSTRAINT ck_solicitud_datos_personales_estado CHECK (estado IN ('ATENDIDA', 'EN_PROCESO', 'PARCIAL', 'RECHAZADA', 'RECIBIDA'))
);

COMMENT ON TABLE auditoria.solicitud_datos_personales IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.solicitud_datos_personales.id IS 'PK';
COMMENT ON COLUMN auditoria.solicitud_datos_personales.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN auditoria.solicitud_datos_personales.atendida_por IS 'FK, NULL';
COMMENT ON COLUMN auditoria.solicitud_datos_personales.tipo IS 'CK';
COMMENT ON COLUMN auditoria.solicitud_datos_personales.estado IS 'CK, IDX';
COMMENT ON COLUMN auditoria.solicitud_datos_personales.fecha_limite_legal IS 'IDX';
COMMENT ON COLUMN auditoria.solicitud_datos_personales.respuesta IS 'NULL';
COMMENT ON COLUMN auditoria.solicitud_datos_personales.atendida_en IS 'NULL';
