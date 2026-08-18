-- devolucion_comision · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: DevolucionComision
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.devolucion_comision (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  devengo_id                         UUID NOT NULL,
  transaccion_id                     UUID,
  reclamo_id                         UUID,
  autorizada_por                     UUID NOT NULL,
  motivo                             VARCHAR(30) NOT NULL,
  detalle                            VARCHAR(300) NOT NULL,
  monto_devuelto                     NUMERIC(12,2) DEFAULT 0 NOT NULL,
  forma                              VARCHAR(25) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  solicitada_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  ejecutada_en                       TIMESTAMPTZ,
  CONSTRAINT pk_devolucion_comision PRIMARY KEY (id),
  CONSTRAINT ck_devolucion_comision_motivo CHECK (motivo IN ('ENTREGA_ANULADA', 'ERROR_DE_TARIFA', 'FALLA_DE_SERVICIO', 'RECLAMO_PROCEDENTE')),
  CONSTRAINT ck_devolucion_comision_monto_devuelto CHECK (monto_devuelto > 0),
  CONSTRAINT ck_devolucion_comision_forma CHECK (forma IN ('ABONO_BILLETERA', 'COMPENSACION', 'NOTA_CREDITO')),
  CONSTRAINT ck_devolucion_comision_estado CHECK (estado IN ('AUTORIZADA', 'EJECUTADA', 'RECHAZADA', 'SOLICITADA'))
);

COMMENT ON TABLE tarifas.devolucion_comision IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.devolucion_comision.id IS 'PK';
COMMENT ON COLUMN tarifas.devolucion_comision.devengo_id IS 'FK, IDX';
COMMENT ON COLUMN tarifas.devolucion_comision.transaccion_id IS 'FK, NULL, M10';
COMMENT ON COLUMN tarifas.devolucion_comision.reclamo_id IS 'FK, NULL, M12';
COMMENT ON COLUMN tarifas.devolucion_comision.autorizada_por IS 'FK';
COMMENT ON COLUMN tarifas.devolucion_comision.motivo IS 'CK';
COMMENT ON COLUMN tarifas.devolucion_comision.monto_devuelto IS 'CK: > 0';
COMMENT ON COLUMN tarifas.devolucion_comision.forma IS 'CK';
COMMENT ON COLUMN tarifas.devolucion_comision.estado IS 'CK, IDX';
COMMENT ON COLUMN tarifas.devolucion_comision.ejecutada_en IS 'NULL';
