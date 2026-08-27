-- conciliacion_custodia · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: ConciliacionCustodia
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.conciliacion_custodia (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cuenta_custodia_id                 UUID NOT NULL,
  cierre_diario_id                   UUID,
  ejecutada_por                      UUID,
  fecha                              DATE NOT NULL,
  saldo_dinero_electronico           NUMERIC(18,2) DEFAULT 0 NOT NULL,
  saldo_custodia                     NUMERIC(18,2) DEFAULT 0 NOT NULL,
  saldo_en_transito                  NUMERIC(18,2) DEFAULT 0 NOT NULL,
  diferencia                         NUMERIC(18,2) GENERATED ALWAYS AS (saldo_custodia - saldo_dinero_electronico) STORED,
  ratio_cobertura                    NUMERIC(9,6) GENERATED ALWAYS AS (CASE WHEN saldo_dinero_electronico = 0 THEN 1 ELSE round(saldo_custodia / saldo_dinero_electronico, 6) END) STORED,
  cumple_encaje                      BOOLEAN DEFAULT FALSE NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  ejecutada_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_conciliacion_custodia PRIMARY KEY (id),
  CONSTRAINT ck_conciliacion_custodia_estado CHECK (estado IN ('CUADRADA', 'DESCUADRADA', 'EN_ANALISIS'))
);

COMMENT ON TABLE nucleo_financiero.conciliacion_custodia IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.conciliacion_custodia.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.conciliacion_custodia.cuenta_custodia_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.conciliacion_custodia.cierre_diario_id IS 'FK, NULL, M3';
COMMENT ON COLUMN nucleo_financiero.conciliacion_custodia.ejecutada_por IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.conciliacion_custodia.diferencia IS 'GENERATED';
COMMENT ON COLUMN nucleo_financiero.conciliacion_custodia.ratio_cobertura IS 'GENERATED';
COMMENT ON COLUMN nucleo_financiero.conciliacion_custodia.cumple_encaje IS 'IDX';
COMMENT ON COLUMN nucleo_financiero.conciliacion_custodia.estado IS 'CK, IDX';
