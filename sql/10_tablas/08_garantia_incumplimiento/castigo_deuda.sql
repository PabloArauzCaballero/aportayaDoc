-- castigo_deuda · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: CastigoDeuda
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.castigo_deuda (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  deuda_id                           UUID NOT NULL,
  aprobado_por                       UUID NOT NULL,
  asiento_contable_id                UUID,
  monto_castigado                    NUMERIC(14,2) DEFAULT 0 NOT NULL,
  motivo                             VARCHAR(30) NOT NULL,
  justificacion                      VARCHAR(400) NOT NULL,
  mantiene_registro_reputacional     BOOLEAN DEFAULT FALSE NOT NULL,
  fecha                              TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_castigo_deuda PRIMARY KEY (id),
  CONSTRAINT ck_castigo_deuda_motivo CHECK (motivo IN ('COSTO_MAYOR_QUE_DEUDA', 'FALLECIMIENTO', 'INCOBRABLE', 'PRESCRIPCION', 'RESOLUCION_LEGAL'))
);

COMMENT ON TABLE garantia.castigo_deuda IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.castigo_deuda.id IS 'PK';
COMMENT ON COLUMN garantia.castigo_deuda.deuda_id IS 'FK, UQ';
COMMENT ON COLUMN garantia.castigo_deuda.aprobado_por IS 'FK';
COMMENT ON COLUMN garantia.castigo_deuda.asiento_contable_id IS 'FK, NULL, M3';
COMMENT ON COLUMN garantia.castigo_deuda.motivo IS 'CK';
