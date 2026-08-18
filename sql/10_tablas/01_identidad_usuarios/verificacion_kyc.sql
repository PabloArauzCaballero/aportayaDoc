-- verificacion_kyc · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: VerificacionKYC
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.verificacion_kyc (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  documento_id                       UUID,
  nivel_solicitado                   VARCHAR(15) NOT NULL,
  estado                             VARCHAR(20) NOT NULL,
  proveedor                          VARCHAR(40),
  referencia_proveedor               VARCHAR(80),
  puntaje_biometrico                 NUMERIC(5,2),
  url_selfie                         VARCHAR(255),
  motivo_rechazo                     VARCHAR(160),
  revisada_por                       UUID,
  iniciada_en                        TIMESTAMPTZ NOT NULL,
  resuelta_en                        TIMESTAMPTZ,
  vigente_hasta                      DATE,
  CONSTRAINT pk_verificacion_kyc PRIMARY KEY (id),
  CONSTRAINT ck_verificacion_kyc_nivel_solicitado CHECK (nivel_solicitado IN ('AVANZADO', 'BASICO', 'INTERMEDIO', 'NINGUNO')),
  CONSTRAINT ck_verificacion_kyc_estado CHECK (estado IN ('APROBADA', 'EN_REVISION', 'NO_INICIADA', 'PENDIENTE', 'RECHAZADA', 'VENCIDA'))
);

COMMENT ON TABLE identidad.verificacion_kyc IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.verificacion_kyc.id IS 'PK';
COMMENT ON COLUMN identidad.verificacion_kyc.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN identidad.verificacion_kyc.documento_id IS 'FK, NULL';
COMMENT ON COLUMN identidad.verificacion_kyc.nivel_solicitado IS 'CK';
COMMENT ON COLUMN identidad.verificacion_kyc.estado IS 'CK';
COMMENT ON COLUMN identidad.verificacion_kyc.proveedor IS 'NULL';
COMMENT ON COLUMN identidad.verificacion_kyc.referencia_proveedor IS 'NULL';
COMMENT ON COLUMN identidad.verificacion_kyc.puntaje_biometrico IS 'NULL';
COMMENT ON COLUMN identidad.verificacion_kyc.url_selfie IS 'NULL';
COMMENT ON COLUMN identidad.verificacion_kyc.motivo_rechazo IS 'NULL';
COMMENT ON COLUMN identidad.verificacion_kyc.revisada_por IS 'FK, NULL';
COMMENT ON COLUMN identidad.verificacion_kyc.resuelta_en IS 'NULL';
COMMENT ON COLUMN identidad.verificacion_kyc.vigente_hasta IS 'NULL';
