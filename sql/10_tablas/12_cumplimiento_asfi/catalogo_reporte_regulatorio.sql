-- catalogo_reporte_regulatorio · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: CatalogoReporteRegulatorio
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.catalogo_reporte_regulatorio (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(30) NOT NULL,
  organismo                          VARCHAR(10) NOT NULL,
  nombre                             VARCHAR(120) NOT NULL,
  periodicidad                       VARCHAR(12) NOT NULL,
  formato                            VARCHAR(15) NOT NULL,
  plazo_dias                         SMALLINT NOT NULL,
  base_normativa                     VARCHAR(120) NOT NULL,
  obligatorio                        BOOLEAN DEFAULT FALSE NOT NULL,
  activo                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_catalogo_reporte_regulatorio PRIMARY KEY (id),
  CONSTRAINT ck_catalogo_reporte_regulatorio_organismo CHECK (organismo IN ('ASFI', 'BCB', 'SIN', 'UIF')),
  CONSTRAINT ck_catalogo_reporte_regulatorio_periodicidad CHECK (periodicidad IN ('ANUAL', 'DIARIA', 'EVENTUAL', 'MENSUAL', 'SEMANAL', 'TRIMESTRAL')),
  CONSTRAINT ck_catalogo_reporte_regulatorio_formato CHECK (formato IN ('CSV', 'JSON', 'TXT', 'WEB', 'XLSX', 'XML'))
);

COMMENT ON TABLE cumplimiento.catalogo_reporte_regulatorio IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.catalogo_reporte_regulatorio.id IS 'PK';
COMMENT ON COLUMN cumplimiento.catalogo_reporte_regulatorio.codigo IS 'UQ';
COMMENT ON COLUMN cumplimiento.catalogo_reporte_regulatorio.organismo IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.catalogo_reporte_regulatorio.periodicidad IS 'CK';
COMMENT ON COLUMN cumplimiento.catalogo_reporte_regulatorio.formato IS 'CK';
