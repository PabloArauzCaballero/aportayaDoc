-- cuenta_bancaria_beneficiario · módulo 04 — Entregas de Fondo
-- clase de dominio: CuentaBancariaBeneficiario
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS entregas.cuenta_bancaria_beneficiario (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  tipo_cuenta                        VARCHAR(15) NOT NULL,
  entidad_financiera                 VARCHAR(60) NOT NULL,
  numero_cuenta_cifrado              VARCHAR(255) NOT NULL,
  version_llave                      SMALLINT NOT NULL,
  hash_numero_cuenta                 VARCHAR(64) NOT NULL,
  numero_enmascarado                 VARCHAR(30) NOT NULL,
  titular_nombre                     VARCHAR(120) NOT NULL,
  titular_documento                  VARCHAR(30) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  es_principal                       BOOLEAN DEFAULT FALSE NOT NULL,
  estado_verificacion                VARCHAR(15) NOT NULL,
  metodo_verificacion                VARCHAR(20),
  verificada_en                      TIMESTAMPTZ,
  bloqueada_hasta                    TIMESTAMPTZ,
  CONSTRAINT pk_cuenta_bancaria_beneficiario PRIMARY KEY (id),
  CONSTRAINT ck_cuenta_bancaria_beneficiario_tipo_cuenta CHECK (tipo_cuenta IN ('AHORRO', 'BILLETERA', 'CORRIENTE')),
  CONSTRAINT ck_cuenta_bancaria_beneficiario_estado_verificacion CHECK (estado_verificacion IN ('PENDIENTE', 'RECHAZADA', 'VERIFICADA'))
);

COMMENT ON TABLE entregas.cuenta_bancaria_beneficiario IS 'Módulo 04 — Entregas de Fondo. Que la bolsa llegue completa, a la persona correcta, una sola vez';
COMMENT ON COLUMN entregas.cuenta_bancaria_beneficiario.id IS 'PK';
COMMENT ON COLUMN entregas.cuenta_bancaria_beneficiario.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN entregas.cuenta_bancaria_beneficiario.tipo_cuenta IS 'CK';
COMMENT ON COLUMN entregas.cuenta_bancaria_beneficiario.hash_numero_cuenta IS 'UQ+usuario_id';
COMMENT ON COLUMN entregas.cuenta_bancaria_beneficiario.estado_verificacion IS 'CK';
COMMENT ON COLUMN entregas.cuenta_bancaria_beneficiario.metodo_verificacion IS 'NULL';
COMMENT ON COLUMN entregas.cuenta_bancaria_beneficiario.verificada_en IS 'NULL';
COMMENT ON COLUMN entregas.cuenta_bancaria_beneficiario.bloqueada_hasta IS 'NULL';
