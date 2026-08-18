-- factura_proveedor · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: FacturaProveedor
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.factura_proveedor (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  tercero_comercial_id               UUID NOT NULL,
  orden_compra_id                    UUID,
  centro_costo_id                    UUID,
  numero_factura                     VARCHAR(30) NOT NULL,
  fecha_emision                      DATE NOT NULL,
  fecha_vencimiento                  DATE NOT NULL,
  monto                              NUMERIC(14,2) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  monto_pagado                       NUMERIC(14,2) DEFAULT 0 NOT NULL,
  saldo_pendiente                    NUMERIC(14,2),
  estado                             VARCHAR(15) NOT NULL,
  aprobada_por                       UUID,
  asiento_contable_id                UUID,
  CONSTRAINT pk_factura_proveedor PRIMARY KEY (id),
  CONSTRAINT ck_factura_proveedor_monto CHECK (monto > 0),
  CONSTRAINT ck_factura_proveedor_estado CHECK (estado IN ('ANULADA', 'APROBADA', 'PAGADA', 'PAGADA_PARCIAL', 'REGISTRADA'))
);

COMMENT ON TABLE erp.factura_proveedor IS 'Módulo 13 — Contabilidad Financiera y ERP. [append-only] Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.factura_proveedor.id IS 'PK';
COMMENT ON COLUMN erp.factura_proveedor.tercero_comercial_id IS 'FK, IDX';
COMMENT ON COLUMN erp.factura_proveedor.orden_compra_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN erp.factura_proveedor.centro_costo_id IS 'FK, NULL';
COMMENT ON COLUMN erp.factura_proveedor.monto IS 'CK: > 0';
COMMENT ON COLUMN erp.factura_proveedor.saldo_pendiente IS 'GENERATED';
COMMENT ON COLUMN erp.factura_proveedor.estado IS 'CK, IDX';
COMMENT ON COLUMN erp.factura_proveedor.aprobada_por IS 'FK, NULL';
COMMENT ON COLUMN erp.factura_proveedor.asiento_contable_id IS 'FK, NULL, M3';
