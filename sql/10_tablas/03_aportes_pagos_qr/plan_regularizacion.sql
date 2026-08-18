-- plan_regularizacion · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: PlanRegularizacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.plan_regularizacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  participante_id                    UUID NOT NULL,
  monto_total                        NUMERIC(14,2) DEFAULT 0 NOT NULL,
  num_cuotas                         SMALLINT NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  aprobado_por                       UUID NOT NULL,
  creado_en                          TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_plan_regularizacion PRIMARY KEY (id),
  CONSTRAINT ck_plan_regularizacion_estado CHECK (estado IN ('ANULADO', 'CUMPLIDO', 'INCUMPLIDO', 'VIGENTE'))
);

COMMENT ON TABLE aportes.plan_regularizacion IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.plan_regularizacion.id IS 'PK';
COMMENT ON COLUMN aportes.plan_regularizacion.participante_id IS 'FK, IDX';
COMMENT ON COLUMN aportes.plan_regularizacion.estado IS 'CK';
COMMENT ON COLUMN aportes.plan_regularizacion.aprobado_por IS 'FK';
