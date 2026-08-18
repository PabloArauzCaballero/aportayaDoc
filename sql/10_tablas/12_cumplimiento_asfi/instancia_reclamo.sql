-- instancia_reclamo · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: InstanciaReclamo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.instancia_reclamo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  reclamo_id                         UUID NOT NULL,
  instancia                          VARCHAR(15) NOT NULL,
  fecha_elevacion                    TIMESTAMPTZ NOT NULL,
  numero_expediente                  VARCHAR(60),
  estado                             VARCHAR(15) NOT NULL,
  resolucion                         TEXT,
  fecha_resolucion                   TIMESTAMPTZ,
  monto_resarcido                    NUMERIC(14,2),
  CONSTRAINT pk_instancia_reclamo PRIMARY KEY (id),
  CONSTRAINT ck_instancia_reclamo_instancia CHECK (instancia IN ('ARBITRAJE', 'DEFENSORIA', 'ENTIDAD', 'JUDICIAL', 'REGULADOR')),
  CONSTRAINT ck_instancia_reclamo_estado CHECK (estado IN ('DESISTIDA', 'EN_TRAMITE', 'PRESENTADA', 'RESUELTA'))
);

COMMENT ON TABLE cumplimiento.instancia_reclamo IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.instancia_reclamo.id IS 'PK';
COMMENT ON COLUMN cumplimiento.instancia_reclamo.reclamo_id IS 'FK, IDX';
COMMENT ON COLUMN cumplimiento.instancia_reclamo.instancia IS 'CK';
COMMENT ON COLUMN cumplimiento.instancia_reclamo.numero_expediente IS 'NULL';
COMMENT ON COLUMN cumplimiento.instancia_reclamo.estado IS 'CK';
COMMENT ON COLUMN cumplimiento.instancia_reclamo.resolucion IS 'NULL';
COMMENT ON COLUMN cumplimiento.instancia_reclamo.fecha_resolucion IS 'NULL';
COMMENT ON COLUMN cumplimiento.instancia_reclamo.monto_resarcido IS 'NULL';
