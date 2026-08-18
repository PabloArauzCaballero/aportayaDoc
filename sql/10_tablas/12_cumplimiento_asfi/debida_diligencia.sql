-- debida_diligencia · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: DebidaDiligencia
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.debida_diligencia (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  verificacion_kyc_id                UUID,
  calificacion_riesgo_id             UUID,
  aprobada_por                       UUID,
  segunda_revision_por               UUID,
  tipo                               VARCHAR(15) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  documentos_requeridos              JSONB NOT NULL,
  documentos_recibidos               JSONB NOT NULL,
  observaciones                      VARCHAR(500),
  iniciada_en                        TIMESTAMPTZ NOT NULL,
  completada_en                      TIMESTAMPTZ,
  vence_en                           TIMESTAMPTZ,
  CONSTRAINT pk_debida_diligencia PRIMARY KEY (id),
  CONSTRAINT ck_debida_diligencia_tipo CHECK (tipo IN ('AMPLIADA', 'CONTINUA', 'ESTANDAR', 'REFORZADA', 'SIMPLIFICADA')),
  CONSTRAINT ck_debida_diligencia_estado CHECK (estado IN ('COMPLETA', 'EN_PROCESO', 'OBSERVADA', 'PENDIENTE', 'RECHAZADA', 'VENCIDA'))
);

COMMENT ON TABLE cumplimiento.debida_diligencia IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.debida_diligencia.id IS 'PK';
COMMENT ON COLUMN cumplimiento.debida_diligencia.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.debida_diligencia.verificacion_kyc_id IS 'FK, NULL, M1';
COMMENT ON COLUMN cumplimiento.debida_diligencia.calificacion_riesgo_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.debida_diligencia.aprobada_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.debida_diligencia.segunda_revision_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.debida_diligencia.tipo IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.debida_diligencia.estado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.debida_diligencia.observaciones IS 'NULL';
COMMENT ON COLUMN cumplimiento.debida_diligencia.completada_en IS 'NULL';
COMMENT ON COLUMN cumplimiento.debida_diligencia.vence_en IS 'NULL, IDX';
