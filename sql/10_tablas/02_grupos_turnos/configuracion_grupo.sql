-- configuracion_grupo · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- clase de dominio: ConfiguracionGrupo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.configuracion_grupo (
  grupo_id                           UUID DEFAULT gen_random_uuid() NOT NULL,
  permite_cupos_multiples            BOOLEAN DEFAULT FALSE NOT NULL,
  max_cupos_por_persona              SMALLINT NOT NULL,
  permite_permuta_turnos             BOOLEAN DEFAULT FALSE NOT NULL,
  requiere_avalista                  BOOLEAN DEFAULT FALSE NOT NULL,
  permite_ingreso_tardio             BOOLEAN DEFAULT FALSE NOT NULL,
  hora_limite_pago                   TIME NOT NULL,
  tolerancia_monto_parcial           NUMERIC(5,2) NOT NULL,
  politica_mora_id                   UUID,
  politica_sancion_id                UUID,
  CONSTRAINT pk_configuracion_grupo PRIMARY KEY (grupo_id)
);

COMMENT ON TABLE grupos.configuracion_grupo IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.configuracion_grupo.grupo_id IS 'PK, FK';
COMMENT ON COLUMN grupos.configuracion_grupo.politica_mora_id IS 'FK, NULL';
COMMENT ON COLUMN grupos.configuracion_grupo.politica_sancion_id IS 'FK, NULL';
