-- asignacion_tarifario · módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
-- clase de dominio: AsignacionTarifario
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS tarifas.asignacion_tarifario (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  tarifario_id                       UUID NOT NULL,
  segmento_id                        UUID,
  grupo_id                           UUID,
  usuario_id                         UUID,
  autorizado_por                     UUID,
  ambito                             VARCHAR(15) NOT NULL,
  prioridad                          SMALLINT NOT NULL,
  motivo                             VARCHAR(200),
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  CONSTRAINT pk_asignacion_tarifario PRIMARY KEY (id),
  CONSTRAINT ck_asignacion_tarifario_ambito CHECK (ambito IN ('GLOBAL', 'GRUPO', 'SEGMENTO', 'USUARIO'))
);

COMMENT ON TABLE tarifas.asignacion_tarifario IS 'Módulo 11 — Tarifas, Comisiones, Impuestos y Facturación. La política de cobro es dato, no código: se cambia con un seeder';
COMMENT ON COLUMN tarifas.asignacion_tarifario.id IS 'PK';
COMMENT ON COLUMN tarifas.asignacion_tarifario.tarifario_id IS 'FK, IDX';
COMMENT ON COLUMN tarifas.asignacion_tarifario.segmento_id IS 'FK, NULL';
COMMENT ON COLUMN tarifas.asignacion_tarifario.grupo_id IS 'FK, NULL, M2';
COMMENT ON COLUMN tarifas.asignacion_tarifario.usuario_id IS 'FK, NULL, M1';
COMMENT ON COLUMN tarifas.asignacion_tarifario.autorizado_por IS 'FK, NULL';
COMMENT ON COLUMN tarifas.asignacion_tarifario.ambito IS 'CK, IDX';
COMMENT ON COLUMN tarifas.asignacion_tarifario.motivo IS 'NULL';
COMMENT ON COLUMN tarifas.asignacion_tarifario.vigente_hasta IS 'NULL';
