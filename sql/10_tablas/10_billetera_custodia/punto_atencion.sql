-- punto_atencion · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: PuntoAtencion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.punto_atencion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(20) NOT NULL,
  tipo                               VARCHAR(25) NOT NULL,
  razon_social                       VARCHAR(120) NOT NULL,
  nit                                VARCHAR(20),
  departamento                       VARCHAR(30) NOT NULL,
  municipio                          VARCHAR(60) NOT NULL,
  direccion                          VARCHAR(200) NOT NULL,
  responsable_usuario_id             UUID,
  limite_efectivo_diario             NUMERIC(16,2) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  habilitado_desde                   DATE NOT NULL,
  CONSTRAINT pk_punto_atencion PRIMARY KEY (id),
  CONSTRAINT ck_punto_atencion_tipo CHECK (tipo IN ('AGENCIA_PROPIA', 'AGENTE_CORRESPONSAL', 'CAJERO')),
  CONSTRAINT ck_punto_atencion_estado CHECK (estado IN ('CERRADO', 'HABILITADO', 'SUSPENDIDO'))
);

COMMENT ON TABLE nucleo_financiero.punto_atencion IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.punto_atencion.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.punto_atencion.codigo IS 'UQ';
COMMENT ON COLUMN nucleo_financiero.punto_atencion.tipo IS 'CK';
COMMENT ON COLUMN nucleo_financiero.punto_atencion.nit IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.punto_atencion.responsable_usuario_id IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.punto_atencion.estado IS 'CK, IDX';
