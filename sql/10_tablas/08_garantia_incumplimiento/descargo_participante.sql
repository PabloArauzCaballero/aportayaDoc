-- descargo_participante · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: DescargoParticipante
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.descargo_participante (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  registro_id                        UUID NOT NULL,
  participante_id                    UUID NOT NULL,
  argumento                          TEXT NOT NULL,
  evidencias                         JSONB NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  resolucion                         VARCHAR(400),
  resuelto_por                       UUID,
  presentado_en                      TIMESTAMPTZ NOT NULL,
  resuelto_en                        TIMESTAMPTZ,
  CONSTRAINT pk_descargo_participante PRIMARY KEY (id),
  CONSTRAINT ck_descargo_participante_estado CHECK (estado IN ('ACEPTADO', 'EN_ANALISIS', 'PRESENTADO', 'RECHAZADO'))
);

COMMENT ON TABLE garantia.descargo_participante IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.descargo_participante.id IS 'PK';
COMMENT ON COLUMN garantia.descargo_participante.registro_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.descargo_participante.participante_id IS 'FK';
COMMENT ON COLUMN garantia.descargo_participante.estado IS 'CK';
COMMENT ON COLUMN garantia.descargo_participante.resolucion IS 'NULL';
COMMENT ON COLUMN garantia.descargo_participante.resuelto_por IS 'FK, NULL';
COMMENT ON COLUMN garantia.descargo_participante.resuelto_en IS 'NULL';
