-- snapshot_reputacion · módulo 06 — Transparencia y Reputación
-- clase de dominio: SnapshotReputacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS transparencia.snapshot_reputacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  puntaje                            NUMERIC(6,2) NOT NULL,
  nivel_confianza                    VARCHAR(20) NOT NULL,
  fotografia_factores                JSONB NOT NULL,
  motivo                             VARCHAR(25) NOT NULL,
  tomado_en                          TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_snapshot_reputacion PRIMARY KEY (id),
  CONSTRAINT ck_snapshot_reputacion_motivo CHECK (motivo IN ('AUDITORIA', 'CIERRE_DE_GRUPO', 'INGRESO_A_GRUPO', 'PERIODICO'))
);

COMMENT ON TABLE transparencia.snapshot_reputacion IS 'Módulo 06 — Transparencia y Reputación. Que nadie tenga que "creerle" al organizador';
COMMENT ON COLUMN transparencia.snapshot_reputacion.id IS 'PK';
COMMENT ON COLUMN transparencia.snapshot_reputacion.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN transparencia.snapshot_reputacion.motivo IS 'CK';
COMMENT ON COLUMN transparencia.snapshot_reputacion.tomado_en IS 'IDX';
