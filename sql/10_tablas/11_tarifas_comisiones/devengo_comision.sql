-- devengo_comision · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: DevengoComision
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.devengo_comision (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  concepto_tarifa_id                 UUID NOT NULL,
  tarifario_id                       UUID NOT NULL,
  cotizacion_id                      UUID,
  grupo_id                           UUID,
  participante_id                    UUID,
  usuario_obligado_id                UUID NOT NULL,
  asiento_contable_id                UUID,
  referencia_tipo                    VARCHAR(30) NOT NULL,
  referencia_id                      UUID NOT NULL,
  monto_base                         NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_comision                     NUMERIC(12,2) DEFAULT 0 NOT NULL,
  monto_descuento                    NUMERIC(12,2) DEFAULT 0 NOT NULL,
  monto_impuesto                     NUMERIC(12,2) DEFAULT 0 NOT NULL,
  monto_total                        NUMERIC(12,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  estado                             VARCHAR(20) NOT NULL,
  fecha_devengo                      TIMESTAMPTZ NOT NULL,
  periodo_contable                   CHAR(7) NOT NULL,
  clave_idempotencia                 VARCHAR(100) NOT NULL,
  CONSTRAINT pk_devengo_comision PRIMARY KEY (id),
  CONSTRAINT ck_devengo_comision_referencia_tipo CHECK (referencia_tipo IN ('CICLO', 'ENTREGA_FONDO', 'ORDEN_RETIRO', 'PAGO')),
  CONSTRAINT ck_devengo_comision_monto_comision CHECK (monto_comision >= 0),
  CONSTRAINT ck_devengo_comision_estado CHECK (estado IN ('COBRADO', 'COBRADO_PARCIAL', 'DEVENGADO', 'DEVUELTO', 'EXONERADO', 'INCOBRABLE', 'REVERSADO'))
);

COMMENT ON TABLE tarifas.devengo_comision IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. [append-only] La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.devengo_comision.id IS 'PK';
COMMENT ON COLUMN tarifas.devengo_comision.concepto_tarifa_id IS 'FK, IDX';
COMMENT ON COLUMN tarifas.devengo_comision.tarifario_id IS 'FK, IDX';
COMMENT ON COLUMN tarifas.devengo_comision.cotizacion_id IS 'FK, NULL, UQ';
COMMENT ON COLUMN tarifas.devengo_comision.grupo_id IS 'FK, NULL, IDX, M2';
COMMENT ON COLUMN tarifas.devengo_comision.participante_id IS 'FK, NULL, M2';
COMMENT ON COLUMN tarifas.devengo_comision.usuario_obligado_id IS 'FK, IDX, M1';
COMMENT ON COLUMN tarifas.devengo_comision.asiento_contable_id IS 'FK, NULL, M3';
COMMENT ON COLUMN tarifas.devengo_comision.referencia_tipo IS 'CK';
COMMENT ON COLUMN tarifas.devengo_comision.referencia_id IS 'IDX, polimorfica';
COMMENT ON COLUMN tarifas.devengo_comision.monto_comision IS 'CK: >= 0';
COMMENT ON COLUMN tarifas.devengo_comision.estado IS 'CK, IDX';
COMMENT ON COLUMN tarifas.devengo_comision.fecha_devengo IS 'IDX';
COMMENT ON COLUMN tarifas.devengo_comision.periodo_contable IS 'IDX';
