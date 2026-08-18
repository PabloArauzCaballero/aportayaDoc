-- prueba_control · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: PruebaControl
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.prueba_control (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  control_id                         UUID NOT NULL,
  ejecutada_por                      UUID NOT NULL,
  periodo                            CHAR(7) NOT NULL,
  tamanio_muestra                    INTEGER NOT NULL,
  excepciones                        INTEGER DEFAULT 0 NOT NULL,
  resultado                          VARCHAR(12) NOT NULL,
  evidencia_url                      VARCHAR(255),
  ejecutada_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_prueba_control PRIMARY KEY (id),
  CONSTRAINT ck_prueba_control_resultado CHECK (resultado IN ('DEFICIENTE', 'EFECTIVO', 'NO_EFECTIVO'))
);

COMMENT ON TABLE cumplimiento.prueba_control IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.prueba_control.id IS 'PK';
COMMENT ON COLUMN cumplimiento.prueba_control.control_id IS 'FK, IDX';
COMMENT ON COLUMN cumplimiento.prueba_control.ejecutada_por IS 'FK';
COMMENT ON COLUMN cumplimiento.prueba_control.periodo IS 'UQ+control_id';
COMMENT ON COLUMN cumplimiento.prueba_control.resultado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.prueba_control.evidencia_url IS 'NULL';
