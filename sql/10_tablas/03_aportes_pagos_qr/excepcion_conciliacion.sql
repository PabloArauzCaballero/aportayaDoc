-- excepcion_conciliacion · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: ExcepcionConciliacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.excepcion_conciliacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  conciliacion_id                    UUID NOT NULL,
  tipo                               VARCHAR(30) NOT NULL,
  descripcion                        VARCHAR(300) NOT NULL,
  monto_diferencia                   NUMERIC(14,2) DEFAULT 0 NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  asignada_a                         UUID,
  resolucion                         VARCHAR(300),
  abierta_en                         TIMESTAMPTZ DEFAULT now() NOT NULL,
  resuelta_en                        TIMESTAMPTZ,
  CONSTRAINT pk_excepcion_conciliacion PRIMARY KEY (id),
  CONSTRAINT ck_excepcion_conciliacion_tipo CHECK (tipo IN ('FUERA_DE_PLAZO', 'MONEDA_DISTINTA', 'MONTO_DISTINTO', 'OBLIGACION_SIN_PAGO', 'PAGO_DUPLICADO', 'PAGO_SIN_OBLIGACION', 'REFERENCIA_AUSENTE')),
  CONSTRAINT ck_excepcion_conciliacion_estado CHECK (estado IN ('ABIERTA', 'EN_GESTION', 'ESCALADA', 'RESUELTA'))
);

COMMENT ON TABLE aportes.excepcion_conciliacion IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.excepcion_conciliacion.id IS 'PK';
COMMENT ON COLUMN aportes.excepcion_conciliacion.conciliacion_id IS 'FK, IDX';
COMMENT ON COLUMN aportes.excepcion_conciliacion.tipo IS 'CK';
COMMENT ON COLUMN aportes.excepcion_conciliacion.estado IS 'CK, IDX';
COMMENT ON COLUMN aportes.excepcion_conciliacion.asignada_a IS 'FK, NULL';
COMMENT ON COLUMN aportes.excepcion_conciliacion.resolucion IS 'NULL';
COMMENT ON COLUMN aportes.excepcion_conciliacion.resuelta_en IS 'NULL';
