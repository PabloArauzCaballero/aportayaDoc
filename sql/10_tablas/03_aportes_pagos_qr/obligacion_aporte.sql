-- obligacion_aporte · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: ObligacionAporte
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.obligacion_aporte (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  periodo_id                         UUID NOT NULL,
  cupo_id                            UUID NOT NULL,
  participante_id                    UUID NOT NULL,
  politica_mora_id                   UUID,
  obligacion_origen_id               UUID,
  plan_regularizacion_id             UUID,
  tipo                               VARCHAR(30) NOT NULL,
  monto_esperado                     NUMERIC(14,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  monto_pagado                       NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_recargo                      NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_condonado                    NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_cubierto_garantia            NUMERIC(14,2) DEFAULT 0 NOT NULL,
  saldo_pendiente                    NUMERIC(14,2) GENERATED ALWAYS AS (monto_esperado + monto_recargo - monto_pagado - monto_condonado - monto_cubierto_garantia) STORED,
  estado                             VARCHAR(30) NOT NULL,
  fecha_vencimiento                  DATE NOT NULL,
  fecha_fin_gracia                   DATE NOT NULL,
  fecha_pago_efectivo                TIMESTAMPTZ,
  dias_mora                          SMALLINT NOT NULL,
  version                            INTEGER DEFAULT 0 NOT NULL,
  CONSTRAINT pk_obligacion_aporte PRIMARY KEY (id),
  CONSTRAINT ck_obligacion_aporte_tipo CHECK (tipo IN ('AJUSTE_MANUAL', 'APORTE_FONDO_GARANTIA', 'APORTE_PERIODICO', 'COMISION_PLATAFORMA', 'RECARGO_MORA', 'REPOSICION_COBERTURA')),
  CONSTRAINT ck_obligacion_aporte_monto_esperado CHECK (monto_esperado > 0),
  CONSTRAINT ck_obligacion_aporte_estado CHECK (estado IN ('ANULADO', 'CONDONADO', 'CUBIERTO_POR_GARANTIA', 'EN_MORA', 'EN_VERIFICACION', 'EXONERADO', 'PAGADO', 'PAGADO_PARCIAL', 'PENDIENTE', 'PROGRAMADO', 'REPORTADO_POR_USUARIO', 'REPROGRAMADO', 'VENCIDO'))
);

COMMENT ON TABLE aportes.obligacion_aporte IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.obligacion_aporte.id IS 'PK';
COMMENT ON COLUMN aportes.obligacion_aporte.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN aportes.obligacion_aporte.periodo_id IS 'FK, IDX';
COMMENT ON COLUMN aportes.obligacion_aporte.cupo_id IS 'FK, IDX';
COMMENT ON COLUMN aportes.obligacion_aporte.participante_id IS 'FK, IDX';
COMMENT ON COLUMN aportes.obligacion_aporte.politica_mora_id IS 'FK, NULL';
COMMENT ON COLUMN aportes.obligacion_aporte.obligacion_origen_id IS 'FK, NULL';
COMMENT ON COLUMN aportes.obligacion_aporte.plan_regularizacion_id IS 'FK, NULL';
COMMENT ON COLUMN aportes.obligacion_aporte.tipo IS 'CK';
COMMENT ON COLUMN aportes.obligacion_aporte.monto_esperado IS 'CK: > 0';
COMMENT ON COLUMN aportes.obligacion_aporte.saldo_pendiente IS 'GENERATED';
COMMENT ON COLUMN aportes.obligacion_aporte.estado IS 'CK, IDX';
COMMENT ON COLUMN aportes.obligacion_aporte.fecha_vencimiento IS 'IDX';
COMMENT ON COLUMN aportes.obligacion_aporte.fecha_pago_efectivo IS 'NULL';
