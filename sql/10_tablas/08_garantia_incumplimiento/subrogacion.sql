-- subrogacion · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: Subrogacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.subrogacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cobertura_id                       UUID NOT NULL,
  deuda_id                           UUID NOT NULL,
  acreedor_original                  VARCHAR(30) NOT NULL,
  acreedor_subrogado                 VARCHAR(30) NOT NULL,
  monto_subrogado                    NUMERIC(14,2) DEFAULT 0 NOT NULL,
  fecha                              TIMESTAMPTZ NOT NULL,
  documento_respaldo_url             VARCHAR(255),
  CONSTRAINT pk_subrogacion PRIMARY KEY (id)
);

COMMENT ON TABLE garantia.subrogacion IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.subrogacion.id IS 'PK';
COMMENT ON COLUMN garantia.subrogacion.cobertura_id IS 'FK, UQ';
COMMENT ON COLUMN garantia.subrogacion.deuda_id IS 'FK, UQ';
COMMENT ON COLUMN garantia.subrogacion.documento_respaldo_url IS 'NULL';
