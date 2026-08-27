-- evaluacion_desempeno · módulo 07 — Organizador y Automatización
-- clase de dominio: EvaluacionDesempeno
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS organizador.evaluacion_desempeno (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  organizador_id                     UUID NOT NULL,
  periodo_evaluado                   VARCHAR(10) NOT NULL,
  indice_morosidad_cartera           NUMERIC(5,2) NOT NULL,
  tasa_finalizacion_grupos           NUMERIC(5,2) NOT NULL,
  satisfaccion_participantes         NUMERIC(3,2) NOT NULL,
  tiempo_respuesta_promedio_horas    NUMERIC(6,2) NOT NULL,
  incidencias_abiertas               SMALLINT NOT NULL,
  coberturas_consumidas              SMALLINT NOT NULL,
  puntaje_global                     NUMERIC(5,2) NOT NULL,
  nivel_sugerido                     VARCHAR(15) NOT NULL,
  accion_recomendada                 VARCHAR(120) NOT NULL,
  evaluado_en                        TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_evaluacion_desempeno PRIMARY KEY (id),
  CONSTRAINT ck_evaluacion_desempeno_nivel_sugerido CHECK (nivel_sugerido IN ('APRENDIZ', 'ESTANDAR', 'MAESTRO', 'SENIOR'))
);

COMMENT ON TABLE organizador.evaluacion_desempeno IS 'Módulo 07 — Organizador y Automatización. Administrar es un rol, no un negocio: el organizador no cobra ni custodia';
COMMENT ON COLUMN organizador.evaluacion_desempeno.id IS 'PK';
COMMENT ON COLUMN organizador.evaluacion_desempeno.organizador_id IS 'FK, IDX';
COMMENT ON COLUMN organizador.evaluacion_desempeno.nivel_sugerido IS 'CK';
