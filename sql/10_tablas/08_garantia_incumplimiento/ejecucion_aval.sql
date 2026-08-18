-- ejecucion_aval · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: EjecucionAval
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.ejecucion_aval (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  aval_id                            UUID NOT NULL,
  registro_id                        UUID NOT NULL,
  deuda_id                           UUID NOT NULL,
  pago_id                            UUID,
  monto_ejecutado                    NUMERIC(14,2) DEFAULT 0 NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  notificada_en                      TIMESTAMPTZ NOT NULL,
  plazo_respuesta                    TIMESTAMPTZ NOT NULL,
  genera_deuda_del_avalista          BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_ejecucion_aval PRIMARY KEY (id),
  CONSTRAINT ck_ejecucion_aval_estado CHECK (estado IN ('ACEPTADA', 'EN_DISPUTA', 'NOTIFICADA', 'PAGADA', 'RECHAZADA'))
);

COMMENT ON TABLE garantia.ejecucion_aval IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.ejecucion_aval.id IS 'PK';
COMMENT ON COLUMN garantia.ejecucion_aval.aval_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.ejecucion_aval.registro_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.ejecucion_aval.deuda_id IS 'FK';
COMMENT ON COLUMN garantia.ejecucion_aval.pago_id IS 'FK, NULL, M3';
COMMENT ON COLUMN garantia.ejecucion_aval.estado IS 'CK';
