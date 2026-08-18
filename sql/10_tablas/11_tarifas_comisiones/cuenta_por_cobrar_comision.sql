-- cuenta_por_cobrar_comision · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: CuentaPorCobrarComision
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.cuenta_por_cobrar_comision (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  devengo_id                         UUID NOT NULL,
  usuario_id                         UUID NOT NULL,
  gestion_cobranza_id                UUID,
  monto                              NUMERIC(12,2) NOT NULL,
  saldo                              NUMERIC(12,2) NOT NULL,
  dias_vencido                       SMALLINT NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  vence_en                           DATE NOT NULL,
  castigada_en                       TIMESTAMPTZ,
  CONSTRAINT pk_cuenta_por_cobrar_comision PRIMARY KEY (id),
  CONSTRAINT ck_cuenta_por_cobrar_comision_estado CHECK (estado IN ('CASTIGADA', 'EN_COBRANZA', 'PAGADA', 'VENCIDA', 'VIGENTE'))
);

COMMENT ON TABLE tarifas.cuenta_por_cobrar_comision IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.cuenta_por_cobrar_comision.id IS 'PK';
COMMENT ON COLUMN tarifas.cuenta_por_cobrar_comision.devengo_id IS 'FK, UQ';
COMMENT ON COLUMN tarifas.cuenta_por_cobrar_comision.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN tarifas.cuenta_por_cobrar_comision.gestion_cobranza_id IS 'FK, NULL, M8';
COMMENT ON COLUMN tarifas.cuenta_por_cobrar_comision.dias_vencido IS 'IDX';
COMMENT ON COLUMN tarifas.cuenta_por_cobrar_comision.estado IS 'CK, IDX';
COMMENT ON COLUMN tarifas.cuenta_por_cobrar_comision.castigada_en IS 'NULL';
