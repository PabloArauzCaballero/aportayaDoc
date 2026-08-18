-- deduccion_entrega · módulo 04 — Entregas de Fondo
-- clase de dominio: DeduccionEntrega
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS entregas.deduccion_entrega (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  entrega_id                         UUID NOT NULL,
  tipo                               VARCHAR(35) NOT NULL,
  descripcion                        VARCHAR(200) NOT NULL,
  monto                              NUMERIC(14,2) NOT NULL,
  referencia_origen_id               UUID,
  es_obligatoria                     BOOLEAN DEFAULT FALSE NOT NULL,
  aplicada_en                        TIMESTAMPTZ DEFAULT now() NOT NULL,
  revertida_en                       TIMESTAMPTZ,
  CONSTRAINT pk_deduccion_entrega PRIMARY KEY (id),
  CONSTRAINT ck_deduccion_entrega_tipo CHECK (tipo IN ('AJUSTE_ACORDADO', 'APORTE_PROPIO_DEL_PERIODO', 'COMISION_PLATAFORMA', 'COSTO_TRANSFERENCIA', 'DEUDA_VENCIDA_PROPIA', 'RECARGO_MORA_PROPIO', 'REPOSICION_FONDO_GARANTIA', 'RETENCION_IMPUESTO')),
  CONSTRAINT ck_deduccion_entrega_monto CHECK (monto > 0)
);

COMMENT ON TABLE entregas.deduccion_entrega IS 'Módulo 04 — Entregas de Fondo. Que la bolsa llegue completa, a la persona correcta, una sola vez';
COMMENT ON COLUMN entregas.deduccion_entrega.id IS 'PK';
COMMENT ON COLUMN entregas.deduccion_entrega.entrega_id IS 'FK, IDX';
COMMENT ON COLUMN entregas.deduccion_entrega.tipo IS 'CK';
COMMENT ON COLUMN entregas.deduccion_entrega.monto IS 'CK: > 0';
COMMENT ON COLUMN entregas.deduccion_entrega.referencia_origen_id IS 'NULL, polimorfica';
COMMENT ON COLUMN entregas.deduccion_entrega.revertida_en IS 'NULL';
