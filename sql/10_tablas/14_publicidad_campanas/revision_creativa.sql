-- revision_creativa · módulo 14 — Publicidad y Campañas
-- clase de dominio: RevisionCreativa
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS publicidad.revision_creativa (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  pieza_creativa_id                  UUID NOT NULL,
  revisada_por                       UUID NOT NULL,
  decision                           VARCHAR(10) NOT NULL,
  motivo                             VARCHAR(300),
  revisada_en                        TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_revision_creativa PRIMARY KEY (id),
  CONSTRAINT ck_revision_creativa_decision CHECK (decision IN ('APROBADA', 'RECHAZADA'))
);

COMMENT ON TABLE publicidad.revision_creativa IS 'Módulo 14 — Publicidad y Campañas. Que un partner se anuncie dentro de la app sin inventar un segundo cobro';
COMMENT ON COLUMN publicidad.revision_creativa.id IS 'PK';
COMMENT ON COLUMN publicidad.revision_creativa.pieza_creativa_id IS 'FK, IDX';
COMMENT ON COLUMN publicidad.revision_creativa.revisada_por IS 'FK';
COMMENT ON COLUMN publicidad.revision_creativa.decision IS 'CK';
COMMENT ON COLUMN publicidad.revision_creativa.motivo IS 'NULL';
