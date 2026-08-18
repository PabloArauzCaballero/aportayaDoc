-- capacitacion_cumplimiento · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: CapacitacionCumplimiento
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.capacitacion_cumplimiento (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  tema                               VARCHAR(120) NOT NULL,
  modalidad                          VARCHAR(15) NOT NULL,
  horas                              NUMERIC(5,2) NOT NULL,
  fecha                              DATE NOT NULL,
  calificacion                       NUMERIC(5,2),
  aprobada                           BOOLEAN DEFAULT FALSE NOT NULL,
  evidencia_url                      VARCHAR(255),
  periodo                            CHAR(4) NOT NULL,
  CONSTRAINT pk_capacitacion_cumplimiento PRIMARY KEY (id),
  CONSTRAINT ck_capacitacion_cumplimiento_modalidad CHECK (modalidad IN ('AUTOESTUDIO', 'MIXTA', 'PRESENCIAL', 'VIRTUAL'))
);

COMMENT ON TABLE cumplimiento.capacitacion_cumplimiento IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.capacitacion_cumplimiento.id IS 'PK';
COMMENT ON COLUMN cumplimiento.capacitacion_cumplimiento.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.capacitacion_cumplimiento.modalidad IS 'CK';
COMMENT ON COLUMN cumplimiento.capacitacion_cumplimiento.fecha IS 'IDX';
COMMENT ON COLUMN cumplimiento.capacitacion_cumplimiento.calificacion IS 'NULL';
COMMENT ON COLUMN cumplimiento.capacitacion_cumplimiento.evidencia_url IS 'NULL';
COMMENT ON COLUMN cumplimiento.capacitacion_cumplimiento.periodo IS 'IDX';
