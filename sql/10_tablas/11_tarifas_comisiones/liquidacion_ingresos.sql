-- liquidacion_ingresos · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: LiquidacionIngresos
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.liquidacion_ingresos (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  periodo                            CHAR(7) NOT NULL,
  fecha_inicio                       DATE NOT NULL,
  fecha_fin                          DATE NOT NULL,
  total_devengado                    NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_cobrado                      NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_exonerado                    NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_devuelto                     NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_incobrable                   NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_impuestos                    NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_costo_proveedores            NUMERIC(16,2) DEFAULT 0 NOT NULL,
  ingreso_neto                       NUMERIC(16,2) GENERATED ALWAYS AS (total_cobrado - total_devuelto - total_impuestos - total_costo_proveedores) STORED,
  cantidad_operaciones               INTEGER DEFAULT 0 NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  asiento_contable_id                UUID,
  cerrada_por                        UUID,
  cerrada_en                         TIMESTAMPTZ,
  CONSTRAINT pk_liquidacion_ingresos PRIMARY KEY (id),
  CONSTRAINT ck_liquidacion_ingresos_estado CHECK (estado IN ('ABIERTA', 'CERRADA', 'EN_CIERRE', 'REABIERTA'))
);

COMMENT ON TABLE tarifas.liquidacion_ingresos IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.liquidacion_ingresos.id IS 'PK';
COMMENT ON COLUMN tarifas.liquidacion_ingresos.periodo IS 'UQ';
COMMENT ON COLUMN tarifas.liquidacion_ingresos.ingreso_neto IS 'GENERATED';
COMMENT ON COLUMN tarifas.liquidacion_ingresos.estado IS 'CK, IDX';
COMMENT ON COLUMN tarifas.liquidacion_ingresos.asiento_contable_id IS 'FK, NULL, M3';
COMMENT ON COLUMN tarifas.liquidacion_ingresos.cerrada_por IS 'FK, NULL';
COMMENT ON COLUMN tarifas.liquidacion_ingresos.cerrada_en IS 'NULL';
