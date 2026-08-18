-- candidato_reemplazo · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: CandidatoReemplazo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.candidato_reemplazo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  reemplazo_id                       UUID NOT NULL,
  usuario_id                         UUID NOT NULL,
  puntaje_reputacion                 NUMERIC(6,2) NOT NULL,
  acepta_condiciones                 BOOLEAN DEFAULT FALSE NOT NULL,
  fuente_candidato                   VARCHAR(20) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  CONSTRAINT pk_candidato_reemplazo PRIMARY KEY (id),
  CONSTRAINT ck_candidato_reemplazo_fuente_candidato CHECK (fuente_candidato IN ('EMPAREJAMIENTO', 'INVITACION', 'LISTA_ESPERA')),
  CONSTRAINT ck_candidato_reemplazo_estado CHECK (estado IN ('ACEPTADO', 'DESCARTADO', 'PROPUESTO'))
);

COMMENT ON TABLE garantia.candidato_reemplazo IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.candidato_reemplazo.id IS 'PK';
COMMENT ON COLUMN garantia.candidato_reemplazo.reemplazo_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.candidato_reemplazo.usuario_id IS 'FK';
COMMENT ON COLUMN garantia.candidato_reemplazo.fuente_candidato IS 'CK';
COMMENT ON COLUMN garantia.candidato_reemplazo.estado IS 'CK';
