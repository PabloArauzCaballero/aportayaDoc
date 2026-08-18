-- aplicacion_promocion · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: AplicacionPromocion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.aplicacion_promocion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  campana_id                         UUID NOT NULL,
  devengo_id                         UUID NOT NULL,
  monto_descontado                   NUMERIC(12,2) DEFAULT 0 NOT NULL,
  aplicada_en                        TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_aplicacion_promocion PRIMARY KEY (id)
);

COMMENT ON TABLE tarifas.aplicacion_promocion IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.aplicacion_promocion.id IS 'PK';
COMMENT ON COLUMN tarifas.aplicacion_promocion.campana_id IS 'FK, IDX';
COMMENT ON COLUMN tarifas.aplicacion_promocion.devengo_id IS 'FK, IDX';
