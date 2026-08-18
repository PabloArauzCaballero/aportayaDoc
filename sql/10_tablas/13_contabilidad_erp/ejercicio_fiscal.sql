-- ejercicio_fiscal · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: EjercicioFiscal
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.ejercicio_fiscal (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  anio                               SMALLINT NOT NULL,
  fecha_inicio                       DATE NOT NULL,
  fecha_fin                          DATE NOT NULL,
  estado                             VARCHAR(10) NOT NULL,
  cerrado_en                         TIMESTAMPTZ,
  cerrado_por                        UUID,
  CONSTRAINT pk_ejercicio_fiscal PRIMARY KEY (id),
  CONSTRAINT ck_ejercicio_fiscal_estado CHECK (estado IN ('ABIERTO', 'CERRADO'))
);

COMMENT ON TABLE erp.ejercicio_fiscal IS 'Módulo 13 — Contabilidad Financiera y ERP. Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.ejercicio_fiscal.id IS 'PK';
COMMENT ON COLUMN erp.ejercicio_fiscal.anio IS 'UQ';
COMMENT ON COLUMN erp.ejercicio_fiscal.estado IS 'CK, IDX';
COMMENT ON COLUMN erp.ejercicio_fiscal.cerrado_en IS 'NULL';
COMMENT ON COLUMN erp.ejercicio_fiscal.cerrado_por IS 'FK, NULL';
