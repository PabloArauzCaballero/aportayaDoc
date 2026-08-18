-- factura_publicidad · módulo 14 — Publicidad y Campañas
-- clase de dominio: FacturaPublicidad
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS publicidad.factura_publicidad (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cuenta_publicitaria_id             UUID NOT NULL,
  periodo                            VARCHAR(7) NOT NULL,
  monto_total                        NUMERIC(14,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  factura_electronica_id             UUID,
  cuenta_por_cobrar_id               UUID,
  estado                             VARCHAR(15) NOT NULL,
  generada_en                        TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_factura_publicidad PRIMARY KEY (id),
  CONSTRAINT ck_factura_publicidad_monto_total CHECK (monto_total > 0),
  CONSTRAINT ck_factura_publicidad_estado CHECK (estado IN ('ANULADA', 'COBRADA', 'FACTURADA', 'GENERADA'))
);

COMMENT ON TABLE publicidad.factura_publicidad IS 'Módulo 14 — Publicidad y Campañas. [append-only] Que un partner se anuncie dentro de la app sin inventar un segundo cobro';
COMMENT ON COLUMN publicidad.factura_publicidad.id IS 'PK';
COMMENT ON COLUMN publicidad.factura_publicidad.cuenta_publicitaria_id IS 'FK, IDX';
COMMENT ON COLUMN publicidad.factura_publicidad.monto_total IS 'CK: > 0';
COMMENT ON COLUMN publicidad.factura_publicidad.factura_electronica_id IS 'FK, NULL, M11';
COMMENT ON COLUMN publicidad.factura_publicidad.cuenta_por_cobrar_id IS 'FK, NULL, M13';
COMMENT ON COLUMN publicidad.factura_publicidad.estado IS 'CK, IDX';
