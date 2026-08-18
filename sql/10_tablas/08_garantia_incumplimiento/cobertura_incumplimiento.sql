-- cobertura_incumplimiento · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: CoberturaIncumplimiento
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.cobertura_incumplimiento (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  fondo_id                           UUID NOT NULL,
  registro_id                        UUID NOT NULL,
  obligacion_id                      UUID NOT NULL,
  periodo_id                         UUID NOT NULL,
  movimiento_fondo_id                UUID,
  asiento_contable_id                UUID,
  aprobada_por                       UUID,
  monto_solicitado                   NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_cubierto                     NUMERIC(14,2) DEFAULT 0 NOT NULL,
  porcentaje_cobertura               NUMERIC(5,2) NOT NULL,
  estado                             VARCHAR(25) NOT NULL,
  requirio_aprobacion_manual         BOOLEAN DEFAULT FALSE NOT NULL,
  motivo_rechazo                     VARCHAR(300),
  solicitada_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  aplicada_en                        TIMESTAMPTZ,
  CONSTRAINT pk_cobertura_incumplimiento PRIMARY KEY (id),
  CONSTRAINT ck_cobertura_incumplimiento_estado CHECK (estado IN ('APLICADA', 'APROBADA', 'EN_RECUPERACION', 'INCOBRABLE', 'RECHAZADA', 'RECUPERADA_PARCIAL', 'RECUPERADA_TOTAL', 'REVERSADA', 'SOLICITADA'))
);

COMMENT ON TABLE garantia.cobertura_incumplimiento IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.cobertura_incumplimiento.id IS 'PK';
COMMENT ON COLUMN garantia.cobertura_incumplimiento.fondo_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.cobertura_incumplimiento.registro_id IS 'FK, UQ';
COMMENT ON COLUMN garantia.cobertura_incumplimiento.obligacion_id IS 'FK, UQ, M3';
COMMENT ON COLUMN garantia.cobertura_incumplimiento.periodo_id IS 'FK, M2';
COMMENT ON COLUMN garantia.cobertura_incumplimiento.movimiento_fondo_id IS 'FK, NULL';
COMMENT ON COLUMN garantia.cobertura_incumplimiento.asiento_contable_id IS 'FK, NULL, M3';
COMMENT ON COLUMN garantia.cobertura_incumplimiento.aprobada_por IS 'FK, NULL';
COMMENT ON COLUMN garantia.cobertura_incumplimiento.estado IS 'CK, IDX';
COMMENT ON COLUMN garantia.cobertura_incumplimiento.motivo_rechazo IS 'NULL';
COMMENT ON COLUMN garantia.cobertura_incumplimiento.aplicada_en IS 'NULL';
