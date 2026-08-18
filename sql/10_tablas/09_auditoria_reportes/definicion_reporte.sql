-- definicion_reporte · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: DefinicionReporte
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.definicion_reporte (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  tipo                               VARCHAR(40) NOT NULL,
  nombre                             VARCHAR(80) NOT NULL,
  descripcion                        VARCHAR(300) NOT NULL,
  consulta_base                      TEXT NOT NULL,
  parametros_esperados               JSONB NOT NULL,
  columnas                           JSONB NOT NULL,
  permiso_requerido                  VARCHAR(60) NOT NULL,
  contiene_datos_sensibles           BOOLEAN DEFAULT FALSE NOT NULL,
  cache_minutos                      SMALLINT NOT NULL,
  activa                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_definicion_reporte PRIMARY KEY (id),
  CONSTRAINT ck_definicion_reporte_tipo CHECK (tipo IN ('CARTERA_EN_MORA', 'CONCILIACION_DIARIA', 'DESEMPENO_ORGANIZADOR', 'ESTADO_DE_CUENTA_PARTICIPANTE', 'ESTADO_DE_GRUPO', 'HISTORICO_DE_PAGOS', 'INCUMPLIMIENTOS_Y_SANCIONES', 'KPI_PLATAFORMA', 'MOVIMIENTO_FONDO_GARANTIA', 'OPERACIONES_SOSPECHOSAS'))
);

COMMENT ON TABLE auditoria.definicion_reporte IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.definicion_reporte.id IS 'PK';
COMMENT ON COLUMN auditoria.definicion_reporte.tipo IS 'CK';
COMMENT ON COLUMN auditoria.definicion_reporte.nombre IS 'UQ';
