-- alerta_riesgo · módulo 06 — Transparencia y Reputación
-- clase de dominio: AlertaRiesgo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS transparencia.alerta_riesgo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  ambito                             VARCHAR(15) NOT NULL,
  ambito_id                          UUID NOT NULL,
  codigo                             VARCHAR(40) NOT NULL,
  severidad                          VARCHAR(10) NOT NULL,
  descripcion                        VARCHAR(300) NOT NULL,
  evidencia                          JSONB NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  detectada_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  cerrada_en                         TIMESTAMPTZ,
  CONSTRAINT pk_alerta_riesgo PRIMARY KEY (id),
  CONSTRAINT ck_alerta_riesgo_ambito CHECK (ambito IN ('GRUPO', 'ORGANIZADOR', 'USUARIO')),
  CONSTRAINT ck_alerta_riesgo_codigo CHECK (codigo IN ('CAIDA_ABRUPTA_SCORE', 'GRUPO_INVIABLE', 'MORA_CONCENTRADA', 'RETIRO_MASIVO')),
  CONSTRAINT ck_alerta_riesgo_severidad CHECK (severidad IN ('ALTA', 'BAJA', 'CRITICA', 'MEDIA')),
  CONSTRAINT ck_alerta_riesgo_estado CHECK (estado IN ('ABIERTA', 'CONFIRMADA', 'DESCARTADA', 'EN_REVISION'))
);

COMMENT ON TABLE transparencia.alerta_riesgo IS 'Módulo 06 — Transparencia y Reputación. Que nadie tenga que "creerle" al organizador';
COMMENT ON COLUMN transparencia.alerta_riesgo.id IS 'PK';
COMMENT ON COLUMN transparencia.alerta_riesgo.ambito IS 'CK';
COMMENT ON COLUMN transparencia.alerta_riesgo.ambito_id IS 'IDX';
COMMENT ON COLUMN transparencia.alerta_riesgo.codigo IS 'CK';
COMMENT ON COLUMN transparencia.alerta_riesgo.severidad IS 'CK';
COMMENT ON COLUMN transparencia.alerta_riesgo.estado IS 'CK, IDX';
COMMENT ON COLUMN transparencia.alerta_riesgo.cerrada_en IS 'NULL';
