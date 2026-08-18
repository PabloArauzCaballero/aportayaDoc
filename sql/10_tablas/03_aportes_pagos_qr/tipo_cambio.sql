-- tipo_cambio · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: TipoCambio
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS catalogo.tipo_cambio (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  moneda_origen                      CHAR(3) NOT NULL,
  moneda_destino                     CHAR(3) NOT NULL,
  fecha                              DATE NOT NULL,
  tipo_cambio                        NUMERIC(12,6) NOT NULL,
  fuente                             VARCHAR(15) NOT NULL,
  cargado_en                         TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_tipo_cambio PRIMARY KEY (id),
  CONSTRAINT ck_tipo_cambio_tipo_cambio CHECK (tipo_cambio > 0),
  CONSTRAINT ck_tipo_cambio_fuente CHECK (fuente IN ('BCB', 'MANUAL', 'PROVEEDOR'))
);

COMMENT ON TABLE catalogo.tipo_cambio IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN catalogo.tipo_cambio.id IS 'PK';
COMMENT ON COLUMN catalogo.tipo_cambio.moneda_origen IS 'UQ+moneda_destino+fecha';
COMMENT ON COLUMN catalogo.tipo_cambio.fecha IS 'IDX';
COMMENT ON COLUMN catalogo.tipo_cambio.tipo_cambio IS 'CK: > 0';
COMMENT ON COLUMN catalogo.tipo_cambio.fuente IS 'CK';
