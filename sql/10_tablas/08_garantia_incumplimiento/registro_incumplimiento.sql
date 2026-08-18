-- registro_incumplimiento · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: RegistroIncumplimiento
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.registro_incumplimiento (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo_expediente                  VARCHAR(20) NOT NULL,
  usuario_id                         UUID NOT NULL,
  participante_id                    UUID NOT NULL,
  grupo_id                           UUID NOT NULL,
  periodo_id                         UUID,
  cupo_id                            UUID,
  obligacion_id                      UUID,
  entrega_afectada_id                UUID,
  responsable_gestion                UUID,
  tipo                               VARCHAR(40) NOT NULL,
  severidad                          VARCHAR(10) NOT NULL,
  estado                             VARCHAR(30) NOT NULL,
  origen_deteccion                   VARCHAR(30) NOT NULL,
  monto_involucrado                  NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_recuperado                   NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_castigado                    NUMERIC(14,2) DEFAULT 0 NOT NULL,
  dias_mora_al_detectar              SMALLINT NOT NULL,
  dias_mora_actuales                 SMALLINT NOT NULL,
  es_reincidencia                    BOOLEAN DEFAULT FALSE NOT NULL,
  numero_reincidencia                SMALLINT NOT NULL,
  afecto_a_la_entrega                BOOLEAN DEFAULT FALSE NOT NULL,
  detectado_en                       TIMESTAMPTZ DEFAULT now() NOT NULL,
  notificado_en                      TIMESTAMPTZ,
  fecha_limite_subsanacion           TIMESTAMPTZ,
  cerrado_en                         TIMESTAMPTZ,
  motivo_cierre                      VARCHAR(200),
  resumen_resolucion                 TEXT,
  reportado_por                      UUID,
  version                            INTEGER DEFAULT 0 NOT NULL,
  CONSTRAINT pk_registro_incumplimiento PRIMARY KEY (id),
  CONSTRAINT ck_registro_incumplimiento_tipo CHECK (tipo IN ('ABANDONO_DE_GRUPO', 'APORTE_ATRASADO', 'APORTE_IMPAGO', 'APORTE_PARCIAL', 'BENEFICIARIO_NO_CONTINUA_APORTANDO', 'COMPROBANTE_FALSO', 'FRAUDE_CONFIRMADO', 'INCUMPLIMIENTO_AVAL', 'INCUMPLIMIENTO_ORGANIZADOR', 'INCUMPLIMIENTO_PLAN_REGULARIZACION', 'PAGO_RECHAZADO_O_REVERSADO', 'USO_INDEBIDO_DE_LA_PLATAFORMA')),
  CONSTRAINT ck_registro_incumplimiento_severidad CHECK (severidad IN ('CRITICA', 'GRAVE', 'LEVE', 'MODERADA')),
  CONSTRAINT ck_registro_incumplimiento_estado CHECK (estado IN ('ANULADO_POR_ERROR', 'CASTIGADO_INCOBRABLE', 'CON_PROMESA_DE_PAGO', 'CUBIERTO_POR_GARANTIA', 'DETECTADO', 'EN_GESTION_COBRANZA', 'EN_RECUPERACION', 'JUDICIALIZADO', 'NOTIFICADO', 'PRESCRITO', 'REGULARIZADO_CON_PLAN', 'SUBSANADO')),
  CONSTRAINT ck_registro_incumplimiento_origen_deteccion CHECK (origen_deteccion IN ('ALERTA_DE_RIESGO', 'AUDITORIA_INTERNA', 'AUTOMATICO_CONCILIACION', 'AUTOMATICO_VENCIMIENTO', 'REPORTE_DE_ORGANIZADOR', 'REPORTE_DE_PARTICIPANTE'))
);

COMMENT ON TABLE garantia.registro_incumplimiento IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. [append-only] El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.registro_incumplimiento.id IS 'PK';
COMMENT ON COLUMN garantia.registro_incumplimiento.codigo_expediente IS 'UQ';
COMMENT ON COLUMN garantia.registro_incumplimiento.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.registro_incumplimiento.participante_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.registro_incumplimiento.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.registro_incumplimiento.periodo_id IS 'FK, NULL';
COMMENT ON COLUMN garantia.registro_incumplimiento.cupo_id IS 'FK, NULL';
COMMENT ON COLUMN garantia.registro_incumplimiento.obligacion_id IS 'FK, NULL, UQ parcial';
COMMENT ON COLUMN garantia.registro_incumplimiento.entrega_afectada_id IS 'FK, NULL, M4';
COMMENT ON COLUMN garantia.registro_incumplimiento.responsable_gestion IS 'FK, NULL';
COMMENT ON COLUMN garantia.registro_incumplimiento.tipo IS 'CK, IDX';
COMMENT ON COLUMN garantia.registro_incumplimiento.severidad IS 'CK, IDX';
COMMENT ON COLUMN garantia.registro_incumplimiento.estado IS 'CK, IDX';
COMMENT ON COLUMN garantia.registro_incumplimiento.origen_deteccion IS 'CK';
COMMENT ON COLUMN garantia.registro_incumplimiento.detectado_en IS 'IDX';
COMMENT ON COLUMN garantia.registro_incumplimiento.notificado_en IS 'NULL';
COMMENT ON COLUMN garantia.registro_incumplimiento.fecha_limite_subsanacion IS 'NULL';
COMMENT ON COLUMN garantia.registro_incumplimiento.cerrado_en IS 'NULL';
COMMENT ON COLUMN garantia.registro_incumplimiento.motivo_cierre IS 'NULL';
COMMENT ON COLUMN garantia.registro_incumplimiento.resumen_resolucion IS 'NULL';
COMMENT ON COLUMN garantia.registro_incumplimiento.reportado_por IS 'FK, NULL';
