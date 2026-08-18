-- instrumento_fondeo · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: InstrumentoFondeo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.instrumento_fondeo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  tipo                               VARCHAR(20) NOT NULL,
  entidad_financiera                 VARCHAR(60) NOT NULL,
  token_proveedor                    VARCHAR(255),
  hash_identificador                 VARCHAR(64) NOT NULL,
  enmascarado                        VARCHAR(30) NOT NULL,
  titular_nombre                     VARCHAR(120) NOT NULL,
  titular_documento                  VARCHAR(30) NOT NULL,
  titular_coincide                   BOOLEAN DEFAULT FALSE NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  es_principal                       BOOLEAN DEFAULT FALSE NOT NULL,
  estado_verificacion                VARCHAR(15) NOT NULL,
  metodo_verificacion                VARCHAR(20),
  verificado_en                      TIMESTAMPTZ,
  bloqueado_hasta                    TIMESTAMPTZ,
  CONSTRAINT pk_instrumento_fondeo PRIMARY KEY (id),
  CONSTRAINT ck_instrumento_fondeo_tipo CHECK (tipo IN ('AGENTE', 'CUENTA_BANCARIA', 'EFECTIVO', 'QR_BANCARIO', 'TARJETA')),
  CONSTRAINT ck_instrumento_fondeo_estado_verificacion CHECK (estado_verificacion IN ('PENDIENTE', 'RECHAZADO', 'VENCIDO', 'VERIFICADO'))
);

COMMENT ON TABLE nucleo_financiero.instrumento_fondeo IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.instrumento_fondeo.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.instrumento_fondeo.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN nucleo_financiero.instrumento_fondeo.tipo IS 'CK';
COMMENT ON COLUMN nucleo_financiero.instrumento_fondeo.token_proveedor IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.instrumento_fondeo.hash_identificador IS 'UQ+usuario_id';
COMMENT ON COLUMN nucleo_financiero.instrumento_fondeo.estado_verificacion IS 'CK';
COMMENT ON COLUMN nucleo_financiero.instrumento_fondeo.metodo_verificacion IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.instrumento_fondeo.verificado_en IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.instrumento_fondeo.bloqueado_hasta IS 'NULL';
