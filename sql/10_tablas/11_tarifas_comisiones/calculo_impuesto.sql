-- calculo_impuesto · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: CalculoImpuesto
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.calculo_impuesto (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  devengo_id                         UUID NOT NULL,
  impuesto_id                        UUID NOT NULL,
  base_imponible                     NUMERIC(14,2) NOT NULL,
  alicuota_aplicada                  NUMERIC(6,4) NOT NULL,
  monto_impuesto                     NUMERIC(12,2) DEFAULT 0 NOT NULL,
  incluido_en_precio                 BOOLEAN DEFAULT FALSE NOT NULL,
  periodo_fiscal                     CHAR(7) NOT NULL,
  CONSTRAINT pk_calculo_impuesto PRIMARY KEY (id)
);

COMMENT ON TABLE tarifas.calculo_impuesto IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.calculo_impuesto.id IS 'PK';
COMMENT ON COLUMN tarifas.calculo_impuesto.devengo_id IS 'FK, IDX';
COMMENT ON COLUMN tarifas.calculo_impuesto.impuesto_id IS 'FK';
COMMENT ON COLUMN tarifas.calculo_impuesto.periodo_fiscal IS 'IDX';
