-- sancion · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: Sancion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.sancion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  registro_id                        UUID NOT NULL,
  usuario_id                         UUID NOT NULL,
  participante_id                    UUID,
  matriz_id                          UUID,
  acuerdo_grupo_id                   UUID,
  aplicada_por                       UUID,
  tipo                               VARCHAR(35) NOT NULL,
  descripcion                        VARCHAR(300) NOT NULL,
  monto_recargo                      NUMERIC(14,2),
  impacto_reputacion                 NUMERIC(6,2),
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  estado                             VARCHAR(20) NOT NULL,
  notificada_en                      TIMESTAMPTZ,
  firme_en                           TIMESTAMPTZ,
  CONSTRAINT pk_sancion PRIMARY KEY (id),
  CONSTRAINT ck_sancion_tipo CHECK (tipo IN ('ADVERTENCIA', 'AFECTACION_REPUTACION', 'EXPULSION_DEL_GRUPO', 'INHABILITACION_PLATAFORMA', 'PERDIDA_DE_PRIORIDAD_DE_TURNO', 'RECARGO_MONETARIO', 'RESTRICCION_NUEVOS_GRUPOS', 'RETENCION_DE_ENTREGA', 'SUSPENSION_DE_VOTO')),
  CONSTRAINT ck_sancion_estado CHECK (estado IN ('CUMPLIDA', 'EN_APELACION', 'EN_DESCARGO', 'FIRME', 'NOTIFICADA', 'PRESCRITA', 'PROPUESTA', 'REVOCADA'))
);

COMMENT ON TABLE garantia.sancion IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.sancion.id IS 'PK';
COMMENT ON COLUMN garantia.sancion.registro_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.sancion.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.sancion.participante_id IS 'FK, NULL';
COMMENT ON COLUMN garantia.sancion.matriz_id IS 'FK, NULL';
COMMENT ON COLUMN garantia.sancion.acuerdo_grupo_id IS 'FK, NULL, M2';
COMMENT ON COLUMN garantia.sancion.aplicada_por IS 'FK, NULL';
COMMENT ON COLUMN garantia.sancion.tipo IS 'CK, IDX';
COMMENT ON COLUMN garantia.sancion.monto_recargo IS 'NULL';
COMMENT ON COLUMN garantia.sancion.impacto_reputacion IS 'NULL';
COMMENT ON COLUMN garantia.sancion.vigente_hasta IS 'NULL';
COMMENT ON COLUMN garantia.sancion.estado IS 'CK, IDX';
COMMENT ON COLUMN garantia.sancion.notificada_en IS 'NULL';
COMMENT ON COLUMN garantia.sancion.firme_en IS 'NULL';
