-- oficial_cumplimiento · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: OficialCumplimiento
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.oficial_cumplimiento (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  tipo                               VARCHAR(10) NOT NULL,
  fecha_designacion                  DATE NOT NULL,
  acta_designacion                   VARCHAR(80) NOT NULL,
  comunicada_al_regulador_en         DATE,
  fecha_baja                         DATE,
  activo                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_oficial_cumplimiento PRIMARY KEY (id),
  CONSTRAINT ck_oficial_cumplimiento_tipo CHECK (tipo IN ('SUPLENTE', 'TITULAR'))
);

COMMENT ON TABLE cumplimiento.oficial_cumplimiento IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.oficial_cumplimiento.id IS 'PK';
COMMENT ON COLUMN cumplimiento.oficial_cumplimiento.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.oficial_cumplimiento.tipo IS 'CK';
COMMENT ON COLUMN cumplimiento.oficial_cumplimiento.comunicada_al_regulador_en IS 'NULL';
COMMENT ON COLUMN cumplimiento.oficial_cumplimiento.fecha_baja IS 'NULL';
COMMENT ON COLUMN cumplimiento.oficial_cumplimiento.activo IS 'IDX';
