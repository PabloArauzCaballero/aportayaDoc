-- hallazgo_auditoria · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: HallazgoAuditoria
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.hallazgo_auditoria (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(20) NOT NULL,
  responsable_id                     UUID,
  origen                             VARCHAR(25) NOT NULL,
  descripcion                        TEXT NOT NULL,
  severidad                          VARCHAR(10) NOT NULL,
  proceso                            VARCHAR(60) NOT NULL,
  fecha_identificacion               DATE NOT NULL,
  plazo_regularizacion               DATE NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  CONSTRAINT pk_hallazgo_auditoria PRIMARY KEY (id),
  CONSTRAINT ck_hallazgo_auditoria_origen CHECK (origen IN ('AUDITORIA_EXTERNA', 'AUDITORIA_INTERNA', 'AUTOEVALUACION', 'REGULADOR')),
  CONSTRAINT ck_hallazgo_auditoria_severidad CHECK (severidad IN ('ALTA', 'BAJA', 'CRITICA', 'MEDIA')),
  CONSTRAINT ck_hallazgo_auditoria_estado CHECK (estado IN ('ABIERTO', 'ACEPTADO_RIESGO', 'EN_REMEDIACION', 'SUBSANADO', 'VENCIDO'))
);

COMMENT ON TABLE cumplimiento.hallazgo_auditoria IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.hallazgo_auditoria.id IS 'PK';
COMMENT ON COLUMN cumplimiento.hallazgo_auditoria.codigo IS 'UQ';
COMMENT ON COLUMN cumplimiento.hallazgo_auditoria.responsable_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.hallazgo_auditoria.origen IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.hallazgo_auditoria.severidad IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.hallazgo_auditoria.plazo_regularizacion IS 'IDX';
COMMENT ON COLUMN cumplimiento.hallazgo_auditoria.estado IS 'CK, IDX';
