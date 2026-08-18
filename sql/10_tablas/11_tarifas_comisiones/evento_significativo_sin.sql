-- evento_significativo_sin · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.evento_significativo_sin (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  registrado_por                     UUID,
  codigo_evento                      VARCHAR(10) NOT NULL,
  descripcion                        VARCHAR(200) NOT NULL,
  sucursal                           SMALLINT NOT NULL,
  punto_venta                        SMALLINT NOT NULL,
  cufd_evento                        VARCHAR(120) NOT NULL,
  fecha_inicio                       TIMESTAMPTZ NOT NULL,
  fecha_fin                          TIMESTAMPTZ,
  cantidad_documentos_offline        INTEGER DEFAULT 0 NOT NULL,
  plazo_registro                     TIMESTAMPTZ NOT NULL,
  registrado_en_sin                  TIMESTAMPTZ,
  codigo_recepcion_evento            VARCHAR(60),
  estado                             VARCHAR(15) NOT NULL,
  CONSTRAINT pk_evento_significativo_sin PRIMARY KEY (id),
  CONSTRAINT ck_evento_significativo_sin_codigo_evento CHECK (codigo_evento ~ '^[0-9]{1,10}$'),
  CONSTRAINT ck_evento_significativo_sin_estado CHECK (estado IN ('ABIERTO', 'CERRADO', 'REGISTRADO', 'VENCIDO'))
);

COMMENT ON TABLE tarifas.evento_significativo_sin IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.evento_significativo_sin.id IS 'PK';
COMMENT ON COLUMN tarifas.evento_significativo_sin.registrado_por IS 'FK, NULL';
COMMENT ON COLUMN tarifas.evento_significativo_sin.codigo_evento IS 'CK, IDX';
COMMENT ON COLUMN tarifas.evento_significativo_sin.fecha_inicio IS 'IDX';
COMMENT ON COLUMN tarifas.evento_significativo_sin.fecha_fin IS 'NULL';
COMMENT ON COLUMN tarifas.evento_significativo_sin.plazo_registro IS 'IDX';
COMMENT ON COLUMN tarifas.evento_significativo_sin.registrado_en_sin IS 'NULL';
COMMENT ON COLUMN tarifas.evento_significativo_sin.codigo_recepcion_evento IS 'UQ, NULL';
COMMENT ON COLUMN tarifas.evento_significativo_sin.estado IS 'CK, IDX';
