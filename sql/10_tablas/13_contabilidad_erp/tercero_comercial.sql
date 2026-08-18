-- tercero_comercial · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: TerceroComercial
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.tercero_comercial (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  tipo                               VARCHAR(12) NOT NULL,
  razon_social                       VARCHAR(150) NOT NULL,
  numero_documento                   VARCHAR(30) NOT NULL,
  email                              VARCHAR(120),
  telefono                           VARCHAR(20),
  cuenta_contable_id                 UUID,
  estado                             VARCHAR(15) NOT NULL,
  creado_en                          TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_tercero_comercial PRIMARY KEY (id),
  CONSTRAINT ck_tercero_comercial_tipo CHECK (tipo IN ('AMBOS', 'CLIENTE', 'PROVEEDOR')),
  CONSTRAINT ck_tercero_comercial_estado CHECK (estado IN ('ACTIVO', 'BLOQUEADO', 'INACTIVO'))
);

COMMENT ON TABLE erp.tercero_comercial IS 'Módulo 13 — Contabilidad Financiera y ERP. Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.tercero_comercial.id IS 'PK';
COMMENT ON COLUMN erp.tercero_comercial.tipo IS 'CK';
COMMENT ON COLUMN erp.tercero_comercial.numero_documento IS 'UQ';
COMMENT ON COLUMN erp.tercero_comercial.email IS 'NULL';
COMMENT ON COLUMN erp.tercero_comercial.telefono IS 'NULL';
COMMENT ON COLUMN erp.tercero_comercial.cuenta_contable_id IS 'FK, NULL, IDX, M3';
COMMENT ON COLUMN erp.tercero_comercial.estado IS 'CK, IDX';
