-- aceptacion_contrato · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: AceptacionContrato
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.aceptacion_contrato (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  contrato_adhesion_id               UUID NOT NULL,
  usuario_id                         UUID NOT NULL,
  dispositivo_id                     UUID,
  token_firma_id                     UUID,
  version_aceptada                   SMALLINT NOT NULL,
  ip                                 INET,
  hash_evidencia                     VARCHAR(64) NOT NULL,
  aceptado_en                        TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_aceptacion_contrato PRIMARY KEY (id)
);

COMMENT ON TABLE cumplimiento.aceptacion_contrato IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.aceptacion_contrato.id IS 'PK';
COMMENT ON COLUMN cumplimiento.aceptacion_contrato.contrato_adhesion_id IS 'FK, IDX';
COMMENT ON COLUMN cumplimiento.aceptacion_contrato.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.aceptacion_contrato.dispositivo_id IS 'FK, NULL, M1';
COMMENT ON COLUMN cumplimiento.aceptacion_contrato.token_firma_id IS 'FK, NULL, M1';
COMMENT ON COLUMN cumplimiento.aceptacion_contrato.ip IS 'NULL';
