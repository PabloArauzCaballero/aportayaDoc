-- referencia_personal · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: ReferenciaPersonal
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.referencia_personal (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  nombre                             VARCHAR(120) NOT NULL,
  telefono                           VARCHAR(20) NOT NULL,
  relacion                           VARCHAR(20) NOT NULL,
  verificada                         BOOLEAN DEFAULT FALSE NOT NULL,
  verificada_en                      TIMESTAMPTZ,
  acepta_ser_avalista                BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_referencia_personal PRIMARY KEY (id),
  CONSTRAINT ck_referencia_personal_relacion CHECK (relacion IN ('FAMILIAR', 'LABORAL', 'VECINAL'))
);

COMMENT ON TABLE identidad.referencia_personal IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.referencia_personal.id IS 'PK';
COMMENT ON COLUMN identidad.referencia_personal.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN identidad.referencia_personal.relacion IS 'CK';
COMMENT ON COLUMN identidad.referencia_personal.verificada_en IS 'NULL';
