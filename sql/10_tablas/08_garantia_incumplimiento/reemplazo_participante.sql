-- reemplazo_participante · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: ReemplazoParticipante
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.reemplazo_participante (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  cupo_id                            UUID NOT NULL,
  registro_id                        UUID,
  participante_saliente_id           UUID NOT NULL,
  participante_entrante_id           UUID,
  acuerdo_grupo_id                   UUID,
  deuda_asumida_por_entrante         NUMERIC(14,2) NOT NULL,
  deuda_retenida_por_saliente        NUMERIC(14,2) NOT NULL,
  conserva_orden_de_turno            BOOLEAN DEFAULT FALSE NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  fecha                              TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_reemplazo_participante PRIMARY KEY (id),
  CONSTRAINT ck_reemplazo_participante_estado CHECK (estado IN ('APROBADO', 'BUSCANDO', 'EJECUTADO', 'FALLIDO', 'PROPUESTO'))
);

COMMENT ON TABLE garantia.reemplazo_participante IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.reemplazo_participante.id IS 'PK';
COMMENT ON COLUMN garantia.reemplazo_participante.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.reemplazo_participante.cupo_id IS 'FK';
COMMENT ON COLUMN garantia.reemplazo_participante.registro_id IS 'FK, NULL';
COMMENT ON COLUMN garantia.reemplazo_participante.participante_saliente_id IS 'FK';
COMMENT ON COLUMN garantia.reemplazo_participante.participante_entrante_id IS 'FK, NULL';
COMMENT ON COLUMN garantia.reemplazo_participante.acuerdo_grupo_id IS 'FK, NULL, M2';
COMMENT ON COLUMN garantia.reemplazo_participante.estado IS 'CK';
