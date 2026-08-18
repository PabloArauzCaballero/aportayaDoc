-- acta_comite · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: ActaComite
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.acta_comite (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  comite_gobierno_id                 UUID NOT NULL,
  elaborada_por                      UUID,
  numero                             VARCHAR(20) NOT NULL,
  fecha                              DATE NOT NULL,
  asistentes                         JSONB NOT NULL,
  cumple_quorum                      BOOLEAN DEFAULT FALSE NOT NULL,
  temas_tratados                     JSONB NOT NULL,
  decisiones                         JSONB NOT NULL,
  url_documento                      VARCHAR(255) NOT NULL,
  hash_documento                     VARCHAR(64) NOT NULL,
  CONSTRAINT pk_acta_comite PRIMARY KEY (id)
);

COMMENT ON TABLE cumplimiento.acta_comite IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. [append-only] Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.acta_comite.id IS 'PK';
COMMENT ON COLUMN cumplimiento.acta_comite.comite_gobierno_id IS 'FK, IDX';
COMMENT ON COLUMN cumplimiento.acta_comite.elaborada_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.acta_comite.numero IS 'UQ+comite_gobierno_id';
COMMENT ON COLUMN cumplimiento.acta_comite.fecha IS 'IDX';
