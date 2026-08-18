-- incidente_operativo · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: IncidenteOperativo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.incidente_operativo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(20) NOT NULL,
  titulo                             VARCHAR(160) NOT NULL,
  severidad                          VARCHAR(6) NOT NULL,
  sistema_afectado                   VARCHAR(60) NOT NULL,
  descripcion                        TEXT NOT NULL,
  impacto_usuarios                   INTEGER DEFAULT 0 NOT NULL,
  impacto_monetario                  NUMERIC(16,2) DEFAULT 0 NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  detectado_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  resuelto_en                        TIMESTAMPTZ,
  causa_raiz                         TEXT,
  acciones_correctivas               TEXT,
  CONSTRAINT pk_incidente_operativo PRIMARY KEY (id),
  CONSTRAINT ck_incidente_operativo_severidad CHECK (severidad IN ('SEV1', 'SEV2', 'SEV3')),
  CONSTRAINT ck_incidente_operativo_estado CHECK (estado IN ('DETECTADO', 'MITIGADO', 'POST_MORTEM', 'RESUELTO'))
);

COMMENT ON TABLE auditoria.incidente_operativo IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.incidente_operativo.id IS 'PK';
COMMENT ON COLUMN auditoria.incidente_operativo.codigo IS 'UQ';
COMMENT ON COLUMN auditoria.incidente_operativo.severidad IS 'CK';
COMMENT ON COLUMN auditoria.incidente_operativo.estado IS 'CK';
COMMENT ON COLUMN auditoria.incidente_operativo.resuelto_en IS 'NULL';
COMMENT ON COLUMN auditoria.incidente_operativo.causa_raiz IS 'NULL';
COMMENT ON COLUMN auditoria.incidente_operativo.acciones_correctivas IS 'NULL';
