-- verificacion_publica · módulo 06 — Transparencia y Reputación
-- clase de dominio: VerificacionPublica
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS transparencia.verificacion_publica (
  codigo                             VARCHAR(40) NOT NULL,
  tipo_documento                     VARCHAR(30) NOT NULL,
  referencia_id                      UUID NOT NULL,
  hash_esperado                      VARCHAR(64) NOT NULL,
  hash_recomputado                   VARCHAR(64),
  resultado                          VARCHAR(15),
  verificado_en                      TIMESTAMPTZ,
  consultas                          INTEGER NOT NULL,
  ultima_consulta_en                 TIMESTAMPTZ,
  CONSTRAINT pk_verificacion_publica PRIMARY KEY (codigo),
  CONSTRAINT ck_verificacion_publica_tipo_documento CHECK (tipo_documento IN ('CERTIFICADO_REPUTACION', 'CONSTANCIA_PAGO', 'ESTADO_GRUPO')),
  CONSTRAINT ck_verificacion_publica_resultado CHECK (resultado IN ('COINCIDE', 'NO_COINCIDE', 'SIN_DATOS'))
);

COMMENT ON TABLE transparencia.verificacion_publica IS 'Módulo 06 — Transparencia y Reputación. Que nadie tenga que "creerle" al organizador';
COMMENT ON COLUMN transparencia.verificacion_publica.codigo IS 'PK';
COMMENT ON COLUMN transparencia.verificacion_publica.tipo_documento IS 'CK';
COMMENT ON COLUMN transparencia.verificacion_publica.referencia_id IS 'IDX';
COMMENT ON COLUMN transparencia.verificacion_publica.hash_recomputado IS 'NULL';
COMMENT ON COLUMN transparencia.verificacion_publica.resultado IS 'CK, NULL';
COMMENT ON COLUMN transparencia.verificacion_publica.verificado_en IS 'NULL';
COMMENT ON COLUMN transparencia.verificacion_publica.ultima_consulta_en IS 'NULL';
