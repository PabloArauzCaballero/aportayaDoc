-- deuda_participante · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: DeudaParticipante
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.deuda_participante (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  participante_id                    UUID NOT NULL,
  grupo_id                           UUID NOT NULL,
  registro_id                        UUID NOT NULL,
  cobertura_id                       UUID,
  acreedor                           VARCHAR(20) NOT NULL,
  capital_original                   NUMERIC(14,2) NOT NULL,
  recargos_acumulados                NUMERIC(14,2) NOT NULL,
  total_abonado                      NUMERIC(14,2) DEFAULT 0 NOT NULL,
  saldo_actual                       NUMERIC(14,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  estado                             VARCHAR(20) NOT NULL,
  es_subrogada                       BOOLEAN DEFAULT FALSE NOT NULL,
  fecha_exigibilidad                 DATE NOT NULL,
  fecha_prescripcion                 DATE NOT NULL,
  dias_vencida                       SMALLINT NOT NULL,
  version                            INTEGER DEFAULT 0 NOT NULL,
  CONSTRAINT pk_deuda_participante PRIMARY KEY (id),
  CONSTRAINT ck_deuda_participante_acreedor CHECK (acreedor IN ('FONDO_GARANTIA', 'GRUPO', 'PLATAFORMA')),
  CONSTRAINT ck_deuda_participante_saldo_actual CHECK (saldo_actual >= 0),
  CONSTRAINT ck_deuda_participante_estado CHECK (estado IN ('CASTIGADA', 'CONDONADA', 'EN_MORA', 'EN_PLAN_DE_PAGO', 'JUDICIALIZADA', 'PAGADA', 'VIGENTE'))
);

COMMENT ON TABLE garantia.deuda_participante IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.deuda_participante.id IS 'PK';
COMMENT ON COLUMN garantia.deuda_participante.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.deuda_participante.participante_id IS 'FK';
COMMENT ON COLUMN garantia.deuda_participante.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.deuda_participante.registro_id IS 'FK, UQ';
COMMENT ON COLUMN garantia.deuda_participante.cobertura_id IS 'FK, NULL, UQ';
COMMENT ON COLUMN garantia.deuda_participante.acreedor IS 'CK';
COMMENT ON COLUMN garantia.deuda_participante.saldo_actual IS 'CK: >= 0, IDX';
COMMENT ON COLUMN garantia.deuda_participante.estado IS 'CK, IDX';
COMMENT ON COLUMN garantia.deuda_participante.fecha_prescripcion IS 'IDX';
