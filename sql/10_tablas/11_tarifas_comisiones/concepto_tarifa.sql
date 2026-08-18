-- concepto_tarifa · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: ConceptoTarifa
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.concepto_tarifa (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  tarifario_id                       UUID NOT NULL,
  hecho_generador_id                 UUID NOT NULL,
  politica_redondeo_id               UUID,
  cuenta_ingreso_id                  UUID,
  codigo                             VARCHAR(40) NOT NULL,
  nombre_comercial                   VARCHAR(80) NOT NULL,
  descripcion_usuario                VARCHAR(300) NOT NULL,
  metodo_calculo                     VARCHAR(25) NOT NULL,
  base_calculo                       VARCHAR(35) NOT NULL,
  valor_porcentual                   NUMERIC(7,4),
  valor_fijo                         NUMERIC(12,2),
  monto_minimo                       NUMERIC(12,2),
  monto_maximo                       NUMERIC(12,2),
  sujeto_obligado                    VARCHAR(35) NOT NULL,
  forma_cobro                        VARCHAR(30) NOT NULL,
  momento_cobro                      VARCHAR(25) NOT NULL,
  gravado_iva                        BOOLEAN DEFAULT FALSE NOT NULL,
  gravado_it                         BOOLEAN DEFAULT FALSE NOT NULL,
  precio_incluye_impuesto            BOOLEAN DEFAULT FALSE NOT NULL,
  orden_aplicacion                   SMALLINT NOT NULL,
  activo                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_concepto_tarifa PRIMARY KEY (id),
  CONSTRAINT ck_concepto_tarifa_metodo_calculo CHECK (metodo_calculo IN ('ESCALONADO_ACUMULATIVO', 'ESCALONADO_POR_TRAMO', 'FIJO', 'GRATUITO', 'MIXTO', 'PORCENTUAL')),
  CONSTRAINT ck_concepto_tarifa_base_calculo CHECK (base_calculo IN ('MONTO_APORTE', 'MONTO_BOLSA_BRUTO', 'MONTO_FIJO_POR_CICLO', 'MONTO_FIJO_POR_PARTICIPANTE', 'MONTO_NETO_ENTREGA', 'MONTO_RECARGA', 'MONTO_RETIRO', 'MONTO_TRANSFERENCIA', 'SIN_BASE')),
  CONSTRAINT ck_concepto_tarifa_sujeto_obligado CHECK (sujeto_obligado IN ('BENEFICIARIO_DEL_TURNO', 'ORGANIZADOR', 'PAGADOR_DE_LA_OPERACION', 'PLATAFORMA_ASUME', 'PRORRATEO_ENTRE_PARTICIPANTES')),
  CONSTRAINT ck_concepto_tarifa_forma_cobro CHECK (forma_cobro IN ('COBRO_EXTERNO', 'COMPENSACION', 'DEBITO_DE_BILLETERA', 'DEDUCCION_DE_ENTREGA', 'OBLIGACION_DE_APORTE')),
  CONSTRAINT ck_concepto_tarifa_momento_cobro CHECK (momento_cobro IN ('AL_CIERRE_DE_CICLO', 'AL_DEVENGAR', 'AL_LIQUIDAR_ENTREGA', 'DIFERIDO_MENSUAL'))
);

COMMENT ON TABLE tarifas.concepto_tarifa IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.concepto_tarifa.id IS 'PK';
COMMENT ON COLUMN tarifas.concepto_tarifa.tarifario_id IS 'FK, IDX';
COMMENT ON COLUMN tarifas.concepto_tarifa.hecho_generador_id IS 'FK, IDX';
COMMENT ON COLUMN tarifas.concepto_tarifa.politica_redondeo_id IS 'FK, NULL';
COMMENT ON COLUMN tarifas.concepto_tarifa.cuenta_ingreso_id IS 'FK, NULL, M3';
COMMENT ON COLUMN tarifas.concepto_tarifa.codigo IS 'UQ+tarifario_id';
COMMENT ON COLUMN tarifas.concepto_tarifa.metodo_calculo IS 'CK';
COMMENT ON COLUMN tarifas.concepto_tarifa.base_calculo IS 'CK';
COMMENT ON COLUMN tarifas.concepto_tarifa.valor_porcentual IS 'NULL';
COMMENT ON COLUMN tarifas.concepto_tarifa.valor_fijo IS 'NULL';
COMMENT ON COLUMN tarifas.concepto_tarifa.monto_minimo IS 'NULL';
COMMENT ON COLUMN tarifas.concepto_tarifa.monto_maximo IS 'NULL';
COMMENT ON COLUMN tarifas.concepto_tarifa.sujeto_obligado IS 'CK';
COMMENT ON COLUMN tarifas.concepto_tarifa.forma_cobro IS 'CK';
COMMENT ON COLUMN tarifas.concepto_tarifa.momento_cobro IS 'CK';
COMMENT ON COLUMN tarifas.concepto_tarifa.activo IS 'IDX';
