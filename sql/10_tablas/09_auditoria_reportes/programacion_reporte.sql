-- programacion_reporte · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: ProgramacionReporte
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.programacion_reporte (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  definicion_id                      UUID NOT NULL,
  expresion_cron                     VARCHAR(40) NOT NULL,
  parametros_fijos                   JSONB NOT NULL,
  destinatarios                      JSONB NOT NULL,
  canal_entrega                      VARCHAR(20) NOT NULL,
  formato                            VARCHAR(10) NOT NULL,
  activa                             BOOLEAN DEFAULT FALSE NOT NULL,
  ultima_ejecucion_en                TIMESTAMPTZ,
  proxima_ejecucion_en               TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_programacion_reporte PRIMARY KEY (id),
  CONSTRAINT ck_programacion_reporte_canal_entrega CHECK (canal_entrega IN ('ALMACENAMIENTO', 'API', 'CORREO', 'PORTAL')),
  CONSTRAINT ck_programacion_reporte_formato CHECK (formato IN ('CSV', 'JSON', 'PDF', 'XLSX'))
);

COMMENT ON TABLE auditoria.programacion_reporte IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.programacion_reporte.id IS 'PK';
COMMENT ON COLUMN auditoria.programacion_reporte.definicion_id IS 'FK, IDX';
COMMENT ON COLUMN auditoria.programacion_reporte.canal_entrega IS 'CK';
COMMENT ON COLUMN auditoria.programacion_reporte.formato IS 'CK';
COMMENT ON COLUMN auditoria.programacion_reporte.ultima_ejecucion_en IS 'NULL';
COMMENT ON COLUMN auditoria.programacion_reporte.proxima_ejecucion_en IS 'IDX';
