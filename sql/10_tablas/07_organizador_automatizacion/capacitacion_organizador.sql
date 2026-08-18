-- capacitacion_organizador · módulo 07 — Organizador y Automatización
-- clase de dominio: CapacitacionOrganizador
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS organizador.capacitacion_organizador (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  organizador_id                     UUID NOT NULL,
  modulo                             VARCHAR(80) NOT NULL,
  completada_en                      TIMESTAMPTZ NOT NULL,
  puntaje_evaluacion                 NUMERIC(5,2) NOT NULL,
  aprobada                           BOOLEAN DEFAULT FALSE NOT NULL,
  vigente_hasta                      DATE,
  CONSTRAINT pk_capacitacion_organizador PRIMARY KEY (id)
);

COMMENT ON TABLE organizador.capacitacion_organizador IS 'Módulo 07 — Organizador y Automatización. Administrar es un rol, no un negocio: el organizador no cobra ni custodia';
COMMENT ON COLUMN organizador.capacitacion_organizador.id IS 'PK';
COMMENT ON COLUMN organizador.capacitacion_organizador.organizador_id IS 'FK, IDX';
COMMENT ON COLUMN organizador.capacitacion_organizador.vigente_hasta IS 'NULL';
