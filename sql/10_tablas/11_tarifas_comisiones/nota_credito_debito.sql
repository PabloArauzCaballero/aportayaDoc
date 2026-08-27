-- nota_credito_debito · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: NotaCreditoDebito
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.nota_credito_debito (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  factura_id                         UUID NOT NULL,
  devolucion_comision_id             UUID,
  tipo                               VARCHAR(8) NOT NULL,
  motivo                             VARCHAR(200) NOT NULL,
  monto                              NUMERIC(12,2) NOT NULL,
  cuf                                VARCHAR(80) NOT NULL,
  fecha_emision                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  estado_fiscal                      VARCHAR(20) NOT NULL,
  CONSTRAINT pk_nota_credito_debito PRIMARY KEY (id),
  CONSTRAINT ck_nota_credito_debito_tipo CHECK (tipo IN ('CREDITO', 'DEBITO')),
  CONSTRAINT ck_nota_credito_debito_estado_fiscal CHECK (estado_fiscal IN ('ANULADA', 'EMITIDA_OFFLINE', 'PENDIENTE', 'RECHAZADA', 'VALIDADA'))
);

COMMENT ON TABLE tarifas.nota_credito_debito IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.nota_credito_debito.id IS 'PK';
COMMENT ON COLUMN tarifas.nota_credito_debito.factura_id IS 'FK, IDX';
COMMENT ON COLUMN tarifas.nota_credito_debito.devolucion_comision_id IS 'FK, NULL';
COMMENT ON COLUMN tarifas.nota_credito_debito.tipo IS 'CK';
COMMENT ON COLUMN tarifas.nota_credito_debito.estado_fiscal IS 'CK';
