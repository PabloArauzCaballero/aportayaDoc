-- apelacion_sancion_org · módulo 07 — Organizador y Automatización
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS organizador.apelacion_sancion_org (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  sancion_organizador_id             UUID NOT NULL,
  argumento                          TEXT NOT NULL,
  evidencias                         JSONB NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  resuelta_por                       UUID,
  resolucion                         VARCHAR(400),
  presentada_en                      TIMESTAMPTZ NOT NULL,
  resuelta_en                        TIMESTAMPTZ,
  CONSTRAINT pk_apelacion_sancion_org PRIMARY KEY (id),
  CONSTRAINT ck_apelacion_sancion_org_estado CHECK (estado IN ('ACEPTADA', 'DESISTIDA', 'EN_REVISION', 'PRESENTADA', 'RECHAZADA'))
);

COMMENT ON TABLE organizador.apelacion_sancion_org IS 'Módulo 07 — Organizador y Automatización. Administrar es un rol, no un negocio: el organizador no cobra ni custodia';
COMMENT ON COLUMN organizador.apelacion_sancion_org.id IS 'PK';
COMMENT ON COLUMN organizador.apelacion_sancion_org.sancion_organizador_id IS 'FK, UQ';
COMMENT ON COLUMN organizador.apelacion_sancion_org.estado IS 'CK';
COMMENT ON COLUMN organizador.apelacion_sancion_org.resuelta_por IS 'FK, NULL';
COMMENT ON COLUMN organizador.apelacion_sancion_org.resolucion IS 'NULL';
COMMENT ON COLUMN organizador.apelacion_sancion_org.resuelta_en IS 'NULL';
