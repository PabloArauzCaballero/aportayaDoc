-- asiento_contable · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: AsientoContable
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.asiento_contable (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  numero                             BIGSERIAL NOT NULL,
  fecha                              TIMESTAMPTZ NOT NULL,
  glosa                              VARCHAR(200) NOT NULL,
  origen_tipo                        VARCHAR(20) NOT NULL,
  origen_id                          UUID NOT NULL,
  grupo_id                           UUID,
  periodo_contable_id                UUID,
  estado                             VARCHAR(15) NOT NULL,
  asiento_reversa_id                 UUID,
  registrado_por                     UUID,
  CONSTRAINT pk_asiento_contable PRIMARY KEY (id),
  CONSTRAINT ck_asiento_contable_origen_tipo CHECK (origen_tipo IN ('AJUSTE', 'BILLETERA', 'COBERTURA', 'COBRO_CXC', 'COMISION', 'DEPRECIACION_ACTIVO', 'ENTREGA', 'FACTURA_PROVEEDOR', 'PAGO')),
  CONSTRAINT ck_asiento_contable_estado CHECK (estado IN ('BORRADOR', 'CONFIRMADO', 'REVERSADO'))
);

COMMENT ON TABLE nucleo_financiero.asiento_contable IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. [append-only] Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN nucleo_financiero.asiento_contable.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.asiento_contable.numero IS 'UQ';
COMMENT ON COLUMN nucleo_financiero.asiento_contable.fecha IS 'IDX';
COMMENT ON COLUMN nucleo_financiero.asiento_contable.origen_tipo IS 'CK';
COMMENT ON COLUMN nucleo_financiero.asiento_contable.origen_id IS 'IDX, polimorfica';
COMMENT ON COLUMN nucleo_financiero.asiento_contable.grupo_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN nucleo_financiero.asiento_contable.periodo_contable_id IS 'FK, NULL, IDX, M13';
COMMENT ON COLUMN nucleo_financiero.asiento_contable.estado IS 'CK';
COMMENT ON COLUMN nucleo_financiero.asiento_contable.asiento_reversa_id IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.asiento_contable.registrado_por IS 'FK, NULL';
