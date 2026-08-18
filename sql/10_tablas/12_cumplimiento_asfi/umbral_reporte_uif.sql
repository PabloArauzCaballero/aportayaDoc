-- umbral_reporte_uif · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: UmbralReporteUif
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS catalogo.umbral_reporte_uif (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  formulario                         VARCHAR(10) NOT NULL,
  inciso                             VARCHAR(4) NOT NULL,
  concepto_operacion                 VARCHAR(30) NOT NULL,
  es_acumulado                       BOOLEAN DEFAULT FALSE NOT NULL,
  umbral_usd                         NUMERIC(16,2) NOT NULL,
  ventana_dias_calendario            SMALLINT,
  exige_declaracion_origen_destino   BOOLEAN DEFAULT FALSE NOT NULL,
  reinicia_tras_superar              BOOLEAN DEFAULT FALSE NOT NULL,
  base_normativa                     VARCHAR(160) NOT NULL,
  vigente_desde                      DATE NOT NULL,
  vigente_hasta                      DATE,
  activo                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_umbral_reporte_uif PRIMARY KEY (id),
  CONSTRAINT ck_umbral_reporte_uif_formulario CHECK (formulario IN ('PCC-01', 'ROG-01', 'ROG-02', 'ROG-03', 'ROG-04')),
  CONSTRAINT ck_umbral_reporte_uif_concepto_operacion CHECK (concepto_operacion IN ('ACTIVO_VIRTUAL', 'CAMBIO_MONEDA', 'CARGA_BILLETERA', 'EFECTIVO', 'ELECTRONICA', 'GIRO', 'REMESA', 'RETIRO_BILLETERA', 'TRANSFERENCIA_BILLETERA'))
);

COMMENT ON TABLE catalogo.umbral_reporte_uif IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN catalogo.umbral_reporte_uif.id IS 'PK';
COMMENT ON COLUMN catalogo.umbral_reporte_uif.formulario IS 'CK, UQ+concepto_operacion+es_acumulado+vigente_desde';
COMMENT ON COLUMN catalogo.umbral_reporte_uif.concepto_operacion IS 'CK';
COMMENT ON COLUMN catalogo.umbral_reporte_uif.ventana_dias_calendario IS 'NULL';
COMMENT ON COLUMN catalogo.umbral_reporte_uif.vigente_hasta IS 'NULL';
COMMENT ON COLUMN catalogo.umbral_reporte_uif.activo IS 'IDX';
