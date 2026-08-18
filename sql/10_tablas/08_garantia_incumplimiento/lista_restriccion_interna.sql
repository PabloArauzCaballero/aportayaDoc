-- lista_restriccion_interna · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: ListaRestriccionInterna
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.lista_restriccion_interna (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  registro_origen_id                 UUID,
  motivo                             VARCHAR(300) NOT NULL,
  nivel_restriccion                  VARCHAR(15) NOT NULL,
  monto_adeudado                     NUMERIC(14,2) DEFAULT 0 NOT NULL,
  incluido_en                        TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  retirado_en                        TIMESTAMPTZ,
  retirado_por                       UUID,
  motivo_retiro                      VARCHAR(300),
  CONSTRAINT pk_lista_restriccion_interna PRIMARY KEY (id),
  CONSTRAINT ck_lista_restriccion_interna_nivel_restriccion CHECK (nivel_restriccion IN ('LIMITADO', 'OBSERVACION', 'VETADO'))
);

COMMENT ON TABLE garantia.lista_restriccion_interna IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.lista_restriccion_interna.id IS 'PK';
COMMENT ON COLUMN garantia.lista_restriccion_interna.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.lista_restriccion_interna.registro_origen_id IS 'FK, NULL';
COMMENT ON COLUMN garantia.lista_restriccion_interna.nivel_restriccion IS 'CK';
COMMENT ON COLUMN garantia.lista_restriccion_interna.vigente_hasta IS 'NULL';
COMMENT ON COLUMN garantia.lista_restriccion_interna.retirado_en IS 'NULL';
COMMENT ON COLUMN garantia.lista_restriccion_interna.retirado_por IS 'FK, NULL';
COMMENT ON COLUMN garantia.lista_restriccion_interna.motivo_retiro IS 'NULL';
