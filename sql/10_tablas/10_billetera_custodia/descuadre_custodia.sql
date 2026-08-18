-- descuadre_custodia · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: DescuadreCustodia
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.descuadre_custodia (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  conciliacion_custodia_id           UUID NOT NULL,
  incidente_operativo_id             UUID,
  resuelto_por                       UUID,
  tipo                               VARCHAR(25) NOT NULL,
  monto_diferencia                   NUMERIC(18,2) DEFAULT 0 NOT NULL,
  severidad                          VARCHAR(10) NOT NULL,
  explicacion                        VARCHAR(500),
  plan_accion                        VARCHAR(500),
  estado                             VARCHAR(15) NOT NULL,
  detectado_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  resuelto_en                        TIMESTAMPTZ,
  CONSTRAINT pk_descuadre_custodia PRIMARY KEY (id),
  CONSTRAINT ck_descuadre_custodia_tipo CHECK (tipo IN ('DESFASE_TEMPORAL', 'ERROR_REGISTRO', 'FALTANTE', 'SOBRANTE')),
  CONSTRAINT ck_descuadre_custodia_severidad CHECK (severidad IN ('ALTA', 'BAJA', 'CRITICA', 'MEDIA')),
  CONSTRAINT ck_descuadre_custodia_estado CHECK (estado IN ('ABIERTO', 'EN_ANALISIS', 'ESCALADO', 'RESUELTO'))
);

COMMENT ON TABLE nucleo_financiero.descuadre_custodia IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.descuadre_custodia.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.descuadre_custodia.conciliacion_custodia_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.descuadre_custodia.incidente_operativo_id IS 'FK, NULL, M9';
COMMENT ON COLUMN nucleo_financiero.descuadre_custodia.resuelto_por IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.descuadre_custodia.tipo IS 'CK';
COMMENT ON COLUMN nucleo_financiero.descuadre_custodia.severidad IS 'CK, IDX';
COMMENT ON COLUMN nucleo_financiero.descuadre_custodia.explicacion IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.descuadre_custodia.plan_accion IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.descuadre_custodia.estado IS 'CK, IDX';
COMMENT ON COLUMN nucleo_financiero.descuadre_custodia.resuelto_en IS 'NULL';
