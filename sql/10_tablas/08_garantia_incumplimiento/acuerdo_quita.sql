-- acuerdo_quita · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: AcuerdoQuita
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.acuerdo_quita (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  registro_id                        UUID NOT NULL,
  aprobado_por                       UUID NOT NULL,
  acuerdo_grupo_id                   UUID,
  monto_original                     NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_condonado                    NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_a_pagar                      NUMERIC(14,2) DEFAULT 0 NOT NULL,
  justificacion                      VARCHAR(400) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  fecha                              TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_acuerdo_quita PRIMARY KEY (id),
  CONSTRAINT ck_acuerdo_quita_estado CHECK (estado IN ('APROBADO', 'EJECUTADO', 'PROPUESTO', 'RECHAZADO'))
);

COMMENT ON TABLE garantia.acuerdo_quita IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.acuerdo_quita.id IS 'PK';
COMMENT ON COLUMN garantia.acuerdo_quita.registro_id IS 'FK, UQ';
COMMENT ON COLUMN garantia.acuerdo_quita.aprobado_por IS 'FK';
COMMENT ON COLUMN garantia.acuerdo_quita.acuerdo_grupo_id IS 'FK, NULL, M2';
COMMENT ON COLUMN garantia.acuerdo_quita.estado IS 'CK';
