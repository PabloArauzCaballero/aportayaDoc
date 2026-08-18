-- politica_redondeo · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: PoliticaRedondeo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.politica_redondeo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(30) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  unidad_minima                      NUMERIC(6,4) NOT NULL,
  modo                               VARCHAR(15) NOT NULL,
  aplica_a                           VARCHAR(20) NOT NULL,
  CONSTRAINT pk_politica_redondeo PRIMARY KEY (id),
  CONSTRAINT ck_politica_redondeo_modo CHECK (modo IN ('ABAJO', 'ARRIBA', 'BANCARIO', 'MAS_CERCANO')),
  CONSTRAINT ck_politica_redondeo_aplica_a CHECK (aplica_a IN ('APORTE', 'COMISION', 'IMPUESTO', 'TOTAL'))
);

COMMENT ON TABLE tarifas.politica_redondeo IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.politica_redondeo.id IS 'PK';
COMMENT ON COLUMN tarifas.politica_redondeo.codigo IS 'UQ';
COMMENT ON COLUMN tarifas.politica_redondeo.modo IS 'CK';
COMMENT ON COLUMN tarifas.politica_redondeo.aplica_a IS 'CK';
