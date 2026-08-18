-- alerta_monitoreo_lft · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: AlertaMonitoreoLft
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.alerta_monitoreo_lft (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  regla_monitoreo_id                 UUID NOT NULL,
  usuario_id                         UUID NOT NULL,
  cuenta_billetera_id                UUID,
  transaccion_id                     UUID,
  caso_id                            UUID,
  asignada_a                         UUID,
  monto_involucrado                  NUMERIC(16,2) DEFAULT 0 NOT NULL,
  detalle                            JSONB NOT NULL,
  severidad                          VARCHAR(10) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  conclusion                         VARCHAR(500),
  detectada_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  cerrada_en                         TIMESTAMPTZ,
  CONSTRAINT pk_alerta_monitoreo_lft PRIMARY KEY (id),
  CONSTRAINT ck_alerta_monitoreo_lft_severidad CHECK (severidad IN ('ALTA', 'BAJA', 'CRITICA', 'MEDIA')),
  CONSTRAINT ck_alerta_monitoreo_lft_estado CHECK (estado IN ('ABIERTA', 'DESCARTADA', 'EN_ANALISIS', 'ESCALADA'))
);

COMMENT ON TABLE cumplimiento.alerta_monitoreo_lft IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.alerta_monitoreo_lft.id IS 'PK';
COMMENT ON COLUMN cumplimiento.alerta_monitoreo_lft.regla_monitoreo_id IS 'FK, IDX';
COMMENT ON COLUMN cumplimiento.alerta_monitoreo_lft.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.alerta_monitoreo_lft.cuenta_billetera_id IS 'FK, NULL, M10';
COMMENT ON COLUMN cumplimiento.alerta_monitoreo_lft.transaccion_id IS 'FK, NULL, M10';
COMMENT ON COLUMN cumplimiento.alerta_monitoreo_lft.caso_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.alerta_monitoreo_lft.asignada_a IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.alerta_monitoreo_lft.severidad IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.alerta_monitoreo_lft.estado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.alerta_monitoreo_lft.conclusion IS 'NULL';
COMMENT ON COLUMN cumplimiento.alerta_monitoreo_lft.detectada_en IS 'IDX';
COMMENT ON COLUMN cumplimiento.alerta_monitoreo_lft.cerrada_en IS 'NULL';
