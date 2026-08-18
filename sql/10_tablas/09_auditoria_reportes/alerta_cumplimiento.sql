-- alerta_cumplimiento · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: AlertaCumplimiento
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.alerta_cumplimiento (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  regla_id                           UUID NOT NULL,
  usuario_id                         UUID NOT NULL,
  grupo_id                           UUID,
  analista_id                        UUID,
  reporte_sospechoso_id              UUID,
  operacion_tipo                     VARCHAR(30) NOT NULL,
  operacion_id                       UUID NOT NULL,
  monto_involucrado                  NUMERIC(16,2) DEFAULT 0 NOT NULL,
  detalle_deteccion                  JSONB NOT NULL,
  severidad                          VARCHAR(10) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  conclusion                         VARCHAR(500),
  detectada_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  resuelta_en                        TIMESTAMPTZ,
  CONSTRAINT pk_alerta_cumplimiento PRIMARY KEY (id),
  CONSTRAINT ck_alerta_cumplimiento_severidad CHECK (severidad IN ('ALTA', 'BAJA', 'CRITICA', 'MEDIA')),
  CONSTRAINT ck_alerta_cumplimiento_estado CHECK (estado IN ('ABIERTA', 'DESCARTADA', 'EN_ANALISIS', 'ESCALADA', 'REPORTADA'))
);

COMMENT ON TABLE auditoria.alerta_cumplimiento IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.alerta_cumplimiento.id IS 'PK';
COMMENT ON COLUMN auditoria.alerta_cumplimiento.regla_id IS 'FK, IDX';
COMMENT ON COLUMN auditoria.alerta_cumplimiento.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN auditoria.alerta_cumplimiento.grupo_id IS 'FK, NULL';
COMMENT ON COLUMN auditoria.alerta_cumplimiento.analista_id IS 'FK, NULL';
COMMENT ON COLUMN auditoria.alerta_cumplimiento.reporte_sospechoso_id IS 'FK, NULL';
COMMENT ON COLUMN auditoria.alerta_cumplimiento.operacion_id IS 'IDX';
COMMENT ON COLUMN auditoria.alerta_cumplimiento.severidad IS 'CK';
COMMENT ON COLUMN auditoria.alerta_cumplimiento.estado IS 'CK, IDX';
COMMENT ON COLUMN auditoria.alerta_cumplimiento.conclusion IS 'NULL';
COMMENT ON COLUMN auditoria.alerta_cumplimiento.detectada_en IS 'IDX';
COMMENT ON COLUMN auditoria.alerta_cumplimiento.resuelta_en IS 'NULL';
