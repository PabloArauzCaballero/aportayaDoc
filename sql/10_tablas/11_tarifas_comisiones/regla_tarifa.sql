-- regla_tarifa · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: ReglaTarifa
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.regla_tarifa (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  concepto_tarifa_id                 UUID NOT NULL,
  orden                              SMALLINT NOT NULL,
  condicion                          JSONB NOT NULL,
  monto_base_desde                   NUMERIC(14,2),
  monto_base_hasta                   NUMERIC(14,2),
  valor_porcentual                   NUMERIC(7,4),
  valor_fijo                         NUMERIC(12,2),
  monto_minimo                       NUMERIC(12,2),
  monto_maximo                       NUMERIC(12,2),
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  CONSTRAINT pk_regla_tarifa PRIMARY KEY (id)
);

COMMENT ON TABLE tarifas.regla_tarifa IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.regla_tarifa.id IS 'PK';
COMMENT ON COLUMN tarifas.regla_tarifa.concepto_tarifa_id IS 'FK, IDX';
COMMENT ON COLUMN tarifas.regla_tarifa.monto_base_desde IS 'NULL';
COMMENT ON COLUMN tarifas.regla_tarifa.monto_base_hasta IS 'NULL';
COMMENT ON COLUMN tarifas.regla_tarifa.valor_porcentual IS 'NULL';
COMMENT ON COLUMN tarifas.regla_tarifa.valor_fijo IS 'NULL';
COMMENT ON COLUMN tarifas.regla_tarifa.monto_minimo IS 'NULL';
COMMENT ON COLUMN tarifas.regla_tarifa.monto_maximo IS 'NULL';
COMMENT ON COLUMN tarifas.regla_tarifa.vigente_hasta IS 'NULL';
