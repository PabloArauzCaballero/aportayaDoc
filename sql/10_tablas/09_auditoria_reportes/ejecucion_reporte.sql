-- ejecucion_reporte · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: EjecucionReporte
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.ejecucion_reporte (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  definicion_id                      UUID NOT NULL,
  grupo_id                           UUID,
  solicitado_por                     UUID NOT NULL,
  parametros                         JSONB NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  filas_generadas                    INTEGER NOT NULL,
  duracion_ms                        INTEGER NOT NULL,
  hash_resultado                     VARCHAR(64),
  mensaje_error                      TEXT,
  iniciada_en                        TIMESTAMPTZ NOT NULL,
  finalizada_en                      TIMESTAMPTZ,
  CONSTRAINT pk_ejecucion_reporte PRIMARY KEY (id),
  CONSTRAINT ck_ejecucion_reporte_estado CHECK (estado IN ('COMPLETADA', 'EJECUTANDO', 'EN_COLA', 'EXPIRADA', 'FALLIDA'))
);

COMMENT ON TABLE auditoria.ejecucion_reporte IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.ejecucion_reporte.id IS 'PK';
COMMENT ON COLUMN auditoria.ejecucion_reporte.definicion_id IS 'FK, IDX';
COMMENT ON COLUMN auditoria.ejecucion_reporte.grupo_id IS 'FK, NULL';
COMMENT ON COLUMN auditoria.ejecucion_reporte.solicitado_por IS 'FK, IDX';
COMMENT ON COLUMN auditoria.ejecucion_reporte.estado IS 'CK, IDX';
COMMENT ON COLUMN auditoria.ejecucion_reporte.hash_resultado IS 'NULL';
COMMENT ON COLUMN auditoria.ejecucion_reporte.mensaje_error IS 'NULL';
COMMENT ON COLUMN auditoria.ejecucion_reporte.finalizada_en IS 'NULL';
