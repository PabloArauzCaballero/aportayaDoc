-- definicion_indicador · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: DefinicionIndicador
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.definicion_indicador (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(40) NOT NULL,
  version                            VARCHAR(20) NOT NULL,
  familia                            VARCHAR(20) NOT NULL,
  dueno_familia                      VARCHAR(80) NOT NULL,
  sentido_meta                       VARCHAR(20) NOT NULL,
  formula                            VARCHAR(400) NOT NULL,
  fuente                             VARCHAR(300) NOT NULL,
  minimo_casos                       SMALLINT NOT NULL,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  CONSTRAINT pk_definicion_indicador PRIMARY KEY (id),
  CONSTRAINT ck_definicion_indicador_familia CHECK (familia IN ('CUMPLIMIENTO', 'FINANZAS', 'NEGOCIO', 'OPERACION', 'RIESGO')),
  CONSTRAINT ck_definicion_indicador_sentido_meta CHECK (sentido_meta IN ('MAYOR_ES_MEJOR', 'MENOR_ES_MEJOR'))
);

COMMENT ON TABLE auditoria.definicion_indicador IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.definicion_indicador.id IS 'PK';
COMMENT ON COLUMN auditoria.definicion_indicador.codigo IS 'UQ+version';
COMMENT ON COLUMN auditoria.definicion_indicador.familia IS 'CK';
COMMENT ON COLUMN auditoria.definicion_indicador.sentido_meta IS 'CK';
COMMENT ON COLUMN auditoria.definicion_indicador.vigente_hasta IS 'NULL';
