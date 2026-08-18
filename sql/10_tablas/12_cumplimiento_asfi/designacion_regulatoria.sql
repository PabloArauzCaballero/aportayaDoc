-- designacion_regulatoria · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: DesignacionRegulatoria
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.designacion_regulatoria (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  acta_comite_id                     UUID,
  cargo                              VARCHAR(40) NOT NULL,
  tipo                               VARCHAR(10) NOT NULL,
  fecha_designacion                  DATE NOT NULL,
  organismo_comunicado               VARCHAR(10),
  comunicada_al_organismo_en         DATE,
  fecha_baja                         DATE,
  activo                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_designacion_regulatoria PRIMARY KEY (id),
  CONSTRAINT ck_designacion_regulatoria_cargo CHECK (cargo IN ('AUDITOR_INTERNO', 'OFICIAL_CUMPLIMIENTO', 'RESPONSABLE_PUNTO_RECLAMO', 'RESPONSABLE_RIESGOS', 'RESPONSABLE_SEGURIDAD_INFORMACION')),
  CONSTRAINT ck_designacion_regulatoria_tipo CHECK (tipo IN ('SUPLENTE', 'TITULAR'))
);

COMMENT ON TABLE cumplimiento.designacion_regulatoria IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.designacion_regulatoria.id IS 'PK';
COMMENT ON COLUMN cumplimiento.designacion_regulatoria.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.designacion_regulatoria.acta_comite_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.designacion_regulatoria.cargo IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.designacion_regulatoria.tipo IS 'CK';
COMMENT ON COLUMN cumplimiento.designacion_regulatoria.organismo_comunicado IS 'NULL';
COMMENT ON COLUMN cumplimiento.designacion_regulatoria.comunicada_al_organismo_en IS 'NULL';
COMMENT ON COLUMN cumplimiento.designacion_regulatoria.fecha_baja IS 'NULL';
COMMENT ON COLUMN cumplimiento.designacion_regulatoria.activo IS 'IDX';
