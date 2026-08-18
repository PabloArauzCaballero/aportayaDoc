-- proveedor_pago · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: ProveedorPago
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.proveedor_pago (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(30) NOT NULL,
  nombre                             VARCHAR(80) NOT NULL,
  tipo                               VARCHAR(15) NOT NULL,
  url_base                           VARCHAR(200) NOT NULL,
  referencia_credenciales            VARCHAR(120) NOT NULL,
  comision_fija                      NUMERIC(10,2) NOT NULL,
  comision_porcentual                NUMERIC(5,3) NOT NULL,
  soporta_webhook                    BOOLEAN DEFAULT FALSE NOT NULL,
  soporta_consulta_estado            BOOLEAN DEFAULT FALSE NOT NULL,
  activo                             BOOLEAN DEFAULT FALSE NOT NULL,
  prioridad                          SMALLINT NOT NULL,
  CONSTRAINT pk_proveedor_pago PRIMARY KEY (id),
  CONSTRAINT ck_proveedor_pago_tipo CHECK (tipo IN ('BANCO', 'BILLETERA', 'PASARELA'))
);

COMMENT ON TABLE aportes.proveedor_pago IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.proveedor_pago.id IS 'PK';
COMMENT ON COLUMN aportes.proveedor_pago.codigo IS 'UQ';
COMMENT ON COLUMN aportes.proveedor_pago.tipo IS 'CK';
