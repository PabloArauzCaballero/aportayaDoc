-- reporte_operacion_sospechosa · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: ReporteOperacionSospechosa
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.reporte_operacion_sospechosa (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  aprobado_por                       UUID,
  tipologia                          VARCHAR(60) NOT NULL,
  monto_total                        NUMERIC(16,2) DEFAULT 0 NOT NULL,
  periodo_analizado                  VARCHAR(20) NOT NULL,
  narrativa                          TEXT NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  numero_radicado                    VARCHAR(40),
  enviado_en                         TIMESTAMPTZ,
  CONSTRAINT pk_reporte_operacion_sospechosa PRIMARY KEY (id),
  CONSTRAINT ck_reporte_operacion_sospechosa_estado CHECK (estado IN ('APROBADO', 'ARCHIVADO', 'BORRADOR', 'ENVIADO'))
);

COMMENT ON TABLE auditoria.reporte_operacion_sospechosa IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.reporte_operacion_sospechosa.id IS 'PK';
COMMENT ON COLUMN auditoria.reporte_operacion_sospechosa.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN auditoria.reporte_operacion_sospechosa.aprobado_por IS 'FK, NULL';
COMMENT ON COLUMN auditoria.reporte_operacion_sospechosa.estado IS 'CK';
COMMENT ON COLUMN auditoria.reporte_operacion_sospechosa.numero_radicado IS 'UQ, NULL';
COMMENT ON COLUMN auditoria.reporte_operacion_sospechosa.enviado_en IS 'NULL';
