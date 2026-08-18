-- arqueo_punto_atencion · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: ArqueoPuntoAtencion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.arqueo_punto_atencion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  punto_atencion_id                  UUID NOT NULL,
  arqueado_por                       UUID NOT NULL,
  fecha                              DATE NOT NULL,
  saldo_inicial                      NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_recargas                     NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_retiros                      NUMERIC(16,2) DEFAULT 0 NOT NULL,
  saldo_teorico                      NUMERIC(16,2) DEFAULT 0 NOT NULL,
  saldo_contado                      NUMERIC(16,2) DEFAULT 0 NOT NULL,
  diferencia                         NUMERIC(16,2) GENERATED ALWAYS AS (saldo_contado - saldo_teorico) STORED,
  estado                             VARCHAR(15) NOT NULL,
  observaciones                      VARCHAR(300),
  cerrado_en                         TIMESTAMPTZ,
  CONSTRAINT pk_arqueo_punto_atencion PRIMARY KEY (id),
  CONSTRAINT ck_arqueo_punto_atencion_estado CHECK (estado IN ('ABIERTO', 'CERRADO', 'CUADRADO', 'DESCUADRADO'))
);

COMMENT ON TABLE nucleo_financiero.arqueo_punto_atencion IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.arqueo_punto_atencion.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.arqueo_punto_atencion.punto_atencion_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.arqueo_punto_atencion.arqueado_por IS 'FK';
COMMENT ON COLUMN nucleo_financiero.arqueo_punto_atencion.fecha IS 'UQ+punto_atencion_id';
COMMENT ON COLUMN nucleo_financiero.arqueo_punto_atencion.diferencia IS 'GENERATED';
COMMENT ON COLUMN nucleo_financiero.arqueo_punto_atencion.estado IS 'CK';
COMMENT ON COLUMN nucleo_financiero.arqueo_punto_atencion.observaciones IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.arqueo_punto_atencion.cerrado_en IS 'NULL';
