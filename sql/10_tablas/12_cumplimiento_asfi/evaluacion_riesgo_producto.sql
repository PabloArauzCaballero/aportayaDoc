-- evaluacion_riesgo_producto · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: EvaluacionRiesgoProducto
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.evaluacion_riesgo_producto (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  producto                           VARCHAR(60) NOT NULL,
  version                            SMALLINT DEFAULT 0 NOT NULL,
  aprobada_por                       UUID,
  riesgos_identificados              JSONB NOT NULL,
  nivel_riesgo_lft                   VARCHAR(6) NOT NULL,
  controles_definidos                JSONB NOT NULL,
  requiere_no_objecion               BOOLEAN DEFAULT FALSE NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  fecha_aprobacion                   DATE,
  CONSTRAINT pk_evaluacion_riesgo_producto PRIMARY KEY (id),
  CONSTRAINT ck_evaluacion_riesgo_producto_nivel_riesgo_lft CHECK (nivel_riesgo_lft IN ('ALTO', 'BAJO', 'MEDIO')),
  CONSTRAINT ck_evaluacion_riesgo_producto_estado CHECK (estado IN ('APROBADA', 'BORRADOR', 'EN_EVALUACION', 'RECHAZADA'))
);

COMMENT ON TABLE cumplimiento.evaluacion_riesgo_producto IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.evaluacion_riesgo_producto.id IS 'PK';
COMMENT ON COLUMN cumplimiento.evaluacion_riesgo_producto.producto IS 'UQ+version';
COMMENT ON COLUMN cumplimiento.evaluacion_riesgo_producto.aprobada_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.evaluacion_riesgo_producto.nivel_riesgo_lft IS 'CK';
COMMENT ON COLUMN cumplimiento.evaluacion_riesgo_producto.estado IS 'CK';
COMMENT ON COLUMN cumplimiento.evaluacion_riesgo_producto.fecha_aprobacion IS 'NULL';
