-- evento_reputacion · módulo 06 — Transparencia y Reputación
-- clase de dominio: EventoReputacion
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS transparencia.evento_reputacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  grupo_id                           UUID,
  participante_id                    UUID,
  tipo                               VARCHAR(40) NOT NULL,
  referencia_tipo                    VARCHAR(30) NOT NULL,
  referencia_origen_id               UUID,
  impacto                            NUMERIC(6,2) NOT NULL,
  factor_afectado                    VARCHAR(40) NOT NULL,
  descripcion                        VARCHAR(200) NOT NULL,
  modelo_version                     VARCHAR(20) NOT NULL,
  es_reversible                      BOOLEAN DEFAULT FALSE NOT NULL,
  revertido_por_id                   UUID,
  ocurrido_en                        TIMESTAMPTZ NOT NULL,
  registrado_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_evento_reputacion PRIMARY KEY (id),
  CONSTRAINT ck_evento_reputacion_tipo CHECK (tipo IN ('ABANDONO_DE_GRUPO', 'APORTE_ANTICIPADO', 'APORTE_CON_RETRASO_GRAVE', 'APORTE_CON_RETRASO_LEVE', 'APORTE_NO_REALIZADO', 'APORTE_PUNTUAL', 'AVAL_EJECUTADO', 'COBERTURA_CONSUMIDA', 'CONFIRMACION_ENTREGA_A_TIEMPO', 'DEUDA_CASTIGADA', 'DEUDA_RECUPERADA', 'EXPULSION', 'FRAUDE_CONFIRMADO', 'GRUPO_COMPLETADO', 'KYC_COMPLETADO', 'RESENA_NEGATIVA', 'RESENA_POSITIVA', 'SANCION_APLICADA', 'SANCION_REVOCADA')),
  CONSTRAINT ck_evento_reputacion_referencia_tipo CHECK (referencia_tipo IN ('COBERTURA_INCUMPLIMIENTO', 'ENTREGA_FONDO', 'GRUPO', 'OBLIGACION_APORTE', 'PARTICIPANTE', 'REGISTRO_INCUMPLIMIENTO', 'RESENA_PARTICIPANTE', 'TRASPASO_CUPO'))
);

COMMENT ON TABLE transparencia.evento_reputacion IS 'Módulo 06 — Transparencia y Reputación. [append-only] Que nadie tenga que "creerle" al organizador';
COMMENT ON COLUMN transparencia.evento_reputacion.id IS 'PK';
COMMENT ON COLUMN transparencia.evento_reputacion.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN transparencia.evento_reputacion.grupo_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN transparencia.evento_reputacion.participante_id IS 'FK, NULL';
COMMENT ON COLUMN transparencia.evento_reputacion.tipo IS 'CK, IDX';
COMMENT ON COLUMN transparencia.evento_reputacion.referencia_tipo IS 'CK';
COMMENT ON COLUMN transparencia.evento_reputacion.referencia_origen_id IS 'NULL, polimorfica';
COMMENT ON COLUMN transparencia.evento_reputacion.revertido_por_id IS 'FK, NULL';
COMMENT ON COLUMN transparencia.evento_reputacion.ocurrido_en IS 'IDX';
