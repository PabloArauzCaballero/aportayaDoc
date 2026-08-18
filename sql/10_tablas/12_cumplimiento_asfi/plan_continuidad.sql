-- plan_continuidad · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: PlanContinuidad
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.plan_continuidad (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  politica_interna_id                UUID,
  responsable_id                     UUID,
  proceso_critico                    VARCHAR(80) NOT NULL,
  rto_minutos                        INTEGER NOT NULL,
  rpo_minutos                        INTEGER NOT NULL,
  estrategia                         VARCHAR(300) NOT NULL,
  periodicidad_prueba_meses          SMALLINT NOT NULL,
  vigente_desde                      DATE NOT NULL,
  proxima_prueba                     DATE NOT NULL,
  CONSTRAINT pk_plan_continuidad PRIMARY KEY (id)
);

COMMENT ON TABLE cumplimiento.plan_continuidad IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.plan_continuidad.id IS 'PK';
COMMENT ON COLUMN cumplimiento.plan_continuidad.politica_interna_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.plan_continuidad.responsable_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.plan_continuidad.proceso_critico IS 'UQ';
COMMENT ON COLUMN cumplimiento.plan_continuidad.proxima_prueba IS 'IDX';
