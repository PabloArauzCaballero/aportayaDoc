-- apelacion_sancion · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: ApelacionSancion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.apelacion_sancion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  sancion_id                         UUID NOT NULL,
  apelante_id                        UUID NOT NULL,
  resuelta_por                       UUID,
  argumento                          TEXT NOT NULL,
  evidencias                         JSONB NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  instancia                          VARCHAR(25) NOT NULL,
  resolucion                         VARCHAR(500),
  presentada_en                      TIMESTAMPTZ NOT NULL,
  fecha_limite_resolucion            TIMESTAMPTZ NOT NULL,
  resuelta_en                        TIMESTAMPTZ,
  CONSTRAINT pk_apelacion_sancion PRIMARY KEY (id),
  CONSTRAINT ck_apelacion_sancion_estado CHECK (estado IN ('ACEPTADA', 'ADMITIDA', 'DESISTIDA', 'EN_REVISION', 'PRESENTADA', 'RECHAZADA')),
  CONSTRAINT ck_apelacion_sancion_instancia CHECK (instancia IN ('COMITE_GRUPO', 'ORGANIZADOR', 'SOPORTE_PLATAFORMA'))
);

COMMENT ON TABLE garantia.apelacion_sancion IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.apelacion_sancion.id IS 'PK';
COMMENT ON COLUMN garantia.apelacion_sancion.sancion_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.apelacion_sancion.apelante_id IS 'FK';
COMMENT ON COLUMN garantia.apelacion_sancion.resuelta_por IS 'FK, NULL';
COMMENT ON COLUMN garantia.apelacion_sancion.estado IS 'CK';
COMMENT ON COLUMN garantia.apelacion_sancion.instancia IS 'CK';
COMMENT ON COLUMN garantia.apelacion_sancion.resolucion IS 'NULL';
COMMENT ON COLUMN garantia.apelacion_sancion.resuelta_en IS 'NULL';
