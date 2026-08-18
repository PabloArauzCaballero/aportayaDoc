-- regla_impacto_evento · módulo 06 — Transparencia y Reputación
-- clase de dominio: ReglaImpactoEvento
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS transparencia.regla_impacto_evento (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  modelo_id                          UUID NOT NULL,
  tipo_evento                        VARCHAR(40) NOT NULL,
  codigo_factor                      VARCHAR(40) NOT NULL,
  impacto_base                       NUMERIC(6,2) DEFAULT 0 NOT NULL,
  multiplicador_por_reincidencia     NUMERIC(4,2) NOT NULL,
  impacto_maximo                     NUMERIC(6,2) DEFAULT 0 NOT NULL,
  requiere_confirmacion              BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_regla_impacto_evento PRIMARY KEY (id)
);

COMMENT ON TABLE transparencia.regla_impacto_evento IS 'Módulo 06 — Transparencia y Reputación. Que nadie tenga que "creerle" al organizador';
COMMENT ON COLUMN transparencia.regla_impacto_evento.id IS 'PK';
COMMENT ON COLUMN transparencia.regla_impacto_evento.modelo_id IS 'FK, IDX';
COMMENT ON COLUMN transparencia.regla_impacto_evento.tipo_evento IS 'UQ+modelo_id';
