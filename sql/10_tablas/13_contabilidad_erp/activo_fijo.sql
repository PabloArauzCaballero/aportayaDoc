-- activo_fijo · módulo 13 — Contabilidad Financiera y ERP
-- clase de dominio: ActivoFijo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS erp.activo_fijo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  categoria_activo_fijo_id           UUID NOT NULL,
  centro_costo_id                    UUID,
  codigo_inventario                  VARCHAR(30) NOT NULL,
  descripcion                        VARCHAR(200) NOT NULL,
  fecha_adquisicion                  DATE NOT NULL,
  costo_adquisicion                  NUMERIC(14,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  valor_residual                     NUMERIC(14,2) NOT NULL,
  depreciacion_acumulada             NUMERIC(14,2) NOT NULL,
  valor_en_libros                    NUMERIC(14,2),
  estado                             VARCHAR(15) NOT NULL,
  factura_proveedor_id               UUID,
  CONSTRAINT pk_activo_fijo PRIMARY KEY (id),
  CONSTRAINT ck_activo_fijo_costo_adquisicion CHECK (costo_adquisicion > 0),
  CONSTRAINT ck_activo_fijo_estado CHECK (estado IN ('ACTIVO', 'DADO_DE_BAJA', 'VENDIDO'))
);

COMMENT ON TABLE erp.activo_fijo IS 'Módulo 13 — Contabilidad Financiera y ERP. Que cerrar un mes no dependa de un Excel armado a mano';
COMMENT ON COLUMN erp.activo_fijo.id IS 'PK';
COMMENT ON COLUMN erp.activo_fijo.categoria_activo_fijo_id IS 'FK, IDX';
COMMENT ON COLUMN erp.activo_fijo.centro_costo_id IS 'FK, NULL';
COMMENT ON COLUMN erp.activo_fijo.codigo_inventario IS 'UQ';
COMMENT ON COLUMN erp.activo_fijo.costo_adquisicion IS 'CK: > 0';
COMMENT ON COLUMN erp.activo_fijo.valor_en_libros IS 'GENERATED';
COMMENT ON COLUMN erp.activo_fijo.estado IS 'CK, IDX';
COMMENT ON COLUMN erp.activo_fijo.factura_proveedor_id IS 'FK, NULL';
