-- solicitud_cierre_billetera · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: SolicitudCierreBilletera
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS nucleo_financiero.solicitud_cierre_billetera (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cuenta_billetera_id                UUID NOT NULL,
  orden_retiro_id                    UUID,
  aprobada_por                       UUID,
  motivo                             VARCHAR(200) NOT NULL,
  saldo_al_solicitar                 NUMERIC(16,2) DEFAULT 0 NOT NULL,
  destino_saldo                      VARCHAR(20) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  solicitada_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  ejecutada_en                       TIMESTAMPTZ,
  CONSTRAINT pk_solicitud_cierre_billetera PRIMARY KEY (id),
  CONSTRAINT ck_solicitud_cierre_billetera_destino_saldo CHECK (destino_saldo IN ('RETIRO', 'TRANSFERENCIA')),
  CONSTRAINT ck_solicitud_cierre_billetera_estado CHECK (estado IN ('APROBADA', 'EJECUTADA', 'EN_VALIDACION', 'RECHAZADA', 'SOLICITADA'))
);

COMMENT ON TABLE nucleo_financiero.solicitud_cierre_billetera IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN nucleo_financiero.solicitud_cierre_billetera.id IS 'PK';
COMMENT ON COLUMN nucleo_financiero.solicitud_cierre_billetera.cuenta_billetera_id IS 'FK, UQ';
COMMENT ON COLUMN nucleo_financiero.solicitud_cierre_billetera.orden_retiro_id IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.solicitud_cierre_billetera.aprobada_por IS 'FK, NULL';
COMMENT ON COLUMN nucleo_financiero.solicitud_cierre_billetera.destino_saldo IS 'CK';
COMMENT ON COLUMN nucleo_financiero.solicitud_cierre_billetera.estado IS 'CK, IDX';
COMMENT ON COLUMN nucleo_financiero.solicitud_cierre_billetera.ejecutada_en IS 'NULL';
