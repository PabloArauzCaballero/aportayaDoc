-- documento_publicado · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: DocumentoPublicado
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.documento_publicado (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  tipo                               VARCHAR(25) NOT NULL,
  referencia_tipo                    VARCHAR(30),
  referencia_id                      UUID,
  publicado_por                      UUID,
  url_publica                        VARCHAR(255) NOT NULL,
  hash_documento                     VARCHAR(64) NOT NULL,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  CONSTRAINT pk_documento_publicado PRIMARY KEY (id),
  CONSTRAINT ck_documento_publicado_tipo CHECK (tipo IN ('CANAL_RECLAMOS', 'CONTRATO', 'HORARIOS', 'POLITICA_PRIVACIDAD', 'TARIFARIO'))
);

COMMENT ON TABLE cumplimiento.documento_publicado IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.documento_publicado.id IS 'PK';
COMMENT ON COLUMN cumplimiento.documento_publicado.tipo IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.documento_publicado.referencia_tipo IS 'NULL';
COMMENT ON COLUMN cumplimiento.documento_publicado.referencia_id IS 'NULL, polimorfica';
COMMENT ON COLUMN cumplimiento.documento_publicado.publicado_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.documento_publicado.vigente_hasta IS 'NULL';
