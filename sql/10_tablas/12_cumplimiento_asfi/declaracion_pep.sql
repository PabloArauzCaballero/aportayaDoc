-- declaracion_pep · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: DeclaracionPep
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.declaracion_pep (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  verificada_por                     UUID,
  es_pep                             BOOLEAN DEFAULT FALSE NOT NULL,
  tipo_pep                           VARCHAR(25),
  cargo                              VARCHAR(120),
  institucion                        VARCHAR(120),
  pais                               CHAR(2),
  desde                              DATE,
  hasta                              DATE,
  evidencia_url                      VARCHAR(255),
  declarada_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_declaracion_pep PRIMARY KEY (id),
  CONSTRAINT ck_declaracion_pep_tipo_pep CHECK (tipo_pep IN ('ALLEGADO', 'EXTRANJERO', 'FAMILIAR', 'NACIONAL', 'ORG_INTERNACIONAL'))
);

COMMENT ON TABLE cumplimiento.declaracion_pep IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.declaracion_pep.id IS 'PK';
COMMENT ON COLUMN cumplimiento.declaracion_pep.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.declaracion_pep.verificada_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.declaracion_pep.es_pep IS 'IDX';
COMMENT ON COLUMN cumplimiento.declaracion_pep.tipo_pep IS 'CK, NULL';
COMMENT ON COLUMN cumplimiento.declaracion_pep.cargo IS 'NULL';
COMMENT ON COLUMN cumplimiento.declaracion_pep.institucion IS 'NULL';
COMMENT ON COLUMN cumplimiento.declaracion_pep.pais IS 'NULL';
COMMENT ON COLUMN cumplimiento.declaracion_pep.desde IS 'NULL';
COMMENT ON COLUMN cumplimiento.declaracion_pep.hasta IS 'NULL';
COMMENT ON COLUMN cumplimiento.declaracion_pep.evidencia_url IS 'NULL';
