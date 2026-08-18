-- datos_facturacion · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: DatosFacturacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.datos_facturacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  tipo_documento                     VARCHAR(5) NOT NULL,
  numero_documento                   VARCHAR(20) NOT NULL,
  razon_social                       VARCHAR(150) NOT NULL,
  email_envio                        VARCHAR(120) NOT NULL,
  es_predeterminado                  BOOLEAN DEFAULT FALSE NOT NULL,
  verificado                         BOOLEAN DEFAULT FALSE NOT NULL,
  actualizado_en                     TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_datos_facturacion PRIMARY KEY (id),
  CONSTRAINT ck_datos_facturacion_tipo_documento CHECK (tipo_documento IN ('CEX', 'CI', 'NIT'))
);

COMMENT ON TABLE tarifas.datos_facturacion IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.datos_facturacion.id IS 'PK';
COMMENT ON COLUMN tarifas.datos_facturacion.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN tarifas.datos_facturacion.tipo_documento IS 'CK';
COMMENT ON COLUMN tarifas.datos_facturacion.numero_documento IS 'UQ+usuario_id';
