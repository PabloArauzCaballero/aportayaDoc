-- fondo_garantia · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: FondoGarantia
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.fondo_garantia (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  ambito                             VARCHAR(20) NOT NULL,
  grupo_id                           UUID,
  politica_cobertura_id              UUID NOT NULL,
  cuenta_contable_id                 UUID NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  saldo_disponible                   NUMERIC(16,2) DEFAULT 0 NOT NULL,
  saldo_comprometido                 NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_aportado                     NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_cubierto                     NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_recuperado                   NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_castigado                    NUMERIC(16,2) DEFAULT 0 NOT NULL,
  estado                             VARCHAR(20) NOT NULL,
  version                            INTEGER DEFAULT 0 NOT NULL,
  CONSTRAINT pk_fondo_garantia PRIMARY KEY (id),
  CONSTRAINT ck_fondo_garantia_ambito CHECK (ambito IN ('MUTUAL_PLATAFORMA', 'POR_GRUPO')),
  CONSTRAINT ck_fondo_garantia_saldo_disponible CHECK (saldo_disponible >= 0),
  CONSTRAINT ck_fondo_garantia_estado CHECK (estado IN ('ACTIVO', 'AGOTADO', 'CERRADO', 'EN_LIQUIDACION'))
);

COMMENT ON TABLE garantia.fondo_garantia IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.fondo_garantia.id IS 'PK';
COMMENT ON COLUMN garantia.fondo_garantia.ambito IS 'CK';
COMMENT ON COLUMN garantia.fondo_garantia.grupo_id IS 'FK, NULL, UQ parcial';
COMMENT ON COLUMN garantia.fondo_garantia.politica_cobertura_id IS 'FK';
COMMENT ON COLUMN garantia.fondo_garantia.cuenta_contable_id IS 'FK, M3';
COMMENT ON COLUMN garantia.fondo_garantia.saldo_disponible IS 'CK: >= 0';
COMMENT ON COLUMN garantia.fondo_garantia.estado IS 'CK';
