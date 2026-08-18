-- historial_estado_incumplimiento · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: HistorialEstadoIncumplimiento
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.historial_estado_incumplimiento (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  registro_id                        UUID NOT NULL,
  estado_anterior                    VARCHAR(30),
  estado_nuevo                       VARCHAR(30) NOT NULL,
  motivo                             VARCHAR(300) NOT NULL,
  monto_asociado                     NUMERIC(14,2),
  ejecutado_por                      UUID,
  es_automatico                      BOOLEAN DEFAULT FALSE NOT NULL,
  fecha_hora                         TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_historial_estado_incumplimiento PRIMARY KEY (id)
);

COMMENT ON TABLE garantia.historial_estado_incumplimiento IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. [append-only] El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.historial_estado_incumplimiento.id IS 'PK';
COMMENT ON COLUMN garantia.historial_estado_incumplimiento.registro_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.historial_estado_incumplimiento.estado_anterior IS 'NULL';
COMMENT ON COLUMN garantia.historial_estado_incumplimiento.monto_asociado IS 'NULL';
COMMENT ON COLUMN garantia.historial_estado_incumplimiento.ejecutado_por IS 'FK, NULL';
COMMENT ON COLUMN garantia.historial_estado_incumplimiento.fecha_hora IS 'IDX';
