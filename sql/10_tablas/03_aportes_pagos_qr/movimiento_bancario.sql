-- movimiento_bancario · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: MovimientoBancario
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.movimiento_bancario (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  extracto_id                        UUID NOT NULL,
  fecha_movimiento                   DATE NOT NULL,
  monto                              NUMERIC(14,2) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  glosa                              VARCHAR(200) NOT NULL,
  referencia_banco                   VARCHAR(80) NOT NULL,
  cuenta_origen                      VARCHAR(40),
  conciliado                         BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_movimiento_bancario PRIMARY KEY (id)
);

COMMENT ON TABLE aportes.movimiento_bancario IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.movimiento_bancario.id IS 'PK';
COMMENT ON COLUMN aportes.movimiento_bancario.extracto_id IS 'FK, IDX';
COMMENT ON COLUMN aportes.movimiento_bancario.fecha_movimiento IS 'IDX';
COMMENT ON COLUMN aportes.movimiento_bancario.glosa IS 'IDX full-text';
COMMENT ON COLUMN aportes.movimiento_bancario.referencia_banco IS 'UQ+extracto_id';
COMMENT ON COLUMN aportes.movimiento_bancario.cuenta_origen IS 'NULL';
COMMENT ON COLUMN aportes.movimiento_bancario.conciliado IS 'IDX';
