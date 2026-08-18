-- exencion_comision · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: ExencionComision
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.exencion_comision (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID,
  grupo_id                           UUID,
  concepto_tarifa_id                 UUID,
  segmento_id                        UUID,
  autorizada_por                     UUID NOT NULL,
  alcance                            VARCHAR(15) NOT NULL,
  motivo                             VARCHAR(25) NOT NULL,
  justificacion                      VARCHAR(300) NOT NULL,
  porcentaje_exencion                NUMERIC(5,2) NOT NULL,
  monto_tope                         NUMERIC(12,2),
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  activa                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_exencion_comision PRIMARY KEY (id),
  CONSTRAINT ck_exencion_comision_alcance CHECK (alcance IN ('CONCEPTO', 'GRUPO', 'SEGMENTO', 'USUARIO')),
  CONSTRAINT ck_exencion_comision_motivo CHECK (motivo IN ('CONVENIO', 'CORTESIA', 'POLITICA_SOCIAL', 'PROMOCION', 'RECLAMO')),
  CONSTRAINT ck_exencion_comision_porcentaje_exencion CHECK (porcentaje_exencion BETWEEN 0 AND 100)
);

COMMENT ON TABLE tarifas.exencion_comision IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.exencion_comision.id IS 'PK';
COMMENT ON COLUMN tarifas.exencion_comision.usuario_id IS 'FK, NULL, M1';
COMMENT ON COLUMN tarifas.exencion_comision.grupo_id IS 'FK, NULL, M2';
COMMENT ON COLUMN tarifas.exencion_comision.concepto_tarifa_id IS 'FK, NULL';
COMMENT ON COLUMN tarifas.exencion_comision.segmento_id IS 'FK, NULL';
COMMENT ON COLUMN tarifas.exencion_comision.autorizada_por IS 'FK';
COMMENT ON COLUMN tarifas.exencion_comision.alcance IS 'CK, IDX';
COMMENT ON COLUMN tarifas.exencion_comision.motivo IS 'CK';
COMMENT ON COLUMN tarifas.exencion_comision.porcentaje_exencion IS 'CK: 0-100';
COMMENT ON COLUMN tarifas.exencion_comision.monto_tope IS 'NULL';
COMMENT ON COLUMN tarifas.exencion_comision.vigente_hasta IS 'NULL';
COMMENT ON COLUMN tarifas.exencion_comision.activa IS 'IDX';
