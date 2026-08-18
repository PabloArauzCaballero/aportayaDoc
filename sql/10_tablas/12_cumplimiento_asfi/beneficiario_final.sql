-- beneficiario_final · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: BeneficiarioFinal
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.beneficiario_final (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  nombre                             VARCHAR(150) NOT NULL,
  documento                          VARCHAR(30) NOT NULL,
  porcentaje_participacion           NUMERIC(5,2) NOT NULL,
  tipo_control                       VARCHAR(30) NOT NULL,
  verificado                         BOOLEAN DEFAULT FALSE NOT NULL,
  registrado_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_beneficiario_final PRIMARY KEY (id),
  CONSTRAINT ck_beneficiario_final_tipo_control CHECK (tipo_control IN ('CONTROL_EFECTIVO', 'OTRO', 'PARTICIPACION_ACCIONARIA', 'REPRESENTACION_LEGAL'))
);

COMMENT ON TABLE cumplimiento.beneficiario_final IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.beneficiario_final.id IS 'PK';
COMMENT ON COLUMN cumplimiento.beneficiario_final.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.beneficiario_final.tipo_control IS 'CK';
