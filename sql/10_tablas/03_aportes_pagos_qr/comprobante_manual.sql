-- comprobante_manual · módulo 03 — Aportes, Pagos QR y Conciliación
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.comprobante_manual (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  pago_id                            UUID NOT NULL,
  archivo_url                        VARCHAR(255) NOT NULL,
  hash_archivo                       VARCHAR(64) NOT NULL,
  estado_revision                    VARCHAR(15) NOT NULL,
  revisado_por                       UUID,
  segunda_revision_por               UUID,
  motivo_rechazo                     VARCHAR(200),
  fecha_revision                     TIMESTAMPTZ,
  CONSTRAINT pk_comprobante_manual PRIMARY KEY (id),
  CONSTRAINT ck_comprobante_manual_estado_revision CHECK (estado_revision IN ('APROBADO', 'OBSERVADO', 'PENDIENTE', 'RECHAZADO'))
);

COMMENT ON TABLE aportes.comprobante_manual IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.comprobante_manual.id IS 'PK';
COMMENT ON COLUMN aportes.comprobante_manual.pago_id IS 'FK, UQ';
COMMENT ON COLUMN aportes.comprobante_manual.estado_revision IS 'CK';
COMMENT ON COLUMN aportes.comprobante_manual.revisado_por IS 'FK, NULL';
COMMENT ON COLUMN aportes.comprobante_manual.segunda_revision_por IS 'FK, NULL';
COMMENT ON COLUMN aportes.comprobante_manual.motivo_rechazo IS 'NULL';
COMMENT ON COLUMN aportes.comprobante_manual.fecha_revision IS 'NULL';
