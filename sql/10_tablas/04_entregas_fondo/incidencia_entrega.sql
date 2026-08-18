-- incidencia_entrega · módulo 04 — Entregas de Fondo
-- clase de dominio: IncidenciaEntrega
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS entregas.incidencia_entrega (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  entrega_id                         UUID NOT NULL,
  tipo                               VARCHAR(35) NOT NULL,
  severidad                          VARCHAR(10) NOT NULL,
  descripcion                        TEXT NOT NULL,
  reportada_por                      UUID NOT NULL,
  asignada_a                         UUID,
  estado                             VARCHAR(15) NOT NULL,
  sla_horas                          SMALLINT NOT NULL,
  fecha_limite_sla                   TIMESTAMPTZ NOT NULL,
  resolucion                         VARCHAR(400),
  evidencias                         JSONB NOT NULL,
  abierta_en                         TIMESTAMPTZ DEFAULT now() NOT NULL,
  resuelta_en                        TIMESTAMPTZ,
  CONSTRAINT pk_incidencia_entrega PRIMARY KEY (id),
  CONSTRAINT ck_incidencia_entrega_tipo CHECK (tipo IN ('BENEFICIARIO_NO_RECIBIO', 'DATOS_BANCARIOS_ERRONEOS', 'DESEMBOLSO_RECHAZADO', 'ENTREGA_DUPLICADA', 'FONDO_INCOMPLETO', 'MONTO_NO_COINCIDE', 'RECLAMO_DE_TERCERO', 'SOSPECHA_FRAUDE')),
  CONSTRAINT ck_incidencia_entrega_severidad CHECK (severidad IN ('ALTA', 'BAJA', 'CRITICA', 'MEDIA')),
  CONSTRAINT ck_incidencia_entrega_estado CHECK (estado IN ('ABIERTA', 'CERRADA', 'EN_GESTION', 'ESCALADA', 'RESUELTA'))
);

COMMENT ON TABLE entregas.incidencia_entrega IS 'Módulo 04 — Entregas de Fondo. Que la bolsa llegue completa, a la persona correcta, una sola vez';
COMMENT ON COLUMN entregas.incidencia_entrega.id IS 'PK';
COMMENT ON COLUMN entregas.incidencia_entrega.entrega_id IS 'FK, IDX';
COMMENT ON COLUMN entregas.incidencia_entrega.tipo IS 'CK';
COMMENT ON COLUMN entregas.incidencia_entrega.severidad IS 'CK';
COMMENT ON COLUMN entregas.incidencia_entrega.reportada_por IS 'FK';
COMMENT ON COLUMN entregas.incidencia_entrega.asignada_a IS 'FK, NULL';
COMMENT ON COLUMN entregas.incidencia_entrega.estado IS 'CK, IDX';
COMMENT ON COLUMN entregas.incidencia_entrega.fecha_limite_sla IS 'IDX';
COMMENT ON COLUMN entregas.incidencia_entrega.resolucion IS 'NULL';
COMMENT ON COLUMN entregas.incidencia_entrega.resuelta_en IS 'NULL';
