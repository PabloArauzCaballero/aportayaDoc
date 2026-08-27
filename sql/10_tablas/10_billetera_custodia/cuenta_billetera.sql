-- cuenta_billetera · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: CuentaBilletera
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.cuenta_billetera (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  numero_cuenta                      VARCHAR(20) NOT NULL,
  tipo                               VARCHAR(35) NOT NULL,
  usuario_id                         UUID,
  grupo_id                           UUID,
  politica_billetera_id              UUID,
  cuenta_contable_id                 UUID,
  moneda                             CHAR(3) NOT NULL,
  estado                             VARCHAR(25) NOT NULL,
  nivel_debida_diligencia            VARCHAR(15) NOT NULL,
  saldo_disponible                   NUMERIC(16,2) DEFAULT 0 NOT NULL,
  saldo_retenido                     NUMERIC(16,2) DEFAULT 0 NOT NULL,
  saldo_total                        NUMERIC(16,2) GENERATED ALWAYS AS (saldo_disponible + saldo_retenido) STORED,
  permite_saldo_negativo             BOOLEAN DEFAULT FALSE NOT NULL,
  fecha_apertura                     TIMESTAMPTZ NOT NULL,
  fecha_cierre                       TIMESTAMPTZ,
  version                            INTEGER DEFAULT 0 NOT NULL,
  CONSTRAINT pk_cuenta_billetera PRIMARY KEY (id),
  CONSTRAINT ck_cuenta_billetera_tipo CHECK (tipo IN ('FONDO_GARANTIA', 'GRUPO', 'LIQUIDACION_PROVEEDOR', 'PLATAFORMA_IMPUESTOS_POR_PAGAR', 'PLATAFORMA_INGRESOS', 'PUENTE_CUSTODIA', 'SUSPENSO_NO_IDENTIFICADO', 'USUARIO')),
  CONSTRAINT ck_cuenta_billetera_estado CHECK (estado IN ('ACTIVA', 'BLOQUEADA_AUTORIDAD', 'CERRADA', 'CONGELADA', 'EN_APERTURA', 'EN_CIERRE', 'LIMITADA')),
  CONSTRAINT ck_cuenta_billetera_nivel_debida_diligencia CHECK (nivel_debida_diligencia IN ('AMPLIADA', 'ESTANDAR', 'REFORZADA', 'SIMPLIFICADA'))
);

COMMENT ON TABLE nucleo_financiero.cuenta_billetera IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.cuenta_billetera.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.cuenta_billetera.numero_cuenta IS 'UQ';
COMMENT ON COLUMN nucleo_financiero.cuenta_billetera.tipo IS 'CK, IDX';
COMMENT ON COLUMN nucleo_financiero.cuenta_billetera.usuario_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN nucleo_financiero.cuenta_billetera.grupo_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN nucleo_financiero.cuenta_billetera.politica_billetera_id IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.cuenta_billetera.cuenta_contable_id IS 'FK, NULL, M3';
COMMENT ON COLUMN nucleo_financiero.cuenta_billetera.estado IS 'CK, IDX';
COMMENT ON COLUMN nucleo_financiero.cuenta_billetera.nivel_debida_diligencia IS 'CK';
COMMENT ON COLUMN nucleo_financiero.cuenta_billetera.saldo_total IS 'GENERATED';
COMMENT ON COLUMN nucleo_financiero.cuenta_billetera.fecha_cierre IS 'NULL';
