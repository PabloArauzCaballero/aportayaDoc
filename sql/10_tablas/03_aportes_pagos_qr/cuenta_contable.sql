-- cuenta_contable · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: CuentaContable
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.cuenta_contable (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(20) NOT NULL,
  nombre                             VARCHAR(80) NOT NULL,
  tipo                               VARCHAR(15) NOT NULL,
  naturaleza                         VARCHAR(12) NOT NULL,
  cuenta_padre_id                    UUID,
  nivel                              SMALLINT NOT NULL,
  es_cuenta_de_movimiento            BOOLEAN DEFAULT FALSE NOT NULL,
  grupo_id                           UUID,
  participante_id                    UUID,
  saldo                              NUMERIC(16,2) NOT NULL,
  CONSTRAINT pk_cuenta_contable PRIMARY KEY (id),
  CONSTRAINT ck_cuenta_contable_tipo CHECK (tipo IN ('ACTIVO', 'EGRESO', 'INGRESO', 'PASIVO', 'PATRIMONIO')),
  CONSTRAINT ck_cuenta_contable_naturaleza CHECK (naturaleza IN ('ACREEDORA', 'DEUDORA'))
);

COMMENT ON TABLE nucleo_financiero.cuenta_contable IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN nucleo_financiero.cuenta_contable.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.cuenta_contable.codigo IS 'UQ';
COMMENT ON COLUMN nucleo_financiero.cuenta_contable.tipo IS 'CK';
COMMENT ON COLUMN nucleo_financiero.cuenta_contable.naturaleza IS 'CK';
COMMENT ON COLUMN nucleo_financiero.cuenta_contable.cuenta_padre_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN nucleo_financiero.cuenta_contable.grupo_id IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.cuenta_contable.participante_id IS 'FK, NULL';
