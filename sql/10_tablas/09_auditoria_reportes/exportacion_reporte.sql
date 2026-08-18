-- exportacion_reporte · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: ExportacionReporte
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.exportacion_reporte (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  ejecucion_id                       UUID NOT NULL,
  formato                            VARCHAR(10) NOT NULL,
  url_archivo                        VARCHAR(255) NOT NULL,
  hash_archivo                       VARCHAR(64) NOT NULL,
  tamano_bytes                       BIGINT NOT NULL,
  esta_cifrado                       BOOLEAN DEFAULT FALSE NOT NULL,
  version_llave                      SMALLINT NOT NULL,
  descargas                          SMALLINT NOT NULL,
  expira_en                          TIMESTAMPTZ NOT NULL,
  generada_en                        TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_exportacion_reporte PRIMARY KEY (id),
  CONSTRAINT ck_exportacion_reporte_formato CHECK (formato IN ('CSV', 'JSON', 'PDF', 'XLSX'))
);

COMMENT ON TABLE auditoria.exportacion_reporte IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.exportacion_reporte.id IS 'PK';
COMMENT ON COLUMN auditoria.exportacion_reporte.ejecucion_id IS 'FK, IDX';
COMMENT ON COLUMN auditoria.exportacion_reporte.formato IS 'CK';
COMMENT ON COLUMN auditoria.exportacion_reporte.expira_en IS 'IDX';
