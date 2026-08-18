-- asiento_plantilla · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: AsientoPlantilla
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.asiento_plantilla (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(30) NOT NULL,
  nombre                             VARCHAR(100) NOT NULL,
  glosa                              VARCHAR(200) NOT NULL,
  periodicidad                       VARCHAR(15) NOT NULL,
  activa                             BOOLEAN DEFAULT FALSE NOT NULL,
  creada_por                         UUID NOT NULL,
  CONSTRAINT pk_asiento_plantilla PRIMARY KEY (id),
  CONSTRAINT ck_asiento_plantilla_periodicidad CHECK (periodicidad IN ('ANUAL', 'MANUAL', 'MENSUAL', 'TRIMESTRAL'))
);

COMMENT ON TABLE erp.asiento_plantilla IS 'Módulo 13 — Contabilidad Financiera y ERP. Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.asiento_plantilla.id IS 'PK';
COMMENT ON COLUMN erp.asiento_plantilla.codigo IS 'UQ';
COMMENT ON COLUMN erp.asiento_plantilla.periodicidad IS 'CK';
COMMENT ON COLUMN erp.asiento_plantilla.creada_por IS 'FK';
