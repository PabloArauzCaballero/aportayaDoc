-- resena_participante · módulo 06 — Transparencia y Reputación
-- clase de dominio: ResenaParticipante
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS transparencia.resena_participante (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  autor_participante_id              UUID NOT NULL,
  evaluado_usuario_id                UUID NOT NULL,
  calificacion                       SMALLINT NOT NULL,
  comentario                         VARCHAR(500),
  dimension                          VARCHAR(20) NOT NULL,
  estado_moderacion                  VARCHAR(15) NOT NULL,
  moderada_por                       UUID,
  creada_en                          TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_resena_participante PRIMARY KEY (id),
  CONSTRAINT ck_resena_participante_calificacion CHECK (calificacion BETWEEN 1 AND 5),
  CONSTRAINT ck_resena_participante_dimension CHECK (dimension IN ('COMUNICACION', 'ORGANIZACION', 'PUNTUALIDAD')),
  CONSTRAINT ck_resena_participante_estado_moderacion CHECK (estado_moderacion IN ('OCULTA', 'PENDIENTE', 'PUBLICADA', 'RECHAZADA'))
);

COMMENT ON TABLE transparencia.resena_participante IS 'Módulo 06 — Transparencia y Reputación. Que nadie tenga que "creerle" al organizador';
COMMENT ON COLUMN transparencia.resena_participante.id IS 'PK';
COMMENT ON COLUMN transparencia.resena_participante.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN transparencia.resena_participante.autor_participante_id IS 'FK';
COMMENT ON COLUMN transparencia.resena_participante.evaluado_usuario_id IS 'FK, IDX';
COMMENT ON COLUMN transparencia.resena_participante.calificacion IS 'CK: 1..5';
COMMENT ON COLUMN transparencia.resena_participante.comentario IS 'NULL';
COMMENT ON COLUMN transparencia.resena_participante.dimension IS 'CK';
COMMENT ON COLUMN transparencia.resena_participante.estado_moderacion IS 'CK';
COMMENT ON COLUMN transparencia.resena_participante.moderada_por IS 'FK, NULL';
