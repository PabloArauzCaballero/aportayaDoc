-- devolucion_fondo · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: DevolucionFondo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.devolucion_fondo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  fondo_id                           UUID NOT NULL,
  participante_id                    UUID NOT NULL,
  monto_aportado                     NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_consumido                    NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_a_devolver                   NUMERIC(14,2) DEFAULT 0 NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  motivo_retencion                   VARCHAR(200),
  fecha                              TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_devolucion_fondo PRIMARY KEY (id),
  CONSTRAINT ck_devolucion_fondo_estado CHECK (estado IN ('CALCULADA', 'PAGADA', 'RETENIDA'))
);

COMMENT ON TABLE garantia.devolucion_fondo IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.devolucion_fondo.id IS 'PK';
COMMENT ON COLUMN garantia.devolucion_fondo.fondo_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.devolucion_fondo.participante_id IS 'FK';
COMMENT ON COLUMN garantia.devolucion_fondo.estado IS 'CK';
COMMENT ON COLUMN garantia.devolucion_fondo.motivo_retencion IS 'NULL';
