-- proceso_anonimizacion · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: ProcesoAnonimizacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.proceso_anonimizacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  solicitud_id                       UUID,
  estrategia                         VARCHAR(25) NOT NULL,
  entidades_afectadas                JSONB NOT NULL,
  datos_retenidos_por_ley            JSONB NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  ejecutado_en                       TIMESTAMPTZ,
  CONSTRAINT pk_proceso_anonimizacion PRIMARY KEY (id),
  CONSTRAINT ck_proceso_anonimizacion_estrategia CHECK (estrategia IN ('BORRADO_PARCIAL', 'BORRADO_TOTAL', 'SEUDONIMIZACION')),
  CONSTRAINT ck_proceso_anonimizacion_estado CHECK (estado IN ('EJECUTADO', 'PLANIFICADO', 'REVERTIDO'))
);

COMMENT ON TABLE auditoria.proceso_anonimizacion IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.proceso_anonimizacion.id IS 'PK';
COMMENT ON COLUMN auditoria.proceso_anonimizacion.usuario_id IS 'FK, UQ';
COMMENT ON COLUMN auditoria.proceso_anonimizacion.solicitud_id IS 'FK, NULL';
COMMENT ON COLUMN auditoria.proceso_anonimizacion.estrategia IS 'CK';
COMMENT ON COLUMN auditoria.proceso_anonimizacion.estado IS 'CK';
COMMENT ON COLUMN auditoria.proceso_anonimizacion.ejecutado_en IS 'NULL';
