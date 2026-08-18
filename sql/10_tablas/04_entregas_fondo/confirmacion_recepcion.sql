-- confirmacion_recepcion · módulo 04 — Entregas de Fondo
-- clase de dominio: ConfirmacionRecepcion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS entregas.confirmacion_recepcion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  entrega_id                         UUID NOT NULL,
  estado                             VARCHAR(30) NOT NULL,
  monto_confirmado                   NUMERIC(14,2),
  token_confirmacion_id              UUID,
  confirmada_en                      TIMESTAMPTZ,
  plazo_limite                       TIMESTAMPTZ NOT NULL,
  autoconfirmada_por_vencimiento     BOOLEAN DEFAULT FALSE NOT NULL,
  ip_confirmacion                    INET,
  comentario                         VARCHAR(300),
  CONSTRAINT pk_confirmacion_recepcion PRIMARY KEY (id),
  CONSTRAINT ck_confirmacion_recepcion_estado CHECK (estado IN ('CONFIRMADA_AUTOMATICAMENTE', 'CONFIRMADA_POR_BENEFICIARIO', 'OBJETADA', 'PENDIENTE', 'VENCIDA'))
);

COMMENT ON TABLE entregas.confirmacion_recepcion IS 'Módulo 04 — Entregas de Fondo. Que la bolsa llegue completa, a la persona correcta, una sola vez';
COMMENT ON COLUMN entregas.confirmacion_recepcion.id IS 'PK';
COMMENT ON COLUMN entregas.confirmacion_recepcion.entrega_id IS 'FK, UQ';
COMMENT ON COLUMN entregas.confirmacion_recepcion.estado IS 'CK, IDX';
COMMENT ON COLUMN entregas.confirmacion_recepcion.monto_confirmado IS 'NULL';
COMMENT ON COLUMN entregas.confirmacion_recepcion.token_confirmacion_id IS 'FK, NULL, M1';
COMMENT ON COLUMN entregas.confirmacion_recepcion.confirmada_en IS 'NULL';
COMMENT ON COLUMN entregas.confirmacion_recepcion.plazo_limite IS 'IDX';
COMMENT ON COLUMN entregas.confirmacion_recepcion.ip_confirmacion IS 'NULL';
COMMENT ON COLUMN entregas.confirmacion_recepcion.comentario IS 'NULL';
