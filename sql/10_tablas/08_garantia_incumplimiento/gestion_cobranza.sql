-- gestion_cobranza · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: GestionCobranza
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.gestion_cobranza (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  registro_id                        UUID NOT NULL,
  estrategia_id                      UUID NOT NULL,
  gestor_asignado_id                 UUID,
  etapa_actual                       VARCHAR(20) NOT NULL,
  monto_en_gestion                   NUMERIC(14,2) DEFAULT 0 NOT NULL,
  intentos_contacto                  SMALLINT DEFAULT 0 NOT NULL,
  ultimo_contacto_en                 TIMESTAMPTZ,
  proxima_accion_en                  TIMESTAMPTZ NOT NULL,
  estado                             VARCHAR(25) NOT NULL,
  abierta_en                         TIMESTAMPTZ DEFAULT now() NOT NULL,
  cerrada_en                         TIMESTAMPTZ,
  CONSTRAINT pk_gestion_cobranza PRIMARY KEY (id),
  CONSTRAINT ck_gestion_cobranza_etapa_actual CHECK (etapa_actual IN ('ADMINISTRATIVA', 'CASTIGO', 'JUDICIAL', 'PREJUDICIAL', 'PREVENTIVA', 'TEMPRANA')),
  CONSTRAINT ck_gestion_cobranza_estado CHECK (estado IN ('ACTIVA', 'CERRADA_EXITOSA', 'CERRADA_SIN_EXITO', 'PAUSADA'))
);

COMMENT ON TABLE garantia.gestion_cobranza IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.gestion_cobranza.id IS 'PK';
COMMENT ON COLUMN garantia.gestion_cobranza.registro_id IS 'FK, UQ';
COMMENT ON COLUMN garantia.gestion_cobranza.estrategia_id IS 'FK';
COMMENT ON COLUMN garantia.gestion_cobranza.gestor_asignado_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN garantia.gestion_cobranza.etapa_actual IS 'CK, IDX';
COMMENT ON COLUMN garantia.gestion_cobranza.ultimo_contacto_en IS 'NULL';
COMMENT ON COLUMN garantia.gestion_cobranza.proxima_accion_en IS 'IDX';
COMMENT ON COLUMN garantia.gestion_cobranza.estado IS 'CK, IDX';
COMMENT ON COLUMN garantia.gestion_cobranza.cerrada_en IS 'NULL';
