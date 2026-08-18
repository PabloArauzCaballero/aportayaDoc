-- envio_regulatorio · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: EnvioRegulatorio
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.envio_regulatorio (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  reporte_regulatorio_id             UUID NOT NULL,
  enviado_por                        UUID,
  organismo                          VARCHAR(10) NOT NULL,
  canal                              VARCHAR(20) NOT NULL,
  fecha_envio                        TIMESTAMPTZ NOT NULL,
  numero_constancia                  VARCHAR(60),
  estado                             VARCHAR(15) NOT NULL,
  respuesta                          JSONB,
  reintentos                         SMALLINT DEFAULT 0 NOT NULL,
  CONSTRAINT pk_envio_regulatorio PRIMARY KEY (id),
  CONSTRAINT ck_envio_regulatorio_organismo CHECK (organismo IN ('ASFI', 'BCB', 'SIN', 'UIF')),
  CONSTRAINT ck_envio_regulatorio_canal CHECK (canal IN ('CORREO', 'MEDIO_FISICO', 'PORTAL_WEB', 'SERVICIO_WEB')),
  CONSTRAINT ck_envio_regulatorio_estado CHECK (estado IN ('ACEPTADO', 'ENVIADO', 'OBSERVADO', 'PENDIENTE', 'RECHAZADO'))
);

COMMENT ON TABLE cumplimiento.envio_regulatorio IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.envio_regulatorio.id IS 'PK';
COMMENT ON COLUMN cumplimiento.envio_regulatorio.reporte_regulatorio_id IS 'FK, IDX';
COMMENT ON COLUMN cumplimiento.envio_regulatorio.enviado_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.envio_regulatorio.organismo IS 'CK';
COMMENT ON COLUMN cumplimiento.envio_regulatorio.canal IS 'CK';
COMMENT ON COLUMN cumplimiento.envio_regulatorio.fecha_envio IS 'IDX';
COMMENT ON COLUMN cumplimiento.envio_regulatorio.numero_constancia IS 'UQ, NULL';
COMMENT ON COLUMN cumplimiento.envio_regulatorio.estado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.envio_regulatorio.respuesta IS 'NULL';
