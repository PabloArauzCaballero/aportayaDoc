-- desvio_perfil · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: DesvioPerfil
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.desvio_perfil (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  perfil_transaccional_id            UUID NOT NULL,
  alerta_monitoreo_id                UUID,
  periodo                            CHAR(7) NOT NULL,
  monto_observado                    NUMERIC(16,2) DEFAULT 0 NOT NULL,
  monto_esperado                     NUMERIC(16,2) DEFAULT 0 NOT NULL,
  desvio_porcentual                  NUMERIC(8,2) NOT NULL,
  severidad                          VARCHAR(10) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  justificacion                      VARCHAR(500),
  detectado_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_desvio_perfil PRIMARY KEY (id),
  CONSTRAINT ck_desvio_perfil_severidad CHECK (severidad IN ('ALTA', 'BAJA', 'CRITICA', 'MEDIA')),
  CONSTRAINT ck_desvio_perfil_estado CHECK (estado IN ('DETECTADO', 'EN_ANALISIS', 'ESCALADO', 'JUSTIFICADO'))
);

COMMENT ON TABLE cumplimiento.desvio_perfil IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.desvio_perfil.id IS 'PK';
COMMENT ON COLUMN cumplimiento.desvio_perfil.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.desvio_perfil.perfil_transaccional_id IS 'FK';
COMMENT ON COLUMN cumplimiento.desvio_perfil.alerta_monitoreo_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.desvio_perfil.periodo IS 'UQ+usuario_id';
COMMENT ON COLUMN cumplimiento.desvio_perfil.desvio_porcentual IS 'IDX';
COMMENT ON COLUMN cumplimiento.desvio_perfil.severidad IS 'CK';
COMMENT ON COLUMN cumplimiento.desvio_perfil.estado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.desvio_perfil.justificacion IS 'NULL';
