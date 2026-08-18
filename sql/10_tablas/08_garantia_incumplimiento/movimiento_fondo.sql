-- movimiento_fondo · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: MovimientoFondo
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.movimiento_fondo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  fondo_id                           UUID NOT NULL,
  asiento_contable_id                UUID,
  tipo                               VARCHAR(30) NOT NULL,
  monto                              NUMERIC(14,2) NOT NULL,
  saldo_resultante                   NUMERIC(16,2) DEFAULT 0 NOT NULL,
  referencia_tipo                    VARCHAR(30) NOT NULL,
  referencia_id                      UUID NOT NULL,
  descripcion                        VARCHAR(200) NOT NULL,
  fecha                              TIMESTAMPTZ NOT NULL,
  registrado_por                     UUID,
  CONSTRAINT pk_movimiento_fondo PRIMARY KEY (id),
  CONSTRAINT ck_movimiento_fondo_tipo CHECK (tipo IN ('AJUSTE_CONTABLE', 'APORTE_PERIODICO', 'CASTIGO_INCOBRABLE', 'COBERTURA_APLICADA', 'CONSTITUCION', 'DEVOLUCION_A_PARTICIPANTES', 'EJECUCION_AVAL', 'RECUPERACION_ACREDITADA', 'RENDIMIENTO'))
);

COMMENT ON TABLE garantia.movimiento_fondo IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. [append-only] El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.movimiento_fondo.id IS 'PK';
COMMENT ON COLUMN garantia.movimiento_fondo.fondo_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.movimiento_fondo.asiento_contable_id IS 'FK, NULL, M3';
COMMENT ON COLUMN garantia.movimiento_fondo.tipo IS 'CK, IDX';
COMMENT ON COLUMN garantia.movimiento_fondo.referencia_id IS 'IDX';
COMMENT ON COLUMN garantia.movimiento_fondo.fecha IS 'IDX';
COMMENT ON COLUMN garantia.movimiento_fondo.registrado_por IS 'FK, NULL';
