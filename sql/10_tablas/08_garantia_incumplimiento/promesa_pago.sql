-- promesa_pago · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: PromesaPago
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.promesa_pago (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  gestion_id                         UUID NOT NULL,
  monto_prometido                    NUMERIC(14,2) DEFAULT 0 NOT NULL,
  fecha_prometida                    DATE NOT NULL,
  canal_compromiso                   VARCHAR(20) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  monto_efectivo                     NUMERIC(14,2) DEFAULT 0 NOT NULL,
  registrada_por                     UUID,
  creada_en                          TIMESTAMPTZ DEFAULT now() NOT NULL,
  evaluada_en                        TIMESTAMPTZ,
  CONSTRAINT pk_promesa_pago PRIMARY KEY (id),
  CONSTRAINT ck_promesa_pago_estado CHECK (estado IN ('CUMPLIDA', 'INCUMPLIDA', 'PARCIAL', 'VIGENTE'))
);

COMMENT ON TABLE garantia.promesa_pago IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.promesa_pago.id IS 'PK';
COMMENT ON COLUMN garantia.promesa_pago.gestion_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.promesa_pago.fecha_prometida IS 'IDX';
COMMENT ON COLUMN garantia.promesa_pago.estado IS 'CK, IDX';
COMMENT ON COLUMN garantia.promesa_pago.registrada_por IS 'FK, NULL';
COMMENT ON COLUMN garantia.promesa_pago.evaluada_en IS 'NULL';
