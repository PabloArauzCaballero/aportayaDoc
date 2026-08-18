-- evento_riesgo_operativo · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: EventoRiesgoOperativo
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.evento_riesgo_operativo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(20) NOT NULL,
  incidente_operativo_id             UUID,
  registrado_por                     UUID NOT NULL,
  categoria_evento                   VARCHAR(35) NOT NULL,
  factor_riesgo                      VARCHAR(30) NOT NULL,
  reportado_central_riesgo_operativo BOOLEAN DEFAULT FALSE NOT NULL,
  linea_negocio                      VARCHAR(40) NOT NULL,
  descripcion                        TEXT NOT NULL,
  fecha_ocurrencia                   TIMESTAMPTZ NOT NULL,
  fecha_deteccion                    TIMESTAMPTZ NOT NULL,
  fecha_contabilizacion              TIMESTAMPTZ,
  perdida_bruta                      NUMERIC(16,2) DEFAULT 0 NOT NULL,
  recuperacion                       NUMERIC(16,2) DEFAULT 0 NOT NULL,
  perdida_neta                       NUMERIC(16,2) GENERATED ALWAYS AS (perdida_bruta - recuperacion) STORED,
  moneda                             CHAR(3) NOT NULL,
  causa_raiz                         TEXT,
  estado                             VARCHAR(15) NOT NULL,
  CONSTRAINT pk_evento_riesgo_operativo PRIMARY KEY (id),
  CONSTRAINT ck_evento_riesgo_operativo_categoria_evento CHECK (categoria_evento IN ('CLIENTES_PRODUCTOS_PRACTICAS', 'DANOS_ACTIVOS', 'FALLAS_SISTEMAS', 'FRAUDE_EXTERNO', 'FRAUDE_INTERNO', 'RELACIONES_LABORALES')),
  CONSTRAINT ck_evento_riesgo_operativo_factor_riesgo CHECK (factor_riesgo IN ('EVENTOS_EXTERNOS', 'INFRAESTRUCTURA', 'PERSONAS', 'PROCESOS_INTERNOS', 'TECNOLOGIA_INFORMACION')),
  CONSTRAINT ck_evento_riesgo_operativo_estado CHECK (estado IN ('CERRADO', 'EN_ANALISIS', 'EN_REMEDIACION', 'REGISTRADO'))
);

COMMENT ON TABLE cumplimiento.evento_riesgo_operativo IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. [append-only] Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.evento_riesgo_operativo.id IS 'PK';
COMMENT ON COLUMN cumplimiento.evento_riesgo_operativo.codigo IS 'UQ';
COMMENT ON COLUMN cumplimiento.evento_riesgo_operativo.incidente_operativo_id IS 'FK, NULL, M9';
COMMENT ON COLUMN cumplimiento.evento_riesgo_operativo.registrado_por IS 'FK';
COMMENT ON COLUMN cumplimiento.evento_riesgo_operativo.categoria_evento IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.evento_riesgo_operativo.factor_riesgo IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.evento_riesgo_operativo.reportado_central_riesgo_operativo IS 'IDX';
COMMENT ON COLUMN cumplimiento.evento_riesgo_operativo.fecha_ocurrencia IS 'IDX';
COMMENT ON COLUMN cumplimiento.evento_riesgo_operativo.fecha_contabilizacion IS 'NULL';
COMMENT ON COLUMN cumplimiento.evento_riesgo_operativo.perdida_neta IS 'GENERATED';
COMMENT ON COLUMN cumplimiento.evento_riesgo_operativo.causa_raiz IS 'NULL';
COMMENT ON COLUMN cumplimiento.evento_riesgo_operativo.estado IS 'CK, IDX';
