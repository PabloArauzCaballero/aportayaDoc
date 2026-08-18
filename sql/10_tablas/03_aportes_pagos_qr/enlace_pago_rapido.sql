-- enlace_pago_rapido · módulo 03 — Aportes, Pagos QR y Conciliación
-- clase de dominio: EnlacePagoRapido
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS aportes.enlace_pago_rapido (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  orden_cobro_id                     UUID NOT NULL,
  token_id                           UUID NOT NULL,
  url_corta                          VARCHAR(60) NOT NULL,
  clicks                             SMALLINT DEFAULT 0 NOT NULL,
  expira_en                          TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_enlace_pago_rapido PRIMARY KEY (id)
);

COMMENT ON TABLE aportes.enlace_pago_rapido IS 'Módulo 03 — Aportes, Pagos QR y Conciliación. Que "pagué" signifique "el banco lo confirmó"';
COMMENT ON COLUMN aportes.enlace_pago_rapido.id IS 'PK';
COMMENT ON COLUMN aportes.enlace_pago_rapido.orden_cobro_id IS 'FK, UQ';
COMMENT ON COLUMN aportes.enlace_pago_rapido.token_id IS 'FK, UQ, M1';
COMMENT ON COLUMN aportes.enlace_pago_rapido.url_corta IS 'UQ';
