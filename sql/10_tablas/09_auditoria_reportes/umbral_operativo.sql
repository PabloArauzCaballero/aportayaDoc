-- umbral_operativo · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: UmbralOperativo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS catalogo.umbral_operativo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  concepto                           VARCHAR(30) NOT NULL,
  nivel_kyc_requerido                VARCHAR(15) NOT NULL,
  monto_maximo                       NUMERIC(16,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  vigente_desde                      DATE NOT NULL,
  CONSTRAINT pk_umbral_operativo PRIMARY KEY (id),
  CONSTRAINT ck_umbral_operativo_nivel_kyc_requerido CHECK (nivel_kyc_requerido IN ('BASICO', 'COMPLETO', 'INTERMEDIO', 'NINGUNO'))
);

COMMENT ON TABLE catalogo.umbral_operativo IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN catalogo.umbral_operativo.id IS 'PK';
COMMENT ON COLUMN catalogo.umbral_operativo.concepto IS 'UQ+nivel_kyc_requerido';
COMMENT ON COLUMN catalogo.umbral_operativo.nivel_kyc_requerido IS 'CK';
