-- evidencia_incumplimiento · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: EvidenciaIncumplimiento
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.evidencia_incumplimiento (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  registro_id                        UUID NOT NULL,
  tipo                               VARCHAR(25) NOT NULL,
  descripcion                        VARCHAR(300) NOT NULL,
  url_archivo                        VARCHAR(255),
  hash_archivo                       VARCHAR(64),
  contenido_estructurado             JSONB,
  aportada_por                       UUID,
  fecha_hora                         TIMESTAMPTZ NOT NULL,
  es_inmutable                       BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_evidencia_incumplimiento PRIMARY KEY (id),
  CONSTRAINT ck_evidencia_incumplimiento_tipo CHECK (tipo IN ('ACTA_ACUERDO', 'CAPTURA_ESTADO', 'COMUNICACION', 'DOCUMENTO', 'EXTRACTO', 'LOG_SISTEMA'))
);

COMMENT ON TABLE garantia.evidencia_incumplimiento IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.evidencia_incumplimiento.id IS 'PK';
COMMENT ON COLUMN garantia.evidencia_incumplimiento.registro_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.evidencia_incumplimiento.tipo IS 'CK';
COMMENT ON COLUMN garantia.evidencia_incumplimiento.url_archivo IS 'NULL';
COMMENT ON COLUMN garantia.evidencia_incumplimiento.hash_archivo IS 'NULL';
COMMENT ON COLUMN garantia.evidencia_incumplimiento.contenido_estructurado IS 'NULL';
COMMENT ON COLUMN garantia.evidencia_incumplimiento.aportada_por IS 'FK, NULL';
