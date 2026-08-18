-- puntaje_reputacion · módulo 06 — Transparencia y Reputación
-- clase de dominio: PuntajeReputacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS transparencia.puntaje_reputacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  modelo_id                          UUID NOT NULL,
  puntaje                            NUMERIC(6,2) NOT NULL,
  nivel_confianza                    VARCHAR(20) NOT NULL,
  indice_puntualidad                 NUMERIC(5,2) NOT NULL,
  tasa_incumplimiento                NUMERIC(5,2) NOT NULL,
  monto_total_aportado               NUMERIC(16,2) DEFAULT 0 NOT NULL,
  grupos_completados                 SMALLINT NOT NULL,
  grupos_abandonados                 SMALLINT NOT NULL,
  incumplimientos_abiertos           SMALLINT NOT NULL,
  antiguedad_meses                   SMALLINT NOT NULL,
  eventos_considerados               INTEGER NOT NULL,
  modelo_version                     VARCHAR(20) NOT NULL,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  calculado_en                       TIMESTAMPTZ NOT NULL,
  proximo_recalculo_en               TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_puntaje_reputacion PRIMARY KEY (id),
  CONSTRAINT ck_puntaje_reputacion_nivel_confianza CHECK (nivel_confianza IN ('BASICO', 'CONFIABLE', 'EN_OBSERVACION', 'MUY_CONFIABLE', 'REFERENTE', 'RESTRINGIDO', 'SIN_HISTORIAL'))
);

COMMENT ON TABLE transparencia.puntaje_reputacion IS 'Módulo 06 — Transparencia y Reputación. Que nadie tenga que "creerle" al organizador';
COMMENT ON COLUMN transparencia.puntaje_reputacion.id IS 'PK';
COMMENT ON COLUMN transparencia.puntaje_reputacion.usuario_id IS 'FK, UQ';
COMMENT ON COLUMN transparencia.puntaje_reputacion.modelo_id IS 'FK';
COMMENT ON COLUMN transparencia.puntaje_reputacion.puntaje IS 'IDX';
COMMENT ON COLUMN transparencia.puntaje_reputacion.nivel_confianza IS 'CK, IDX';
COMMENT ON COLUMN transparencia.puntaje_reputacion.vigente_hasta IS 'NULL';
COMMENT ON COLUMN transparencia.puntaje_reputacion.proximo_recalculo_en IS 'IDX';
