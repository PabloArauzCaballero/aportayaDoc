-- cambio_tarifario · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: CambioTarifario
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.cambio_tarifario (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  tarifario_anterior_id              UUID NOT NULL,
  tarifario_nuevo_id                 UUID NOT NULL,
  aprobado_por                       UUID NOT NULL,
  tipo_cambio                        VARCHAR(20) NOT NULL,
  requiere_preaviso                  BOOLEAN DEFAULT FALSE NOT NULL,
  dias_preaviso                      SMALLINT NOT NULL,
  fecha_aviso                        TIMESTAMPTZ,
  canal_aviso                        VARCHAR(40),
  usuarios_notificados               INTEGER DEFAULT 0 NOT NULL,
  permite_rescision_sin_costo        BOOLEAN DEFAULT FALSE NOT NULL,
  publicado_en                       TIMESTAMPTZ,
  CONSTRAINT pk_cambio_tarifario PRIMARY KEY (id),
  CONSTRAINT ck_cambio_tarifario_tipo_cambio CHECK (tipo_cambio IN ('ELIMINACION', 'INCREMENTO', 'NUEVO_CONCEPTO', 'REDUCCION'))
);

COMMENT ON TABLE tarifas.cambio_tarifario IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.cambio_tarifario.id IS 'PK';
COMMENT ON COLUMN tarifas.cambio_tarifario.tarifario_anterior_id IS 'FK';
COMMENT ON COLUMN tarifas.cambio_tarifario.tarifario_nuevo_id IS 'FK, UQ';
COMMENT ON COLUMN tarifas.cambio_tarifario.aprobado_por IS 'FK';
COMMENT ON COLUMN tarifas.cambio_tarifario.tipo_cambio IS 'CK';
COMMENT ON COLUMN tarifas.cambio_tarifario.fecha_aviso IS 'NULL';
COMMENT ON COLUMN tarifas.cambio_tarifario.canal_aviso IS 'NULL';
COMMENT ON COLUMN tarifas.cambio_tarifario.publicado_en IS 'NULL';
