-- caso_investigacion_lft · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: CasoInvestigacionLft
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.caso_investigacion_lft (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(20) NOT NULL,
  usuario_id                         UUID NOT NULL,
  analista_id                        UUID NOT NULL,
  revisado_por                       UUID,
  reporte_operacion_sospechosa_id    UUID,
  origen                             VARCHAR(25) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  prioridad                          VARCHAR(10) NOT NULL,
  resumen                            VARCHAR(500) NOT NULL,
  hallazgos                          TEXT,
  decision                           VARCHAR(25),
  abierto_en                         TIMESTAMPTZ DEFAULT now() NOT NULL,
  plazo_limite                       TIMESTAMPTZ NOT NULL,
  cerrado_en                         TIMESTAMPTZ,
  CONSTRAINT pk_caso_investigacion_lft PRIMARY KEY (id),
  CONSTRAINT ck_caso_investigacion_lft_origen CHECK (origen IN ('ALERTA', 'DENUNCIA', 'REQUERIMIENTO', 'REVISION_PERIODICA')),
  CONSTRAINT ck_caso_investigacion_lft_estado CHECK (estado IN ('ABIERTO', 'CERRADO', 'EN_ANALISIS', 'EN_REVISION')),
  CONSTRAINT ck_caso_investigacion_lft_prioridad CHECK (prioridad IN ('ALTA', 'BAJA', 'MEDIA', 'URGENTE')),
  CONSTRAINT ck_caso_investigacion_lft_decision CHECK (decision IN ('DESCARTAR', 'MONITOREO_REFORZADO', 'REPORTAR', 'TERMINAR_RELACION'))
);

COMMENT ON TABLE cumplimiento.caso_investigacion_lft IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.caso_investigacion_lft.id IS 'PK';
COMMENT ON COLUMN cumplimiento.caso_investigacion_lft.codigo IS 'UQ';
COMMENT ON COLUMN cumplimiento.caso_investigacion_lft.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.caso_investigacion_lft.analista_id IS 'FK';
COMMENT ON COLUMN cumplimiento.caso_investigacion_lft.revisado_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.caso_investigacion_lft.reporte_operacion_sospechosa_id IS 'FK, NULL, M9';
COMMENT ON COLUMN cumplimiento.caso_investigacion_lft.origen IS 'CK';
COMMENT ON COLUMN cumplimiento.caso_investigacion_lft.estado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.caso_investigacion_lft.prioridad IS 'CK';
COMMENT ON COLUMN cumplimiento.caso_investigacion_lft.hallazgos IS 'NULL';
COMMENT ON COLUMN cumplimiento.caso_investigacion_lft.decision IS 'CK, NULL';
COMMENT ON COLUMN cumplimiento.caso_investigacion_lft.plazo_limite IS 'IDX';
COMMENT ON COLUMN cumplimiento.caso_investigacion_lft.cerrado_en IS 'NULL';
