-- movimiento_contable · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: MovimientoContable
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.movimiento_contable (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  asiento_id                         UUID NOT NULL,
  cuenta_id                          UUID NOT NULL,
  debe                               NUMERIC(16,2) NOT NULL,
  haber                              NUMERIC(16,2) NOT NULL,
  descripcion                        VARCHAR(160) NOT NULL,
  CONSTRAINT pk_movimiento_contable PRIMARY KEY (id)
);

COMMENT ON TABLE nucleo_financiero.movimiento_contable IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. [append-only] Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN nucleo_financiero.movimiento_contable.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.movimiento_contable.asiento_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.movimiento_contable.cuenta_id IS 'FK, IDX';
