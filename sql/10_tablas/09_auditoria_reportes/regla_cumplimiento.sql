-- regla_cumplimiento · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: ReglaCumplimiento
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.regla_cumplimiento (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(40) NOT NULL,
  descripcion                        VARCHAR(300) NOT NULL,
  categoria                          VARCHAR(25) NOT NULL,
  expresion                          VARCHAR(400) NOT NULL,
  umbral                             NUMERIC(16,2),
  ventana_horas                      SMALLINT,
  severidad                          VARCHAR(10) NOT NULL,
  accion_automatica                  VARCHAR(20) NOT NULL,
  activa                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_regla_cumplimiento PRIMARY KEY (id),
  CONSTRAINT ck_regla_cumplimiento_categoria CHECK (categoria IN ('FRACCIONAMIENTO', 'LISTA_RESTRICTIVA', 'RED_SOSPECHOSA', 'UMBRAL_MONTO', 'VELOCIDAD')),
  CONSTRAINT ck_regla_cumplimiento_severidad CHECK (severidad IN ('ALTA', 'BAJA', 'CRITICA', 'MEDIA')),
  CONSTRAINT ck_regla_cumplimiento_accion_automatica CHECK (accion_automatica IN ('ALERTAR', 'BLOQUEAR_USUARIO', 'RETENER_OPERACION'))
);

COMMENT ON TABLE auditoria.regla_cumplimiento IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.regla_cumplimiento.id IS 'PK';
COMMENT ON COLUMN auditoria.regla_cumplimiento.codigo IS 'UQ';
COMMENT ON COLUMN auditoria.regla_cumplimiento.categoria IS 'CK';
COMMENT ON COLUMN auditoria.regla_cumplimiento.umbral IS 'NULL';
COMMENT ON COLUMN auditoria.regla_cumplimiento.ventana_horas IS 'NULL';
COMMENT ON COLUMN auditoria.regla_cumplimiento.severidad IS 'CK';
COMMENT ON COLUMN auditoria.regla_cumplimiento.accion_automatica IS 'CK';
