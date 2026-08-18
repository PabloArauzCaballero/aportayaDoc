-- conciliacion · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: Conciliacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.conciliacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  pago_id                            UUID NOT NULL,
  movimiento_bancario_id             UUID,
  estado                             VARCHAR(25) NOT NULL,
  metodo                             VARCHAR(20) NOT NULL,
  diferencia_monto                   NUMERIC(14,2) NOT NULL,
  conciliado_por                     UUID,
  fecha_conciliacion                 TIMESTAMPTZ,
  CONSTRAINT pk_conciliacion PRIMARY KEY (id),
  CONSTRAINT ck_conciliacion_estado CHECK (estado IN ('CONCILIADO_AUTOMATICO', 'CONCILIADO_MANUAL', 'DESCARTADO', 'EN_EXCEPCION', 'NO_IDENTIFICADO', 'PENDIENTE')),
  CONSTRAINT ck_conciliacion_metodo CHECK (metodo IN ('MANUAL', 'MONTO_FECHA', 'REFERENCIA_EXACTA'))
);

COMMENT ON TABLE aportes.conciliacion IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.conciliacion.id IS 'PK';
COMMENT ON COLUMN aportes.conciliacion.pago_id IS 'FK, UQ';
COMMENT ON COLUMN aportes.conciliacion.movimiento_bancario_id IS 'FK, NULL, UQ';
COMMENT ON COLUMN aportes.conciliacion.estado IS 'CK, IDX';
COMMENT ON COLUMN aportes.conciliacion.metodo IS 'CK';
COMMENT ON COLUMN aportes.conciliacion.conciliado_por IS 'FK, NULL';
COMMENT ON COLUMN aportes.conciliacion.fecha_conciliacion IS 'NULL';
