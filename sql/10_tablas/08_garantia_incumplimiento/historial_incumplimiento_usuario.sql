-- historial_incumplimiento_usuario · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: HistorialIncumplimientoUsuario
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.historial_incumplimiento_usuario (
  usuario_id                         UUID DEFAULT gen_random_uuid() NOT NULL,
  total_incumplimientos              SMALLINT DEFAULT 0 NOT NULL,
  incumplimientos_leves              SMALLINT NOT NULL,
  incumplimientos_graves             SMALLINT NOT NULL,
  incumplimientos_abiertos           SMALLINT NOT NULL,
  monto_total_incumplido             NUMERIC(16,2) DEFAULT 0 NOT NULL,
  monto_total_recuperado             NUMERIC(16,2) DEFAULT 0 NOT NULL,
  monto_castigado_historico          NUMERIC(16,2) DEFAULT 0 NOT NULL,
  grupos_abandonados                 SMALLINT NOT NULL,
  ultimo_incumplimiento_en           TIMESTAMPTZ,
  dias_mora_promedio                 NUMERIC(6,2) NOT NULL,
  tasa_regularizacion                NUMERIC(5,2) NOT NULL,
  esta_en_lista_restriccion          BOOLEAN DEFAULT FALSE NOT NULL,
  actualizado_en                     TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_historial_incumplimiento_usuario PRIMARY KEY (usuario_id)
);

COMMENT ON TABLE garantia.historial_incumplimiento_usuario IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.historial_incumplimiento_usuario.usuario_id IS 'PK, FK';
COMMENT ON COLUMN garantia.historial_incumplimiento_usuario.incumplimientos_abiertos IS 'IDX';
COMMENT ON COLUMN garantia.historial_incumplimiento_usuario.ultimo_incumplimiento_en IS 'NULL';
