-- comite_gobierno · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: ComiteGobierno
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.comite_gobierno (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  tipo                               VARCHAR(30) NOT NULL,
  periodicidad_minima                VARCHAR(15) NOT NULL,
  composicion_requerida              JSONB NOT NULL,
  quorum_minimo                      SMALLINT NOT NULL,
  activo                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_comite_gobierno PRIMARY KEY (id),
  CONSTRAINT ck_comite_gobierno_tipo CHECK (tipo IN ('AUDITORIA', 'CUMPLIMIENTO', 'DIRECTORIO', 'RIESGOS', 'SEGURIDAD_INFORMACION')),
  CONSTRAINT ck_comite_gobierno_periodicidad_minima CHECK (periodicidad_minima IN ('ANUAL', 'BIMESTRAL', 'CONTINUA', 'DIARIA', 'MENSUAL', 'QUINCENAL', 'SEMANAL', 'SEMESTRAL', 'TRIMESTRAL'))
);

COMMENT ON TABLE cumplimiento.comite_gobierno IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.comite_gobierno.id IS 'PK';
COMMENT ON COLUMN cumplimiento.comite_gobierno.tipo IS 'CK, UQ';
COMMENT ON COLUMN cumplimiento.comite_gobierno.periodicidad_minima IS 'CK';
