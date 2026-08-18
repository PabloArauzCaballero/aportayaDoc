-- liquidacion_participante · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: LiquidacionParticipante
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.liquidacion_participante (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  disolucion_id                      UUID NOT NULL,
  participante_id                    UUID NOT NULL,
  total_aportado                     NUMERIC(14,2) DEFAULT 0 NOT NULL,
  total_cobrado                      NUMERIC(14,2) DEFAULT 0 NOT NULL,
  deuda_pendiente                    NUMERIC(14,2) NOT NULL,
  posicion_neta                      NUMERIC(14,2) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  CONSTRAINT pk_liquidacion_participante PRIMARY KEY (id),
  CONSTRAINT ck_liquidacion_participante_estado CHECK (estado IN ('CALCULADA', 'EN_COBRANZA', 'PAGADA'))
);

COMMENT ON TABLE garantia.liquidacion_participante IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.liquidacion_participante.id IS 'PK';
COMMENT ON COLUMN garantia.liquidacion_participante.disolucion_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.liquidacion_participante.participante_id IS 'FK';
COMMENT ON COLUMN garantia.liquidacion_participante.estado IS 'CK';
