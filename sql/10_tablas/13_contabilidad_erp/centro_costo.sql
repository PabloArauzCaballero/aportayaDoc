-- centro_costo · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: CentroCosto
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.centro_costo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(20) NOT NULL,
  nombre                             VARCHAR(100) NOT NULL,
  tipo                               VARCHAR(20) NOT NULL,
  activo                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_centro_costo PRIMARY KEY (id),
  CONSTRAINT ck_centro_costo_tipo CHECK (tipo IN ('AREA', 'CAMPANA', 'PRODUCTO', 'PROYECTO'))
);

COMMENT ON TABLE erp.centro_costo IS 'Módulo 13 — Contabilidad Financiera y ERP. Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.centro_costo.id IS 'PK';
COMMENT ON COLUMN erp.centro_costo.codigo IS 'UQ';
COMMENT ON COLUMN erp.centro_costo.tipo IS 'CK';
