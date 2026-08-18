-- aval_participante · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: AvalParticipante
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.aval_participante (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  participante_avalado_id            UUID NOT NULL,
  avalista_usuario_id                UUID NOT NULL,
  token_aceptacion_id                UUID,
  es_participante_del_grupo          BOOLEAN DEFAULT FALSE NOT NULL,
  monto_maximo_avalado               NUMERIC(14,2) DEFAULT 0 NOT NULL,
  alcance                            VARCHAR(15) NOT NULL,
  porcentaje_responsabilidad         NUMERIC(5,2) NOT NULL,
  aceptado_en                        TIMESTAMPTZ,
  estado                             VARCHAR(15) NOT NULL,
  liberado_en                        TIMESTAMPTZ,
  CONSTRAINT pk_aval_participante PRIMARY KEY (id),
  CONSTRAINT ck_aval_participante_alcance CHECK (alcance IN ('PORCENTAJE', 'TOTAL', 'UN_PERIODO')),
  CONSTRAINT ck_aval_participante_estado CHECK (estado IN ('EJECUTADO', 'LIBERADO', 'PENDIENTE', 'RECHAZADO', 'VIGENTE'))
);

COMMENT ON TABLE garantia.aval_participante IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.aval_participante.id IS 'PK';
COMMENT ON COLUMN garantia.aval_participante.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.aval_participante.participante_avalado_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.aval_participante.avalista_usuario_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.aval_participante.token_aceptacion_id IS 'FK, NULL, M1';
COMMENT ON COLUMN garantia.aval_participante.alcance IS 'CK';
COMMENT ON COLUMN garantia.aval_participante.aceptado_en IS 'NULL';
COMMENT ON COLUMN garantia.aval_participante.estado IS 'CK, IDX';
COMMENT ON COLUMN garantia.aval_participante.liberado_en IS 'NULL';
