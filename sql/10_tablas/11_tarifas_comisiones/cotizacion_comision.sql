-- cotizacion_comision · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: CotizacionComision
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.cotizacion_comision (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  concepto_tarifa_id                 UUID NOT NULL,
  tarifario_id                       UUID NOT NULL,
  referencia_tipo                    VARCHAR(30) NOT NULL,
  referencia_id                      UUID NOT NULL,
  monto_base                         NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_comision                     NUMERIC(12,2) DEFAULT 0 NOT NULL,
  monto_impuesto                     NUMERIC(12,2) DEFAULT 0 NOT NULL,
  monto_total                        NUMERIC(12,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  desglose                           JSONB NOT NULL,
  valida_hasta                       TIMESTAMPTZ NOT NULL,
  mostrada_al_usuario_en             TIMESTAMPTZ,
  aceptada_en                        TIMESTAMPTZ,
  clave_idempotencia                 VARCHAR(100) NOT NULL,
  CONSTRAINT pk_cotizacion_comision PRIMARY KEY (id),
  CONSTRAINT ck_cotizacion_comision_referencia_tipo CHECK (referencia_tipo IN ('ENTREGA_FONDO', 'ORDEN_RECARGA', 'ORDEN_RETIRO', 'PAGO', 'PERIODO', 'TRANSACCION_BILLETERA'))
);

COMMENT ON TABLE tarifas.cotizacion_comision IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.cotizacion_comision.id IS 'PK';
COMMENT ON COLUMN tarifas.cotizacion_comision.concepto_tarifa_id IS 'FK, IDX';
COMMENT ON COLUMN tarifas.cotizacion_comision.tarifario_id IS 'FK';
COMMENT ON COLUMN tarifas.cotizacion_comision.referencia_tipo IS 'CK';
COMMENT ON COLUMN tarifas.cotizacion_comision.referencia_id IS 'IDX, polimorfica';
COMMENT ON COLUMN tarifas.cotizacion_comision.mostrada_al_usuario_en IS 'NULL';
COMMENT ON COLUMN tarifas.cotizacion_comision.aceptada_en IS 'NULL';
