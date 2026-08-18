-- regla_monitoreo_lft · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: ReglaMonitoreoLft
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.regla_monitoreo_lft (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(40) NOT NULL,
  tipologia                          VARCHAR(80) NOT NULL,
  descripcion                        VARCHAR(300) NOT NULL,
  expresion                          JSONB NOT NULL,
  ventana_evaluacion                 VARCHAR(20) NOT NULL,
  umbral_monto                       NUMERIC(16,2),
  umbral_cantidad                    INTEGER,
  severidad                          VARCHAR(10) NOT NULL,
  accion_automatica                  VARCHAR(30) NOT NULL,
  fuente_normativa                   VARCHAR(120) NOT NULL,
  activa                             BOOLEAN DEFAULT FALSE NOT NULL,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  aprobada_por                       UUID,
  CONSTRAINT pk_regla_monitoreo_lft PRIMARY KEY (id),
  CONSTRAINT ck_regla_monitoreo_lft_severidad CHECK (severidad IN ('ALTA', 'BAJA', 'CRITICA', 'MEDIA')),
  CONSTRAINT ck_regla_monitoreo_lft_accion_automatica CHECK (accion_automatica IN ('BLOQUEAR_CUENTA', 'RETENER_OPERACION', 'SOLO_ALERTAR'))
);

COMMENT ON TABLE cumplimiento.regla_monitoreo_lft IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.regla_monitoreo_lft.id IS 'PK';
COMMENT ON COLUMN cumplimiento.regla_monitoreo_lft.codigo IS 'UQ';
COMMENT ON COLUMN cumplimiento.regla_monitoreo_lft.umbral_monto IS 'NULL';
COMMENT ON COLUMN cumplimiento.regla_monitoreo_lft.umbral_cantidad IS 'NULL';
COMMENT ON COLUMN cumplimiento.regla_monitoreo_lft.severidad IS 'CK';
COMMENT ON COLUMN cumplimiento.regla_monitoreo_lft.accion_automatica IS 'CK';
COMMENT ON COLUMN cumplimiento.regla_monitoreo_lft.activa IS 'IDX';
COMMENT ON COLUMN cumplimiento.regla_monitoreo_lft.aprobada_por IS 'FK, NULL';
