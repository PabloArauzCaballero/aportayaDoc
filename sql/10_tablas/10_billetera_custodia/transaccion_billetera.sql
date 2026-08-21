-- transaccion_billetera · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: TransaccionBilletera
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.transaccion_billetera (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  secuencia                          BIGSERIAL NOT NULL,
  tipo                               VARCHAR(30) NOT NULL,
  estado                             VARCHAR(20) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  monto_total                        NUMERIC(16,2) DEFAULT 0 NOT NULL,
  grupo_id                           UUID,
  asiento_contable_id                UUID,
  sesion_id                          UUID,
  dispositivo_id                     UUID,
  iniciada_por                       UUID,
  origen_tipo                        VARCHAR(30) NOT NULL,
  origen_id                          UUID NOT NULL,
  canal                              VARCHAR(15) NOT NULL,
  ip_origen                          INET,
  clave_idempotencia                 VARCHAR(100) NOT NULL,
  hash_registro                      VARCHAR(64) NOT NULL,
  hash_anterior                      VARCHAR(64),
  ocurrida_en                        TIMESTAMPTZ DEFAULT now() NOT NULL,
  registrada_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_transaccion_billetera PRIMARY KEY (id),
  CONSTRAINT ck_transaccion_billetera_tipo CHECK (tipo IN ('AJUSTE_OPERATIVO', 'APORTE_A_GRUPO', 'COBERTURA_GARANTIA', 'COBRO_COMISION', 'COBRO_IMPUESTO', 'DEVOLUCION', 'ENTREGA_DE_FONDO', 'RECARGA', 'REPOSICION_GARANTIA', 'RETIRO', 'REVERSO', 'TRANSFERENCIA_P2P')),
  CONSTRAINT ck_transaccion_billetera_estado CHECK (estado IN ('APLICADA', 'AUTORIZADA', 'EN_REVISION_FRAUDE', 'INICIADA', 'RECHAZADA', 'REVERSADA')),
  CONSTRAINT ck_transaccion_billetera_monto_total CHECK (monto_total > 0),
  CONSTRAINT ck_transaccion_billetera_origen_tipo CHECK (origen_tipo IN ('AJUSTE', 'COBERTURA_INCUMPLIMIENTO', 'DEVENGO_COMISION', 'ENTREGA_FONDO', 'OBLIGACION_APORTE', 'ORDEN_RECARGA', 'ORDEN_RETIRO', 'TRANSFERENCIA_P2P')),
  CONSTRAINT ck_transaccion_billetera_canal CHECK (canal IN ('API', 'APP', 'BATCH', 'WEB'))
);

COMMENT ON TABLE nucleo_financiero.transaccion_billetera IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. [append-only] El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.secuencia IS 'UQ';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.tipo IS 'CK, IDX';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.estado IS 'CK, IDX';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.monto_total IS 'CK: > 0';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.grupo_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.asiento_contable_id IS 'FK, NULL, M3';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.sesion_id IS 'FK, NULL, M1';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.dispositivo_id IS 'FK, NULL, M1';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.iniciada_por IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.origen_tipo IS 'CK';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.origen_id IS 'IDX, polimorfica';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.canal IS 'CK';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.ip_origen IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.hash_anterior IS 'NULL';
COMMENT ON COLUMN nucleo_financiero.transaccion_billetera.ocurrida_en IS 'IDX';
