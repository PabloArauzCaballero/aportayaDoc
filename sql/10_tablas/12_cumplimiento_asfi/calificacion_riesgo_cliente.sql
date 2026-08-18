-- calificacion_riesgo_cliente · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: CalificacionRiesgoCliente
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.calificacion_riesgo_cliente (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  matriz_riesgo_id                   UUID,
  calificado_por                     UUID,
  nivel                              VARCHAR(6) NOT NULL,
  puntaje_total                      NUMERIC(6,2) NOT NULL,
  nivel_dd_requerido                 VARCHAR(15) NOT NULL,
  periodicidad_revision_meses        SMALLINT NOT NULL,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  proxima_revision                   DATE NOT NULL,
  es_automatica                      BOOLEAN DEFAULT FALSE NOT NULL,
  motivo_cambio                      VARCHAR(300),
  CONSTRAINT pk_calificacion_riesgo_cliente PRIMARY KEY (id),
  CONSTRAINT ck_calificacion_riesgo_cliente_nivel CHECK (nivel IN ('ALTO', 'BAJO', 'MEDIO')),
  CONSTRAINT ck_calificacion_riesgo_cliente_nivel_dd_requerido CHECK (nivel_dd_requerido IN ('AMPLIADA', 'CONTINUA', 'ESTANDAR', 'REFORZADA', 'SIMPLIFICADA'))
);

COMMENT ON TABLE cumplimiento.calificacion_riesgo_cliente IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.calificacion_riesgo_cliente.id IS 'PK';
COMMENT ON COLUMN cumplimiento.calificacion_riesgo_cliente.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.calificacion_riesgo_cliente.matriz_riesgo_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.calificacion_riesgo_cliente.calificado_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.calificacion_riesgo_cliente.nivel IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.calificacion_riesgo_cliente.nivel_dd_requerido IS 'CK';
COMMENT ON COLUMN cumplimiento.calificacion_riesgo_cliente.vigente_hasta IS 'NULL';
COMMENT ON COLUMN cumplimiento.calificacion_riesgo_cliente.proxima_revision IS 'IDX';
COMMENT ON COLUMN cumplimiento.calificacion_riesgo_cliente.motivo_cambio IS 'NULL';
