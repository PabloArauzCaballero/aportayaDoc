-- registro_sellado · módulo 06 — Transparencia y Reputación
-- clase de dominio: RegistroSellado
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS transparencia.registro_sellado (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  bloque_id                          UUID NOT NULL,
  tipo_entidad                       VARCHAR(25) NOT NULL,
  entidad_id                         UUID NOT NULL,
  hash_contenido                     VARCHAR(64) NOT NULL,
  resumen_publico                    JSONB NOT NULL,
  ocurrido_en                        TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_registro_sellado PRIMARY KEY (id),
  CONSTRAINT ck_registro_sellado_tipo_entidad CHECK (tipo_entidad IN ('ACUERDO', 'COBERTURA', 'ENTREGA', 'PAGO', 'SANCION'))
);

COMMENT ON TABLE transparencia.registro_sellado IS 'Módulo 06 — Transparencia y Reputación. [append-only] Que nadie tenga que "creerle" al organizador';
COMMENT ON COLUMN transparencia.registro_sellado.id IS 'PK';
COMMENT ON COLUMN transparencia.registro_sellado.bloque_id IS 'FK, IDX';
COMMENT ON COLUMN transparencia.registro_sellado.tipo_entidad IS 'CK';
COMMENT ON COLUMN transparencia.registro_sellado.entidad_id IS 'IDX, polimorfica';
