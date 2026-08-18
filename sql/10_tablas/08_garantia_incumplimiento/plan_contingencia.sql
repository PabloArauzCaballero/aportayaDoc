-- plan_contingencia · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: PlanContingencia
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.plan_contingencia (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  acuerdo_grupo_id                   UUID,
  disparador                         VARCHAR(30) NOT NULL,
  tipo                               VARCHAR(30) NOT NULL,
  descripcion                        TEXT NOT NULL,
  impacto_estimado                   NUMERIC(14,2) DEFAULT 0 NOT NULL,
  requiere_acuerdo                   BOOLEAN DEFAULT FALSE NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  propuesto_en                       TIMESTAMPTZ NOT NULL,
  ejecutado_en                       TIMESTAMPTZ,
  CONSTRAINT pk_plan_contingencia PRIMARY KEY (id),
  CONSTRAINT ck_plan_contingencia_disparador CHECK (disparador IN ('ABANDONO_MASIVO', 'FONDO_AGOTADO', 'MORA_CRITICA', 'ORGANIZADOR_INHABILITADO')),
  CONSTRAINT ck_plan_contingencia_tipo CHECK (tipo IN ('DISOLUCION', 'EXTENSION_DE_PLAZO', 'PRORRATEO_ENTRE_ACTIVOS', 'REDUCCION_DE_BOLSA', 'REEMPLAZO')),
  CONSTRAINT ck_plan_contingencia_estado CHECK (estado IN ('APROBADO', 'DESCARTADO', 'EJECUTADO', 'PROPUESTO'))
);

COMMENT ON TABLE garantia.plan_contingencia IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.plan_contingencia.id IS 'PK';
COMMENT ON COLUMN garantia.plan_contingencia.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.plan_contingencia.acuerdo_grupo_id IS 'FK, NULL, M2';
COMMENT ON COLUMN garantia.plan_contingencia.disparador IS 'CK';
COMMENT ON COLUMN garantia.plan_contingencia.tipo IS 'CK';
COMMENT ON COLUMN garantia.plan_contingencia.estado IS 'CK';
COMMENT ON COLUMN garantia.plan_contingencia.ejecutado_en IS 'NULL';
