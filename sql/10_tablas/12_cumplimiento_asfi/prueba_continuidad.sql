-- prueba_continuidad · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: PruebaContinuidad
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.prueba_continuidad (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  plan_continuidad_id                UUID NOT NULL,
  acta_comite_id                     UUID,
  ejecutada_por                      UUID NOT NULL,
  tipo                               VARCHAR(25) NOT NULL,
  fecha                              DATE NOT NULL,
  rto_obtenido_minutos               INTEGER NOT NULL,
  rpo_obtenido_minutos               INTEGER NOT NULL,
  resultado                          VARCHAR(10) NOT NULL,
  hallazgos                          TEXT,
  evidencia_url                      VARCHAR(255),
  CONSTRAINT pk_prueba_continuidad PRIMARY KEY (id),
  CONSTRAINT ck_prueba_continuidad_tipo CHECK (tipo IN ('CONMUTACION_REAL', 'ESCRITORIO', 'PARCIAL', 'TOTAL')),
  CONSTRAINT ck_prueba_continuidad_resultado CHECK (resultado IN ('EXITOSA', 'FALLIDA', 'PARCIAL'))
);

COMMENT ON TABLE cumplimiento.prueba_continuidad IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.prueba_continuidad.id IS 'PK';
COMMENT ON COLUMN cumplimiento.prueba_continuidad.plan_continuidad_id IS 'FK, IDX';
COMMENT ON COLUMN cumplimiento.prueba_continuidad.acta_comite_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.prueba_continuidad.ejecutada_por IS 'FK';
COMMENT ON COLUMN cumplimiento.prueba_continuidad.tipo IS 'CK';
COMMENT ON COLUMN cumplimiento.prueba_continuidad.fecha IS 'IDX';
COMMENT ON COLUMN cumplimiento.prueba_continuidad.resultado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.prueba_continuidad.hallazgos IS 'NULL';
COMMENT ON COLUMN cumplimiento.prueba_continuidad.evidencia_url IS 'NULL';
