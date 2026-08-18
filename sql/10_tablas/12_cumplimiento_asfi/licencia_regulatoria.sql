-- licencia_regulatoria · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: LicenciaRegulatoria
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS catalogo.licencia_regulatoria (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  organismo                          VARCHAR(10) NOT NULL,
  tipo                               VARCHAR(30) NOT NULL,
  categoria_actividad                VARCHAR(40) NOT NULL,
  numero_resolucion                  VARCHAR(60),
  estado                             VARCHAR(15) NOT NULL,
  fecha_solicitud                    DATE NOT NULL,
  fecha_otorgamiento                 DATE,
  vigente_hasta                      DATE,
  alcance_autorizado                 JSONB NOT NULL,
  garantia_seriedad                  NUMERIC(16,2),
  documento_url                      VARCHAR(255),
  hash_documento                     VARCHAR(64),
  responsable_id                     UUID,
  CONSTRAINT pk_licencia_regulatoria PRIMARY KEY (id),
  CONSTRAINT ck_licencia_regulatoria_organismo CHECK (organismo IN ('ASFI', 'BCB', 'SIN')),
  CONSTRAINT ck_licencia_regulatoria_tipo CHECK (tipo IN ('AUTORIZACION_ESPECIFICA', 'CERTIFICADO_ADECUACION', 'LICENCIA_FUNCIONAMIENTO')),
  CONSTRAINT ck_licencia_regulatoria_categoria_actividad CHECK (categoria_actividad IN ('BLOCKCHAIN_PSAV', 'PAGOS_Y_PLATAFORMAS_DE_PAGO', 'PLATAFORMAS_FINANCIAMIENTO', 'TECNOLOGIAS_EMPRESARIALES')),
  CONSTRAINT ck_licencia_regulatoria_estado CHECK (estado IN ('CONDICIONADA', 'EN_TRAMITE', 'OTORGADA', 'REVOCADA', 'SUSPENDIDA'))
);

COMMENT ON TABLE catalogo.licencia_regulatoria IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN catalogo.licencia_regulatoria.id IS 'PK';
COMMENT ON COLUMN catalogo.licencia_regulatoria.organismo IS 'CK, IDX';
COMMENT ON COLUMN catalogo.licencia_regulatoria.tipo IS 'CK';
COMMENT ON COLUMN catalogo.licencia_regulatoria.categoria_actividad IS 'CK';
COMMENT ON COLUMN catalogo.licencia_regulatoria.numero_resolucion IS 'UQ, NULL';
COMMENT ON COLUMN catalogo.licencia_regulatoria.estado IS 'CK, IDX';
COMMENT ON COLUMN catalogo.licencia_regulatoria.fecha_otorgamiento IS 'NULL';
COMMENT ON COLUMN catalogo.licencia_regulatoria.vigente_hasta IS 'NULL';
COMMENT ON COLUMN catalogo.licencia_regulatoria.garantia_seriedad IS 'NULL';
COMMENT ON COLUMN catalogo.licencia_regulatoria.documento_url IS 'NULL';
COMMENT ON COLUMN catalogo.licencia_regulatoria.hash_documento IS 'NULL';
COMMENT ON COLUMN catalogo.licencia_regulatoria.responsable_id IS 'FK, NULL';
