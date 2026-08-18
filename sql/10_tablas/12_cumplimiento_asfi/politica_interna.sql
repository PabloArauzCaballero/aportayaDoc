-- politica_interna · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: PoliticaInterna
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.politica_interna (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  acta_comite_id                     UUID,
  responsable_id                     UUID,
  codigo                             VARCHAR(30) NOT NULL,
  tipo                               VARCHAR(15) NOT NULL,
  materia                            VARCHAR(30) NOT NULL,
  version                            SMALLINT DEFAULT 0 NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  url_documento                      VARCHAR(255) NOT NULL,
  hash_documento                     VARCHAR(64) NOT NULL,
  aprobada_por_directorio            BOOLEAN DEFAULT FALSE NOT NULL,
  vigente_desde                      DATE NOT NULL,
  proxima_revision                   DATE NOT NULL,
  CONSTRAINT pk_politica_interna PRIMARY KEY (id),
  CONSTRAINT ck_politica_interna_tipo CHECK (tipo IN ('MANUAL', 'METODOLOGIA', 'PLAN', 'POLITICA', 'PROCEDIMIENTO')),
  CONSTRAINT ck_politica_interna_materia CHECK (materia IN ('CONSUMIDOR', 'CONTINUIDAD', 'LGI_FT', 'RIESGO_OPERATIVO', 'SEGURIDAD_INFORMACION', 'TERCERIZACION')),
  CONSTRAINT ck_politica_interna_estado CHECK (estado IN ('BORRADOR', 'DEROGADA', 'EN_APROBACION', 'SUSTITUIDA', 'VIGENTE'))
);

COMMENT ON TABLE cumplimiento.politica_interna IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.politica_interna.id IS 'PK';
COMMENT ON COLUMN cumplimiento.politica_interna.acta_comite_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.politica_interna.responsable_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.politica_interna.codigo IS 'UQ+version';
COMMENT ON COLUMN cumplimiento.politica_interna.tipo IS 'CK';
COMMENT ON COLUMN cumplimiento.politica_interna.materia IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.politica_interna.estado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.politica_interna.proxima_revision IS 'IDX';
