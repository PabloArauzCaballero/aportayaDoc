-- requerimiento_autoridad · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: RequerimientoAutoridad
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.requerimiento_autoridad (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_afectado_id                UUID,
  bloqueo_saldo_id                   UUID,
  respondido_por                     UUID,
  autoridad                          VARCHAR(15) NOT NULL,
  numero_oficio                      VARCHAR(60) NOT NULL,
  fecha_recepcion                    TIMESTAMPTZ DEFAULT now() NOT NULL,
  plazo_respuesta                    TIMESTAMPTZ NOT NULL,
  alcance                            VARCHAR(300) NOT NULL,
  documento_url                      VARCHAR(255) NOT NULL,
  hash_documento                     VARCHAR(64) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  respuesta_url                      VARCHAR(255),
  respondido_en                      TIMESTAMPTZ,
  CONSTRAINT pk_requerimiento_autoridad PRIMARY KEY (id),
  CONSTRAINT ck_requerimiento_autoridad_autoridad CHECK (autoridad IN ('ASFI', 'FISCALIA', 'JUZGADO', 'POLICIA', 'SIN', 'UIF')),
  CONSTRAINT ck_requerimiento_autoridad_estado CHECK (estado IN ('ARCHIVADO', 'EN_PROCESO', 'RECIBIDO', 'RESPONDIDO', 'VENCIDO'))
);

COMMENT ON TABLE cumplimiento.requerimiento_autoridad IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.requerimiento_autoridad.id IS 'PK';
COMMENT ON COLUMN cumplimiento.requerimiento_autoridad.usuario_afectado_id IS 'FK, NULL, M1';
COMMENT ON COLUMN cumplimiento.requerimiento_autoridad.bloqueo_saldo_id IS 'FK, NULL, M10';
COMMENT ON COLUMN cumplimiento.requerimiento_autoridad.respondido_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.requerimiento_autoridad.autoridad IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.requerimiento_autoridad.numero_oficio IS 'UQ';
COMMENT ON COLUMN cumplimiento.requerimiento_autoridad.plazo_respuesta IS 'IDX';
COMMENT ON COLUMN cumplimiento.requerimiento_autoridad.estado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.requerimiento_autoridad.respuesta_url IS 'NULL';
COMMENT ON COLUMN cumplimiento.requerimiento_autoridad.respondido_en IS 'NULL';
