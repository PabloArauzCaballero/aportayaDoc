-- accion_cobranza · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: AccionCobranza
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.accion_cobranza (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  gestion_id                         UUID NOT NULL,
  notificacion_id                    UUID,
  etapa                              VARCHAR(20) NOT NULL,
  tipo                               VARCHAR(30) NOT NULL,
  canal                              VARCHAR(20) NOT NULL,
  resultado                          VARCHAR(30) NOT NULL,
  nota_gestor                        VARCHAR(500),
  costo                              NUMERIC(10,2) NOT NULL,
  ejecutada_por                      UUID,
  ejecutada_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_accion_cobranza PRIMARY KEY (id),
  CONSTRAINT ck_accion_cobranza_etapa CHECK (etapa IN ('ADMINISTRATIVA', 'CASTIGO', 'JUDICIAL', 'PREJUDICIAL', 'PREVENTIVA', 'TEMPRANA')),
  CONSTRAINT ck_accion_cobranza_tipo CHECK (tipo IN ('AVISO_A_AVALISTA', 'CARTA_FORMAL', 'LLAMADA', 'MENSAJE_DIRECTO', 'RECORDATORIO_AUTOMATICO', 'ULTIMATUM', 'VISITA')),
  CONSTRAINT ck_accion_cobranza_resultado CHECK (resultado IN ('CONTACTADO_SE_COMPROMETE', 'CONTACTADO_SE_NIEGA', 'DISPUTA_LA_DEUDA', 'PAGO_INMEDIATO', 'SIN_RESPUESTA', 'SOLICITA_PLAN', 'TELEFONO_ERRONEO'))
);

COMMENT ON TABLE garantia.accion_cobranza IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.accion_cobranza.id IS 'PK';
COMMENT ON COLUMN garantia.accion_cobranza.gestion_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.accion_cobranza.notificacion_id IS 'FK, NULL, M5';
COMMENT ON COLUMN garantia.accion_cobranza.etapa IS 'CK';
COMMENT ON COLUMN garantia.accion_cobranza.tipo IS 'CK';
COMMENT ON COLUMN garantia.accion_cobranza.resultado IS 'CK';
COMMENT ON COLUMN garantia.accion_cobranza.nota_gestor IS 'NULL';
COMMENT ON COLUMN garantia.accion_cobranza.ejecutada_por IS 'FK, NULL';
COMMENT ON COLUMN garantia.accion_cobranza.ejecutada_en IS 'IDX';
