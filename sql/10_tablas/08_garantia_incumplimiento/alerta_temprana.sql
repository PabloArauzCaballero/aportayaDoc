-- alerta_temprana · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: AlertaTemprana
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.alerta_temprana (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  grupo_id                           UUID,
  codigo                             VARCHAR(40) NOT NULL,
  descripcion                        VARCHAR(300) NOT NULL,
  severidad                          VARCHAR(10) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  generada_en                        TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_alerta_temprana PRIMARY KEY (id),
  CONSTRAINT ck_alerta_temprana_codigo CHECK (codigo IN ('MULTIPLES_GRUPOS_EN_MORA', 'NO_ABRE_MENSAJES', 'PAGA_CADA_VEZ_MAS_TARDE')),
  CONSTRAINT ck_alerta_temprana_severidad CHECK (severidad IN ('ALTA', 'BAJA', 'CRITICA', 'MEDIA')),
  CONSTRAINT ck_alerta_temprana_estado CHECK (estado IN ('ABIERTA', 'ATENDIDA', 'DESCARTADA'))
);

COMMENT ON TABLE garantia.alerta_temprana IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.alerta_temprana.id IS 'PK';
COMMENT ON COLUMN garantia.alerta_temprana.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.alerta_temprana.grupo_id IS 'FK, NULL';
COMMENT ON COLUMN garantia.alerta_temprana.codigo IS 'CK';
COMMENT ON COLUMN garantia.alerta_temprana.severidad IS 'CK';
COMMENT ON COLUMN garantia.alerta_temprana.estado IS 'CK';
