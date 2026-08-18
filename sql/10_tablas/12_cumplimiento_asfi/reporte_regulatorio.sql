-- reporte_regulatorio · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: ReporteRegulatorio
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.reporte_regulatorio (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  catalogo_reporte_id                UUID NOT NULL,
  generado_por                       UUID,
  revisado_por                       UUID,
  aprobado_por                       UUID,
  periodo                            VARCHAR(10) NOT NULL,
  fecha_corte                        DATE NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  cantidad_registros                 INTEGER DEFAULT 0 NOT NULL,
  reporte_en_cero                    BOOLEAN DEFAULT FALSE NOT NULL,
  monto_total                        NUMERIC(18,2) DEFAULT 0 NOT NULL,
  url_archivo                        VARCHAR(255),
  hash_archivo                       VARCHAR(64),
  fecha_limite                       DATE NOT NULL,
  generado_en                        TIMESTAMPTZ,
  CONSTRAINT pk_reporte_regulatorio PRIMARY KEY (id),
  CONSTRAINT ck_reporte_regulatorio_estado CHECK (estado IN ('APROBADO', 'ENVIADO', 'GENERADO', 'OBSERVADO', 'PENDIENTE', 'RECHAZADO', 'REVISADO'))
);

COMMENT ON TABLE cumplimiento.reporte_regulatorio IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.reporte_regulatorio.id IS 'PK';
COMMENT ON COLUMN cumplimiento.reporte_regulatorio.catalogo_reporte_id IS 'FK, IDX';
COMMENT ON COLUMN cumplimiento.reporte_regulatorio.generado_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.reporte_regulatorio.revisado_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.reporte_regulatorio.aprobado_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.reporte_regulatorio.periodo IS 'UQ+catalogo_reporte_id';
COMMENT ON COLUMN cumplimiento.reporte_regulatorio.estado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.reporte_regulatorio.url_archivo IS 'NULL';
COMMENT ON COLUMN cumplimiento.reporte_regulatorio.hash_archivo IS 'NULL';
COMMENT ON COLUMN cumplimiento.reporte_regulatorio.fecha_limite IS 'IDX';
COMMENT ON COLUMN cumplimiento.reporte_regulatorio.generado_en IS 'NULL';
