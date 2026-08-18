-- cargo_comision · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: CargoComision
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.cargo_comision (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  devengo_id                         UUID NOT NULL,
  deduccion_entrega_id               UUID,
  transaccion_id                     UUID,
  obligacion_id                      UUID,
  forma_cobro                        VARCHAR(30) NOT NULL,
  monto_cobrado                      NUMERIC(12,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  intentos                           SMALLINT DEFAULT 0 NOT NULL,
  ultimo_error                       VARCHAR(300),
  cobrado_en                         TIMESTAMPTZ,
  CONSTRAINT pk_cargo_comision PRIMARY KEY (id),
  CONSTRAINT ck_cargo_comision_forma_cobro CHECK (forma_cobro IN ('COBRO_EXTERNO', 'COMPENSACION', 'DEBITO_DE_BILLETERA', 'DEDUCCION_DE_ENTREGA', 'OBLIGACION_DE_APORTE')),
  CONSTRAINT ck_cargo_comision_estado CHECK (estado IN ('ANULADO', 'COBRADO', 'FALLIDO', 'PENDIENTE'))
);

COMMENT ON TABLE tarifas.cargo_comision IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.cargo_comision.id IS 'PK';
COMMENT ON COLUMN tarifas.cargo_comision.devengo_id IS 'FK, IDX';
COMMENT ON COLUMN tarifas.cargo_comision.deduccion_entrega_id IS 'FK, NULL, UQ, M4';
COMMENT ON COLUMN tarifas.cargo_comision.transaccion_id IS 'FK, NULL, M10';
COMMENT ON COLUMN tarifas.cargo_comision.obligacion_id IS 'FK, NULL, M3';
COMMENT ON COLUMN tarifas.cargo_comision.forma_cobro IS 'CK';
COMMENT ON COLUMN tarifas.cargo_comision.estado IS 'CK, IDX';
COMMENT ON COLUMN tarifas.cargo_comision.ultimo_error IS 'NULL';
COMMENT ON COLUMN tarifas.cargo_comision.cobrado_en IS 'NULL';
