-- orden_compra · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: OrdenCompra
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.orden_compra (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  tercero_comercial_id               UUID NOT NULL,
  centro_costo_id                    UUID,
  numero                             VARCHAR(30) NOT NULL,
  descripcion                        VARCHAR(300) NOT NULL,
  monto_total                        NUMERIC(14,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  aprobada_por                       UUID,
  creada_en                          TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_orden_compra PRIMARY KEY (id),
  CONSTRAINT ck_orden_compra_monto_total CHECK (monto_total > 0),
  CONSTRAINT ck_orden_compra_estado CHECK (estado IN ('APROBADA', 'BORRADOR', 'CANCELADA', 'RECIBIDA_PARCIAL', 'RECIBIDA_TOTAL'))
);

COMMENT ON TABLE erp.orden_compra IS 'Módulo 13 — Contabilidad Financiera y ERP. Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.orden_compra.id IS 'PK';
COMMENT ON COLUMN erp.orden_compra.tercero_comercial_id IS 'FK, IDX';
COMMENT ON COLUMN erp.orden_compra.centro_costo_id IS 'FK, NULL';
COMMENT ON COLUMN erp.orden_compra.numero IS 'UQ';
COMMENT ON COLUMN erp.orden_compra.monto_total IS 'CK: > 0';
COMMENT ON COLUMN erp.orden_compra.estado IS 'CK, IDX';
COMMENT ON COLUMN erp.orden_compra.aprobada_por IS 'FK, NULL';
