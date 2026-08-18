-- tarea_automatizada · módulo 07 — Organizador y Automatización
-- clase de dominio: TareaAutomatizada
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS organizador.tarea_automatizada (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  regla_id                           UUID NOT NULL,
  grupo_id                           UUID NOT NULL,
  tipo                               VARCHAR(40) NOT NULL,
  programada_para                    TIMESTAMPTZ NOT NULL,
  estado                             VARCHAR(25) NOT NULL,
  intentos                           SMALLINT DEFAULT 0 NOT NULL,
  clave_idempotencia                 VARCHAR(80) NOT NULL,
  CONSTRAINT pk_tarea_automatizada PRIMARY KEY (id),
  CONSTRAINT ck_tarea_automatizada_estado CHECK (estado IN ('CANCELADA', 'COMPLETADA', 'EN_EJECUCION', 'FALLIDA', 'PROGRAMADA', 'REQUIERE_APROBACION'))
);

COMMENT ON TABLE organizador.tarea_automatizada IS 'Módulo 07 — Organizador y Automatización. Administrar es un rol, no un negocio: el organizador no cobra ni custodia';
COMMENT ON COLUMN organizador.tarea_automatizada.id IS 'PK';
COMMENT ON COLUMN organizador.tarea_automatizada.regla_id IS 'FK, IDX';
COMMENT ON COLUMN organizador.tarea_automatizada.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN organizador.tarea_automatizada.programada_para IS 'IDX';
COMMENT ON COLUMN organizador.tarea_automatizada.estado IS 'CK, IDX';
