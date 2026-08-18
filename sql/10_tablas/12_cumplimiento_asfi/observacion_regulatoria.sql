-- observacion_regulatoria · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: ObservacionRegulatoria
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.observacion_regulatoria (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  envio_regulatorio_id               UUID,
  responsable_id                     UUID,
  organismo                          VARCHAR(10) NOT NULL,
  tipo                               VARCHAR(15) NOT NULL,
  numero_documento                   VARCHAR(60) NOT NULL,
  descripcion                        TEXT NOT NULL,
  monto_multa                        NUMERIC(16,2),
  plazo_respuesta                    DATE NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  respuesta                          TEXT,
  recibida_en                        TIMESTAMPTZ NOT NULL,
  respondida_en                      TIMESTAMPTZ,
  CONSTRAINT pk_observacion_regulatoria PRIMARY KEY (id),
  CONSTRAINT ck_observacion_regulatoria_organismo CHECK (organismo IN ('ASFI', 'BCB', 'SIN', 'UIF')),
  CONSTRAINT ck_observacion_regulatoria_tipo CHECK (tipo IN ('INSTRUCCION', 'MULTA', 'OBSERVACION', 'REQUERIMIENTO')),
  CONSTRAINT ck_observacion_regulatoria_estado CHECK (estado IN ('EN_RESPUESTA', 'FIRME', 'RECIBIDA', 'RESPONDIDA', 'SUBSANADA'))
);

COMMENT ON TABLE cumplimiento.observacion_regulatoria IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.observacion_regulatoria.id IS 'PK';
COMMENT ON COLUMN cumplimiento.observacion_regulatoria.envio_regulatorio_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.observacion_regulatoria.responsable_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.observacion_regulatoria.organismo IS 'CK';
COMMENT ON COLUMN cumplimiento.observacion_regulatoria.tipo IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.observacion_regulatoria.numero_documento IS 'UQ';
COMMENT ON COLUMN cumplimiento.observacion_regulatoria.monto_multa IS 'NULL';
COMMENT ON COLUMN cumplimiento.observacion_regulatoria.plazo_respuesta IS 'IDX';
COMMENT ON COLUMN cumplimiento.observacion_regulatoria.estado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.observacion_regulatoria.respuesta IS 'NULL';
COMMENT ON COLUMN cumplimiento.observacion_regulatoria.respondida_en IS 'NULL';
