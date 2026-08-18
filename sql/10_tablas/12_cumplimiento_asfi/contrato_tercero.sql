-- contrato_tercero · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: ContratoTercero
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.contrato_tercero (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  responsable_id                     UUID,
  razon_social                       VARCHAR(150) NOT NULL,
  nit                                VARCHAR(20),
  servicio_contratado                VARCHAR(160) NOT NULL,
  es_critico                         BOOLEAN DEFAULT FALSE NOT NULL,
  accede_a_datos_personales          BOOLEAN DEFAULT FALSE NOT NULL,
  pais_procesamiento                 CHAR(2) NOT NULL,
  nivel_riesgo                       VARCHAR(10) NOT NULL,
  evaluacion_riesgo_url              VARCHAR(255),
  clausula_confidencialidad          BOOLEAN DEFAULT FALSE NOT NULL,
  clausula_auditoria                 BOOLEAN DEFAULT FALSE NOT NULL,
  clausula_continuidad               BOOLEAN DEFAULT FALSE NOT NULL,
  acuerdo_nivel_servicio             JSONB NOT NULL,
  comunicado_al_organismo            BOOLEAN DEFAULT FALSE NOT NULL,
  vigente_desde                      DATE NOT NULL,
  vigente_hasta                      DATE,
  estado                             VARCHAR(15) NOT NULL,
  CONSTRAINT pk_contrato_tercero PRIMARY KEY (id),
  CONSTRAINT ck_contrato_tercero_nivel_riesgo CHECK (nivel_riesgo IN ('ALTO', 'BAJO', 'CRITICO', 'MEDIO')),
  CONSTRAINT ck_contrato_tercero_estado CHECK (estado IN ('EN_NEGOCIACION', 'SUSPENDIDO', 'TERMINADO', 'VIGENTE'))
);

COMMENT ON TABLE cumplimiento.contrato_tercero IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.contrato_tercero.id IS 'PK';
COMMENT ON COLUMN cumplimiento.contrato_tercero.responsable_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.contrato_tercero.nit IS 'NULL';
COMMENT ON COLUMN cumplimiento.contrato_tercero.es_critico IS 'IDX';
COMMENT ON COLUMN cumplimiento.contrato_tercero.accede_a_datos_personales IS 'IDX';
COMMENT ON COLUMN cumplimiento.contrato_tercero.nivel_riesgo IS 'CK';
COMMENT ON COLUMN cumplimiento.contrato_tercero.evaluacion_riesgo_url IS 'NULL';
COMMENT ON COLUMN cumplimiento.contrato_tercero.vigente_hasta IS 'NULL';
COMMENT ON COLUMN cumplimiento.contrato_tercero.estado IS 'CK, IDX';
