-- cuenta_publicitaria · módulo 14 — Publicidad y Campañas
-- clase de dominio: CuentaPublicitaria
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS publicidad.cuenta_publicitaria (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  anunciante_id                      UUID NOT NULL,
  limite_gasto_mensual               NUMERIC(14,2),
  moneda                             CHAR(3) NOT NULL,
  saldo_consumido_mes                NUMERIC(14,2) DEFAULT 0 NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  creada_en                          TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_cuenta_publicitaria PRIMARY KEY (id),
  CONSTRAINT ck_cuenta_publicitaria_estado CHECK (estado IN ('ACTIVA', 'CERRADA', 'SUSPENDIDA'))
);

COMMENT ON TABLE publicidad.cuenta_publicitaria IS 'Módulo 14 — Publicidad y Campañas. Que un partner se anuncie dentro de la app sin inventar un segundo cobro';
COMMENT ON COLUMN publicidad.cuenta_publicitaria.id IS 'PK';
COMMENT ON COLUMN publicidad.cuenta_publicitaria.anunciante_id IS 'FK';
COMMENT ON COLUMN publicidad.cuenta_publicitaria.limite_gasto_mensual IS 'NULL';
COMMENT ON COLUMN publicidad.cuenta_publicitaria.estado IS 'CK, IDX';
