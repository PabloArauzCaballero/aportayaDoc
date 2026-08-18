-- cierre_diario · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: CierreDiario
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.cierre_diario (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  fecha                              DATE NOT NULL,
  total_recaudado                    NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_conciliado                   NUMERIC(16,2) DEFAULT 0 NOT NULL,
  total_excepciones                  NUMERIC(16,2) DEFAULT 0 NOT NULL,
  cantidad_pagos                     INTEGER DEFAULT 0 NOT NULL,
  cuadrado                           BOOLEAN DEFAULT FALSE NOT NULL,
  cerrado_por                        UUID NOT NULL,
  cerrado_en                         TIMESTAMPTZ NOT NULL,
  reabierto_en                       TIMESTAMPTZ,
  CONSTRAINT pk_cierre_diario PRIMARY KEY (id)
);

COMMENT ON TABLE nucleo_financiero.cierre_diario IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN nucleo_financiero.cierre_diario.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.cierre_diario.fecha IS 'UQ';
COMMENT ON COLUMN nucleo_financiero.cierre_diario.cerrado_por IS 'FK';
COMMENT ON COLUMN nucleo_financiero.cierre_diario.reabierto_en IS 'NULL';
