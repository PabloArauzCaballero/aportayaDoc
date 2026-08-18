-- disolucion_anticipada · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: DisolucionAnticipada
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.disolucion_anticipada (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  acuerdo_grupo_id                   UUID,
  causal                             VARCHAR(25) NOT NULL,
  motivo                             VARCHAR(400) NOT NULL,
  total_aportado_grupo               NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_entregado                    NUMERIC(16,2) DEFAULT 0 NOT NULL,
  saldo_a_distribuir                 NUMERIC(16,2) DEFAULT 0 NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  iniciada_en                        TIMESTAMPTZ NOT NULL,
  cerrada_en                         TIMESTAMPTZ,
  CONSTRAINT pk_disolucion_anticipada PRIMARY KEY (id),
  CONSTRAINT ck_disolucion_anticipada_causal CHECK (causal IN ('ACUERDO', 'CAUSA_GRAVE', 'MORA_GENERALIZADA', 'SIN_REEMPLAZO')),
  CONSTRAINT ck_disolucion_anticipada_estado CHECK (estado IN ('CALCULADA', 'CERRADA', 'EJECUTADA', 'INICIADA'))
);

COMMENT ON TABLE garantia.disolucion_anticipada IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.disolucion_anticipada.id IS 'PK';
COMMENT ON COLUMN garantia.disolucion_anticipada.grupo_id IS 'FK, UQ';
COMMENT ON COLUMN garantia.disolucion_anticipada.acuerdo_grupo_id IS 'FK, NULL, M2';
COMMENT ON COLUMN garantia.disolucion_anticipada.causal IS 'CK';
COMMENT ON COLUMN garantia.disolucion_anticipada.estado IS 'CK';
COMMENT ON COLUMN garantia.disolucion_anticipada.cerrada_en IS 'NULL';
