-- politica_mora · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: PoliticaMora
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.politica_mora (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID,
  dias_gracia                        SMALLINT NOT NULL,
  tipo_recargo                       VARCHAR(20) NOT NULL,
  valor_recargo                      NUMERIC(10,2) NOT NULL,
  tope_recargo                       NUMERIC(14,2) NOT NULL,
  dias_para_mora_grave               SMALLINT NOT NULL,
  dias_para_incumplimiento           SMALLINT NOT NULL,
  aplica_automatico                  BOOLEAN DEFAULT FALSE NOT NULL,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_politica_mora PRIMARY KEY (id),
  CONSTRAINT ck_politica_mora_tipo_recargo CHECK (tipo_recargo IN ('DIARIO_COMPUESTO', 'FIJO', 'PORCENTUAL'))
);

COMMENT ON TABLE aportes.politica_mora IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.politica_mora.id IS 'PK';
COMMENT ON COLUMN aportes.politica_mora.grupo_id IS 'FK, NULL';
COMMENT ON COLUMN aportes.politica_mora.tipo_recargo IS 'CK';
