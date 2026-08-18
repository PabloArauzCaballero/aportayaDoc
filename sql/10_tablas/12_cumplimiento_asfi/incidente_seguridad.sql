-- incidente_seguridad · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: IncidenteSeguridad
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.incidente_seguridad (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(20) NOT NULL,
  activo_informacion_id              UUID,
  incidente_operativo_id             UUID,
  evento_riesgo_id                   UUID,
  responsable_id                     UUID,
  tipo                               VARCHAR(30) NOT NULL,
  severidad                          VARCHAR(10) NOT NULL,
  vector_ataque                      VARCHAR(60),
  datos_personales_afectados         BOOLEAN DEFAULT FALSE NOT NULL,
  usuarios_afectados                 INTEGER NOT NULL,
  detectado_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  contenido_en                       TIMESTAMPTZ,
  reportado_al_organismo_en          TIMESTAMPTZ,
  notificado_a_titulares_en          TIMESTAMPTZ,
  plazo_reporte                      TIMESTAMPTZ NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  leccion_aprendida                  TEXT,
  CONSTRAINT pk_incidente_seguridad PRIMARY KEY (id),
  CONSTRAINT ck_incidente_seguridad_tipo CHECK (tipo IN ('ACCESO_NO_AUTORIZADO', 'DENEGACION_SERVICIO', 'FRAUDE_TECNOLOGICO', 'FUGA_DE_DATOS', 'MALWARE', 'PHISHING')),
  CONSTRAINT ck_incidente_seguridad_severidad CHECK (severidad IN ('ALTA', 'BAJA', 'CRITICA', 'MEDIA')),
  CONSTRAINT ck_incidente_seguridad_estado CHECK (estado IN ('CERRADO', 'CONTENIDO', 'DETECTADO', 'EN_CONTENCION', 'ERRADICADO'))
);

COMMENT ON TABLE cumplimiento.incidente_seguridad IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.id IS 'PK';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.codigo IS 'UQ';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.activo_informacion_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.incidente_operativo_id IS 'FK, NULL, M9';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.evento_riesgo_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.responsable_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.tipo IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.severidad IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.vector_ataque IS 'NULL';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.datos_personales_afectados IS 'IDX';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.detectado_en IS 'IDX';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.contenido_en IS 'NULL';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.reportado_al_organismo_en IS 'NULL';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.notificado_a_titulares_en IS 'NULL';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.plazo_reporte IS 'IDX';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.estado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.incidente_seguridad.leccion_aprendida IS 'NULL';
