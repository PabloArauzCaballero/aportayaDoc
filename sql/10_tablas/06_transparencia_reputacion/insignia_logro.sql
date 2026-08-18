-- insignia_logro · módulo 06 — Transparencia y Reputación
-- clase de dominio: InsigniaLogro
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS transparencia.insignia_logro (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(40) NOT NULL,
  nombre                             VARCHAR(80) NOT NULL,
  descripcion                        VARCHAR(200) NOT NULL,
  criterio                           VARCHAR(300) NOT NULL,
  icono_url                          VARCHAR(255) NOT NULL,
  CONSTRAINT pk_insignia_logro PRIMARY KEY (id)
);

COMMENT ON TABLE transparencia.insignia_logro IS 'Módulo 06 — Transparencia y Reputación. Que nadie tenga que "creerle" al organizador';
COMMENT ON COLUMN transparencia.insignia_logro.id IS 'PK';
COMMENT ON COLUMN transparencia.insignia_logro.codigo IS 'UQ';
