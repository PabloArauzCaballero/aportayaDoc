-- plan_accion_riesgo · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: PlanAccionRiesgo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.plan_accion_riesgo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  evento_riesgo_id                   UUID,
  hallazgo_id                        UUID,
  responsable_id                     UUID NOT NULL,
  descripcion                        VARCHAR(500) NOT NULL,
  fecha_compromiso                   DATE NOT NULL,
  fecha_cierre                       DATE,
  avance_porcentaje                  NUMERIC(5,2) DEFAULT 0 NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  evidencia_url                      VARCHAR(255),
  CONSTRAINT pk_plan_accion_riesgo PRIMARY KEY (id),
  CONSTRAINT ck_plan_accion_riesgo_estado CHECK (estado IN ('CANCELADO', 'CUMPLIDO', 'EN_CURSO', 'PENDIENTE', 'VENCIDO'))
);

COMMENT ON TABLE cumplimiento.plan_accion_riesgo IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.plan_accion_riesgo.id IS 'PK';
COMMENT ON COLUMN cumplimiento.plan_accion_riesgo.evento_riesgo_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.plan_accion_riesgo.hallazgo_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.plan_accion_riesgo.responsable_id IS 'FK';
COMMENT ON COLUMN cumplimiento.plan_accion_riesgo.fecha_compromiso IS 'IDX';
COMMENT ON COLUMN cumplimiento.plan_accion_riesgo.fecha_cierre IS 'NULL';
COMMENT ON COLUMN cumplimiento.plan_accion_riesgo.estado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.plan_accion_riesgo.evidencia_url IS 'NULL';
