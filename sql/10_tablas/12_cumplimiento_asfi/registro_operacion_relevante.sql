-- registro_operacion_relevante · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: RegistroOperacionRelevante
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.registro_operacion_relevante (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  transaccion_id                     UUID NOT NULL,
  umbral_reporte_id                  UUID NOT NULL,
  operacion_inicio_ventana_id        UUID,
  declaracion_origen_fondos_id       UUID,
  reporte_regulatorio_id             UUID,
  formulario                         VARCHAR(10) NOT NULL,
  concepto_operacion                 VARCHAR(30) NOT NULL,
  es_acumulada                       BOOLEAN DEFAULT FALSE NOT NULL,
  ventana_desde                      DATE,
  ventana_hasta                      DATE,
  monto                              NUMERIC(16,2) NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  monto_acumulado_ventana            NUMERIC(16,2) DEFAULT 0 NOT NULL,
  tipo_cambio_aplicado               NUMERIC(12,6) NOT NULL,
  monto_equivalente_usd              NUMERIC(16,2) DEFAULT 0 NOT NULL,
  umbral_aplicado_usd                NUMERIC(16,2) NOT NULL,
  origen_declarado                   VARCHAR(300),
  destino_declarado                  VARCHAR(300),
  exento                             BOOLEAN DEFAULT FALSE NOT NULL,
  motivo_exencion                    VARCHAR(120),
  periodo_remision                   CHAR(7) NOT NULL,
  fecha_operacion                    TIMESTAMPTZ NOT NULL,
  registrada_en                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_registro_operacion_relevante PRIMARY KEY (id),
  CONSTRAINT ck_registro_operacion_relevante_formulario CHECK (formulario IN ('PCC-01', 'ROG-01', 'ROG-02', 'ROG-03', 'ROG-04')),
  CONSTRAINT ck_registro_operacion_relevante_concepto_operacion CHECK (concepto_operacion IN ('ACTIVO_VIRTUAL', 'CAMBIO_MONEDA', 'CARGA_BILLETERA', 'EFECTIVO', 'ELECTRONICA', 'GIRO', 'REMESA', 'RETIRO_BILLETERA', 'TRANSFERENCIA_BILLETERA'))
);

COMMENT ON TABLE cumplimiento.registro_operacion_relevante IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. [append-only] Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.id IS 'PK';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.transaccion_id IS 'FK, IDX, M10';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.umbral_reporte_id IS 'FK, IDX';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.operacion_inicio_ventana_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.declaracion_origen_fondos_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.reporte_regulatorio_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.formulario IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.concepto_operacion IS 'CK';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.ventana_desde IS 'NULL';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.ventana_hasta IS 'NULL';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.monto_equivalente_usd IS 'IDX';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.origen_declarado IS 'NULL';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.destino_declarado IS 'NULL';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.motivo_exencion IS 'NULL';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.periodo_remision IS 'IDX';
COMMENT ON COLUMN cumplimiento.registro_operacion_relevante.fecha_operacion IS 'IDX';
