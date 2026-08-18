-- orden_desembolso · módulo 04 — Entregas de Fondo
-- clase de dominio: OrdenDesembolso
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS entregas.orden_desembolso (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  entrega_id                         UUID NOT NULL,
  proveedor_id                       UUID NOT NULL,
  cuenta_destino_id                  UUID NOT NULL,
  monto                              NUMERIC(14,2) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  estado                             VARCHAR(25) NOT NULL,
  referencia_proveedor               VARCHAR(80),
  glosa                              VARCHAR(140) NOT NULL,
  clave_idempotencia                 VARCHAR(80) NOT NULL,
  creada_en                          TIMESTAMPTZ DEFAULT now() NOT NULL,
  acreditada_en                      TIMESTAMPTZ,
  CONSTRAINT pk_orden_desembolso PRIMARY KEY (id),
  CONSTRAINT ck_orden_desembolso_estado CHECK (estado IN ('ACREDITADA', 'CANCELADA', 'CREADA', 'DEVUELTA_POR_BANCO', 'ENVIADA_A_PROVEEDOR', 'EN_PROCESO', 'RECHAZADA'))
);

COMMENT ON TABLE entregas.orden_desembolso IS 'Módulo 04 — Entregas de Fondo. Que la bolsa llegue completa, a la persona correcta, una sola vez';
COMMENT ON COLUMN entregas.orden_desembolso.id IS 'PK';
COMMENT ON COLUMN entregas.orden_desembolso.entrega_id IS 'FK, IDX';
COMMENT ON COLUMN entregas.orden_desembolso.proveedor_id IS 'FK, M3';
COMMENT ON COLUMN entregas.orden_desembolso.cuenta_destino_id IS 'FK';
COMMENT ON COLUMN entregas.orden_desembolso.estado IS 'CK, IDX';
COMMENT ON COLUMN entregas.orden_desembolso.referencia_proveedor IS 'UQ, NULL';
COMMENT ON COLUMN entregas.orden_desembolso.acreditada_en IS 'NULL';
