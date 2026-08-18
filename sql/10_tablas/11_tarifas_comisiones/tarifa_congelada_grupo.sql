-- tarifa_congelada_grupo · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: TarifaCongeladaGrupo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.tarifa_congelada_grupo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  tarifario_id                       UUID NOT NULL,
  acuerdo_id                         UUID,
  snapshot_conceptos                 JSONB NOT NULL,
  hash_snapshot                      VARCHAR(64) NOT NULL,
  congelada_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  vigente_hasta_ciclo_nro            SMALLINT,
  CONSTRAINT pk_tarifa_congelada_grupo PRIMARY KEY (id)
);

COMMENT ON TABLE tarifas.tarifa_congelada_grupo IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.tarifa_congelada_grupo.id IS 'PK';
COMMENT ON COLUMN tarifas.tarifa_congelada_grupo.grupo_id IS 'FK, UQ, M2';
COMMENT ON COLUMN tarifas.tarifa_congelada_grupo.tarifario_id IS 'FK';
COMMENT ON COLUMN tarifas.tarifa_congelada_grupo.acuerdo_id IS 'FK, NULL, M2';
COMMENT ON COLUMN tarifas.tarifa_congelada_grupo.vigente_hasta_ciclo_nro IS 'NULL';
