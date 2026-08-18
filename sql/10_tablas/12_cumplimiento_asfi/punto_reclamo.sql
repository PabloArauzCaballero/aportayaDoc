-- punto_reclamo · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: PuntoReclamo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.punto_reclamo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(20) NOT NULL,
  tipo                               VARCHAR(12) NOT NULL,
  descripcion                        VARCHAR(200) NOT NULL,
  horario                            VARCHAR(80) NOT NULL,
  responsable_id                     UUID,
  activo                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_punto_reclamo PRIMARY KEY (id),
  CONSTRAINT ck_punto_reclamo_tipo CHECK (tipo IN ('APP', 'CORREO', 'PRESENCIAL', 'TELEFONO', 'WEB'))
);

COMMENT ON TABLE cumplimiento.punto_reclamo IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.punto_reclamo.id IS 'PK';
COMMENT ON COLUMN cumplimiento.punto_reclamo.codigo IS 'UQ';
COMMENT ON COLUMN cumplimiento.punto_reclamo.tipo IS 'CK';
COMMENT ON COLUMN cumplimiento.punto_reclamo.responsable_id IS 'FK, NULL';
