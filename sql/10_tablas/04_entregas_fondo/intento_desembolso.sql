-- intento_desembolso · módulo 04 — Entregas de Fondo
-- clase de dominio: IntentoDesembolso
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS entregas.intento_desembolso (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  orden_desembolso_id                UUID NOT NULL,
  numero_intento                     SMALLINT NOT NULL,
  iniciado_en                        TIMESTAMPTZ NOT NULL,
  finalizado_en                      TIMESTAMPTZ,
  resultado                          VARCHAR(20) NOT NULL,
  codigo_error                       VARCHAR(40),
  mensaje_proveedor                  VARCHAR(255),
  reintentable_en                    TIMESTAMPTZ,
  CONSTRAINT pk_intento_desembolso PRIMARY KEY (id),
  CONSTRAINT ck_intento_desembolso_resultado CHECK (resultado IN ('EXITOSO', 'FALLIDO', 'PENDIENTE', 'TIMEOUT'))
);

COMMENT ON TABLE entregas.intento_desembolso IS 'Módulo 04 — Entregas de Fondo. Que la bolsa llegue completa, a la persona correcta, una sola vez';
COMMENT ON COLUMN entregas.intento_desembolso.id IS 'PK';
COMMENT ON COLUMN entregas.intento_desembolso.orden_desembolso_id IS 'FK, IDX';
COMMENT ON COLUMN entregas.intento_desembolso.finalizado_en IS 'NULL';
COMMENT ON COLUMN entregas.intento_desembolso.resultado IS 'CK';
COMMENT ON COLUMN entregas.intento_desembolso.codigo_error IS 'NULL';
COMMENT ON COLUMN entregas.intento_desembolso.mensaje_proveedor IS 'NULL';
COMMENT ON COLUMN entregas.intento_desembolso.reintentable_en IS 'NULL';
