-- reclamo_cliente · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: ReclamoCliente
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.reclamo_cliente (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(20) NOT NULL,
  usuario_id                         UUID NOT NULL,
  punto_reclamo_id                   UUID NOT NULL,
  responsable_id                     UUID,
  ticket_soporte_id                  UUID,
  devolucion_comision_id             UUID,
  categoria                          VARCHAR(30) NOT NULL,
  producto                           VARCHAR(30) NOT NULL,
  monto_reclamado                    NUMERIC(14,2),
  descripcion                        TEXT NOT NULL,
  canal_ingreso                      VARCHAR(15) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  fecha_ingreso                      TIMESTAMPTZ DEFAULT now() NOT NULL,
  dias_habiles_plazo                 SMALLINT NOT NULL,
  plazo_respuesta                    TIMESTAMPTZ NOT NULL,
  plazo_prorrogado_hasta             TIMESTAMPTZ,
  prorroga_comunicada_al_cliente_en  TIMESTAMPTZ,
  prorroga_comunicada_al_organismo_en TIMESTAMPTZ,
  justificacion_prorroga             VARCHAR(400),
  fecha_respuesta                    TIMESTAMPTZ,
  resultado                          VARCHAR(15),
  respuesta                          TEXT,
  incluido_en_reporte_mensual        CHAR(7),
  conservar_hasta                    DATE NOT NULL,
  CONSTRAINT pk_reclamo_cliente PRIMARY KEY (id),
  CONSTRAINT ck_reclamo_cliente_categoria CHECK (categoria IN ('COMISION', 'DATOS_PERSONALES', 'GRUPO', 'OPERACION_NO_RECONOCIDA', 'SALDO', 'SERVICIO')),
  CONSTRAINT ck_reclamo_cliente_canal_ingreso CHECK (canal_ingreso IN ('APP', 'CORREO', 'PRESENCIAL', 'TELEFONO', 'WEB')),
  CONSTRAINT ck_reclamo_cliente_estado CHECK (estado IN ('CERRADO', 'ELEVADO', 'EN_ANALISIS', 'INGRESADO', 'RESPONDIDO')),
  CONSTRAINT ck_reclamo_cliente_resultado CHECK (resultado IN ('DESFAVORABLE', 'DESISTIDO', 'FAVORABLE', 'PARCIAL'))
);

COMMENT ON TABLE cumplimiento.reclamo_cliente IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.id IS 'PK';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.codigo IS 'UQ';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.usuario_id IS 'FK, IDX, M1';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.punto_reclamo_id IS 'FK';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.responsable_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.ticket_soporte_id IS 'FK, NULL, M9';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.devolucion_comision_id IS 'FK, NULL, M11';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.categoria IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.monto_reclamado IS 'NULL';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.canal_ingreso IS 'CK';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.estado IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.fecha_ingreso IS 'IDX';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.plazo_respuesta IS 'IDX';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.plazo_prorrogado_hasta IS 'NULL';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.prorroga_comunicada_al_cliente_en IS 'NULL';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.prorroga_comunicada_al_organismo_en IS 'NULL';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.justificacion_prorroga IS 'NULL';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.fecha_respuesta IS 'NULL';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.resultado IS 'CK, NULL';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.respuesta IS 'NULL';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.incluido_en_reporte_mensual IS 'NULL, IDX';
COMMENT ON COLUMN cumplimiento.reclamo_cliente.conservar_hasta IS 'IDX';
