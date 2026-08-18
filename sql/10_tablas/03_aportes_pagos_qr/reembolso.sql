-- reembolso · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: Reembolso
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.reembolso (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  pago_id                            UUID NOT NULL,
  monto                              NUMERIC(14,2) NOT NULL,
  motivo                             VARCHAR(30) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  referencia_proveedor               VARCHAR(80),
  solicitado_por                     UUID NOT NULL,
  aprobado_por                       UUID,
  fecha_solicitud                    TIMESTAMPTZ NOT NULL,
  fecha_ejecucion                    TIMESTAMPTZ,
  CONSTRAINT pk_reembolso PRIMARY KEY (id),
  CONSTRAINT ck_reembolso_motivo CHECK (motivo IN ('DISPUTA', 'DUPLICADO', 'ERROR_MONTO', 'GRUPO_CANCELADO')),
  CONSTRAINT ck_reembolso_estado CHECK (estado IN ('APROBADO', 'EJECUTADO', 'RECHAZADO', 'SOLICITADO'))
);

COMMENT ON TABLE aportes.reembolso IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.reembolso.id IS 'PK';
COMMENT ON COLUMN aportes.reembolso.pago_id IS 'FK, IDX';
COMMENT ON COLUMN aportes.reembolso.motivo IS 'CK';
COMMENT ON COLUMN aportes.reembolso.estado IS 'CK';
COMMENT ON COLUMN aportes.reembolso.referencia_proveedor IS 'NULL';
COMMENT ON COLUMN aportes.reembolso.solicitado_por IS 'FK';
COMMENT ON COLUMN aportes.reembolso.aprobado_por IS 'FK, NULL';
COMMENT ON COLUMN aportes.reembolso.fecha_ejecucion IS 'NULL';
