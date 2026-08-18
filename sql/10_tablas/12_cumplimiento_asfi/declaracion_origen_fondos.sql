-- declaracion_origen_fondos · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: DeclaracionOrigenFondos
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.declaracion_origen_fondos (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  transaccion_id                     UUID,
  verificada_por                     UUID,
  monto                              NUMERIC(16,2) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  origen                             VARCHAR(20) NOT NULL,
  descripcion                        VARCHAR(300) NOT NULL,
  documento_respaldo_url             VARCHAR(255),
  hash_documento                     VARCHAR(64),
  estado                             VARCHAR(15) NOT NULL,
  declarada_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_declaracion_origen_fondos PRIMARY KEY (id),
  CONSTRAINT ck_declaracion_origen_fondos_origen CHECK (origen IN ('HERENCIA', 'NEGOCIO', 'OTRO', 'PRESTAMO', 'REMESA', 'SALARIO', 'VENTA_BIEN')),
  CONSTRAINT ck_declaracion_origen_fondos_estado CHECK (estado IN ('DECLARADA', 'OBSERVADA', 'RECHAZADA', 'VERIFICADA'))
);

COMMENT ON TABLE cumplimiento.declaracion_origen_fondos IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.declaracion_origen_fondos.id IS 'PK';
COMMENT ON COLUMN cumplimiento.declaracion_origen_fondos.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.declaracion_origen_fondos.transaccion_id IS 'FK, NULL, M10';
COMMENT ON COLUMN cumplimiento.declaracion_origen_fondos.verificada_por IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.declaracion_origen_fondos.origen IS 'CK';
COMMENT ON COLUMN cumplimiento.declaracion_origen_fondos.documento_respaldo_url IS 'NULL';
COMMENT ON COLUMN cumplimiento.declaracion_origen_fondos.hash_documento IS 'NULL';
COMMENT ON COLUMN cumplimiento.declaracion_origen_fondos.estado IS 'CK';
