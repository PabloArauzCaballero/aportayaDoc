-- expediente_cliente · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: ExpedienteCliente
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.expediente_cliente (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  responsable_id                     UUID,
  completitud_porcentaje             NUMERIC(5,2) NOT NULL,
  documentos                         JSONB NOT NULL,
  ubicacion_fisica                   VARCHAR(120),
  retencion_hasta                    DATE NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  ultima_actualizacion               TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_expediente_cliente PRIMARY KEY (id),
  CONSTRAINT ck_expediente_cliente_estado CHECK (estado IN ('COMPLETO', 'DEPURADO', 'INCOMPLETO', 'OBSERVADO'))
);

COMMENT ON TABLE cumplimiento.expediente_cliente IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.expediente_cliente.id IS 'PK';
COMMENT ON COLUMN cumplimiento.expediente_cliente.usuario_id IS 'FK, UQ, M1';
COMMENT ON COLUMN cumplimiento.expediente_cliente.responsable_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.expediente_cliente.ubicacion_fisica IS 'NULL';
COMMENT ON COLUMN cumplimiento.expediente_cliente.retencion_hasta IS 'IDX';
COMMENT ON COLUMN cumplimiento.expediente_cliente.estado IS 'CK';
