-- validacion_pre_entrega · módulo 04 — Entregas de Fondo
-- clase de dominio: ValidacionPreEntrega
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS entregas.validacion_pre_entrega (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  entrega_id                         UUID NOT NULL,
  regla_id                           UUID NOT NULL,
  resultado                          VARCHAR(15) NOT NULL,
  valor_esperado                     VARCHAR(80),
  valor_obtenido                     VARCHAR(80),
  es_bloqueante                      BOOLEAN DEFAULT FALSE NOT NULL,
  omitida_por                        UUID,
  justificacion_omision              VARCHAR(300),
  evaluada_en                        TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_validacion_pre_entrega PRIMARY KEY (id),
  CONSTRAINT ck_validacion_pre_entrega_resultado CHECK (resultado IN ('ADVERTENCIA', 'APROBADA', 'OMITIDA', 'RECHAZADA'))
);

COMMENT ON TABLE entregas.validacion_pre_entrega IS 'Módulo 04 — Entregas de Fondo. Que la bolsa llegue completa, a la persona correcta, una sola vez';
COMMENT ON COLUMN entregas.validacion_pre_entrega.id IS 'PK';
COMMENT ON COLUMN entregas.validacion_pre_entrega.entrega_id IS 'FK, IDX';
COMMENT ON COLUMN entregas.validacion_pre_entrega.regla_id IS 'FK';
COMMENT ON COLUMN entregas.validacion_pre_entrega.resultado IS 'CK';
COMMENT ON COLUMN entregas.validacion_pre_entrega.valor_esperado IS 'NULL';
COMMENT ON COLUMN entregas.validacion_pre_entrega.valor_obtenido IS 'NULL';
COMMENT ON COLUMN entregas.validacion_pre_entrega.omitida_por IS 'FK, NULL';
COMMENT ON COLUMN entregas.validacion_pre_entrega.justificacion_omision IS 'NULL';
