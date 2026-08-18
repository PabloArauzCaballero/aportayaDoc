-- componente_score · módulo 06 — Transparencia y Reputación
-- clase de dominio: ComponenteScore
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS transparencia.componente_score (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  puntaje_id                         UUID NOT NULL,
  codigo_factor                      VARCHAR(40) NOT NULL,
  valor_crudo                        NUMERIC(12,4) NOT NULL,
  valor_normalizado                  NUMERIC(5,4) NOT NULL,
  contribucion                       NUMERIC(6,2) NOT NULL,
  tendencia                          VARCHAR(10) NOT NULL,
  CONSTRAINT pk_componente_score PRIMARY KEY (id),
  CONSTRAINT ck_componente_score_tendencia CHECK (tendencia IN ('BAJA', 'ESTABLE', 'SUBE'))
);

COMMENT ON TABLE transparencia.componente_score IS 'Módulo 06 — Transparencia y Reputación. Que nadie tenga que "creerle" al organizador';
COMMENT ON COLUMN transparencia.componente_score.id IS 'PK';
COMMENT ON COLUMN transparencia.componente_score.puntaje_id IS 'FK, IDX';
COMMENT ON COLUMN transparencia.componente_score.codigo_factor IS 'UQ+puntaje_id';
COMMENT ON COLUMN transparencia.componente_score.tendencia IS 'CK';
