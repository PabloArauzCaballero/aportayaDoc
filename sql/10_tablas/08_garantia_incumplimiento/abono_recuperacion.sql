-- abono_recuperacion · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: AbonoRecuperacion
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.abono_recuperacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  deuda_id                           UUID NOT NULL,
  pago_id                            UUID,
  entrega_id                         UUID,
  movimiento_fondo_id                UUID,
  monto                              NUMERIC(14,2) NOT NULL,
  origen                             VARCHAR(30) NOT NULL,
  aplicado_a_capital                 NUMERIC(14,2) NOT NULL,
  aplicado_a_recargos                NUMERIC(14,2) NOT NULL,
  saldo_resultante                   NUMERIC(14,2) DEFAULT 0 NOT NULL,
  fecha                              TIMESTAMPTZ NOT NULL,
  registrado_por                     UUID,
  revertido_en                       TIMESTAMPTZ,
  CONSTRAINT pk_abono_recuperacion PRIMARY KEY (id),
  CONSTRAINT ck_abono_recuperacion_monto CHECK (monto > 0),
  CONSTRAINT ck_abono_recuperacion_origen CHECK (origen IN ('ACUERDO_QUITA', 'COMPENSACION', 'DESCUENTO_DE_ENTREGA', 'EJECUCION_AVAL', 'PAGO_VOLUNTARIO'))
);

COMMENT ON TABLE garantia.abono_recuperacion IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. [append-only] El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.abono_recuperacion.id IS 'PK';
COMMENT ON COLUMN garantia.abono_recuperacion.deuda_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.abono_recuperacion.pago_id IS 'FK, NULL, M3';
COMMENT ON COLUMN garantia.abono_recuperacion.entrega_id IS 'FK, NULL, M4';
COMMENT ON COLUMN garantia.abono_recuperacion.movimiento_fondo_id IS 'FK, NULL';
COMMENT ON COLUMN garantia.abono_recuperacion.monto IS 'CK: > 0';
COMMENT ON COLUMN garantia.abono_recuperacion.origen IS 'CK';
COMMENT ON COLUMN garantia.abono_recuperacion.fecha IS 'IDX';
COMMENT ON COLUMN garantia.abono_recuperacion.registrado_por IS 'FK, NULL';
COMMENT ON COLUMN garantia.abono_recuperacion.revertido_en IS 'NULL';
