-- control_interno · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: ControlInterno
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.control_interno (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(20) NOT NULL,
  proceso                            VARCHAR(60) NOT NULL,
  descripcion                        VARCHAR(300) NOT NULL,
  tipo                               VARCHAR(12) NOT NULL,
  frecuencia                         VARCHAR(15) NOT NULL,
  automatizado                       BOOLEAN DEFAULT FALSE NOT NULL,
  riesgo_mitigado                    VARCHAR(120) NOT NULL,
  responsable_id                     UUID,
  activo                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_control_interno PRIMARY KEY (id),
  CONSTRAINT ck_control_interno_tipo CHECK (tipo IN ('CORRECTIVO', 'DETECTIVO', 'PREVENTIVO')),
  CONSTRAINT ck_control_interno_frecuencia CHECK (frecuencia IN ('ANUAL', 'BIMESTRAL', 'CONTINUA', 'DIARIA', 'MENSUAL', 'QUINCENAL', 'SEMANAL', 'SEMESTRAL', 'TRIMESTRAL'))
);

COMMENT ON TABLE cumplimiento.control_interno IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.control_interno.id IS 'PK';
COMMENT ON COLUMN cumplimiento.control_interno.codigo IS 'UQ';
COMMENT ON COLUMN cumplimiento.control_interno.proceso IS 'IDX';
COMMENT ON COLUMN cumplimiento.control_interno.tipo IS 'CK';
COMMENT ON COLUMN cumplimiento.control_interno.frecuencia IS 'CK';
COMMENT ON COLUMN cumplimiento.control_interno.responsable_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.control_interno.activo IS 'IDX';
