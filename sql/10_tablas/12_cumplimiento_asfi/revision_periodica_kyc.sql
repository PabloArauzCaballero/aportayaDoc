-- revision_periodica_kyc · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: RevisionPeriodicaKyc
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.revision_periodica_kyc (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  calificacion_riesgo_id             UUID,
  ejecutada_por                      UUID,
  fecha_programada                   DATE NOT NULL,
  fecha_ejecutada                    DATE,
  resultado                          VARCHAR(30),
  estado                             VARCHAR(15) NOT NULL,
  CONSTRAINT pk_revision_periodica_kyc PRIMARY KEY (id),
  CONSTRAINT ck_revision_periodica_kyc_estado CHECK (estado IN ('EJECUTADA', 'EN_CURSO', 'PROGRAMADA', 'VENCIDA'))
);

COMMENT ON TABLE cumplimiento.revision_periodica_kyc IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.revision_periodica_kyc.id IS 'PK';
COMMENT ON COLUMN cumplimiento.revision_periodica_kyc.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.revision_periodica_kyc.calificacion_riesgo_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.revision_periodica_kyc.ejecutada_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.revision_periodica_kyc.fecha_programada IS 'IDX';
COMMENT ON COLUMN cumplimiento.revision_periodica_kyc.fecha_ejecutada IS 'NULL';
COMMENT ON COLUMN cumplimiento.revision_periodica_kyc.resultado IS 'NULL';
COMMENT ON COLUMN cumplimiento.revision_periodica_kyc.estado IS 'CK, IDX';
