-- presupuesto · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: Presupuesto
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.presupuesto (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  centro_costo_id                    UUID NOT NULL,
  ejercicio_fiscal_id                UUID NOT NULL,
  nombre                             VARCHAR(100) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  aprobado_por                       UUID,
  aprobado_en                        TIMESTAMPTZ,
  CONSTRAINT pk_presupuesto PRIMARY KEY (id),
  CONSTRAINT ck_presupuesto_estado CHECK (estado IN ('APROBADO', 'BORRADOR', 'CERRADO'))
);

COMMENT ON TABLE erp.presupuesto IS 'Módulo 13 — Contabilidad Financiera y ERP. Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.presupuesto.id IS 'PK';
COMMENT ON COLUMN erp.presupuesto.centro_costo_id IS 'FK, IDX';
COMMENT ON COLUMN erp.presupuesto.ejercicio_fiscal_id IS 'FK, IDX';
COMMENT ON COLUMN erp.presupuesto.estado IS 'CK, IDX';
COMMENT ON COLUMN erp.presupuesto.aprobado_por IS 'FK, NULL';
COMMENT ON COLUMN erp.presupuesto.aprobado_en IS 'NULL';
