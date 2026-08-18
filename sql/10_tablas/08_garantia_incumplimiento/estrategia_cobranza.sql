-- estrategia_cobranza · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: EstrategiaCobranza
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.estrategia_cobranza (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  etapa                              VARCHAR(20) NOT NULL,
  dias_mora_desde                    SMALLINT NOT NULL,
  dias_mora_hasta                    SMALLINT NOT NULL,
  canales_permitidos                 VARCHAR(120) NOT NULL,
  frecuencia_dias                    SMALLINT NOT NULL,
  max_contactos_por_semana           SMALLINT NOT NULL,
  plantilla_notificacion_codigo      VARCHAR(50),
  requiere_gestor_humano             BOOLEAN DEFAULT FALSE NOT NULL,
  permite_quita                      BOOLEAN DEFAULT FALSE NOT NULL,
  quita_maxima_porcentaje            NUMERIC(5,2) NOT NULL,
  siguiente_etapa                    VARCHAR(20),
  CONSTRAINT pk_estrategia_cobranza PRIMARY KEY (id),
  CONSTRAINT ck_estrategia_cobranza_etapa CHECK (etapa IN ('ADMINISTRATIVA', 'CASTIGO', 'JUDICIAL', 'PREJUDICIAL', 'PREVENTIVA', 'TEMPRANA'))
);

COMMENT ON TABLE garantia.estrategia_cobranza IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.estrategia_cobranza.id IS 'PK';
COMMENT ON COLUMN garantia.estrategia_cobranza.etapa IS 'CK, UQ+dias_mora_desde';
COMMENT ON COLUMN garantia.estrategia_cobranza.plantilla_notificacion_codigo IS 'NULL, M5';
COMMENT ON COLUMN garantia.estrategia_cobranza.siguiente_etapa IS 'NULL';
