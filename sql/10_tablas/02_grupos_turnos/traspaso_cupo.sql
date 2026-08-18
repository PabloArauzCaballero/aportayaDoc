-- traspaso_cupo · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- clase de dominio: TraspasoCupo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.traspaso_cupo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cupo_id                            UUID NOT NULL,
  participante_origen_id             UUID NOT NULL,
  participante_destino_id            UUID NOT NULL,
  motivo                             VARCHAR(30) NOT NULL,
  deuda_transferida                  NUMERIC(14,2) NOT NULL,
  derecho_cobro_transferido          BOOLEAN DEFAULT FALSE NOT NULL,
  aprobado_por_acuerdo_id            UUID,
  fecha                              TIMESTAMPTZ NOT NULL,
  revertido_en                       TIMESTAMPTZ,
  CONSTRAINT pk_traspaso_cupo PRIMARY KEY (id),
  CONSTRAINT ck_traspaso_cupo_motivo CHECK (motivo IN ('REEMPLAZO_POR_MORA', 'RETIRO', 'VENTA'))
);

COMMENT ON TABLE grupos.traspaso_cupo IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.traspaso_cupo.id IS 'PK';
COMMENT ON COLUMN grupos.traspaso_cupo.cupo_id IS 'FK, IDX';
COMMENT ON COLUMN grupos.traspaso_cupo.participante_origen_id IS 'FK';
COMMENT ON COLUMN grupos.traspaso_cupo.participante_destino_id IS 'FK';
COMMENT ON COLUMN grupos.traspaso_cupo.motivo IS 'CK';
COMMENT ON COLUMN grupos.traspaso_cupo.aprobado_por_acuerdo_id IS 'FK, NULL';
COMMENT ON COLUMN grupos.traspaso_cupo.revertido_en IS 'NULL';
