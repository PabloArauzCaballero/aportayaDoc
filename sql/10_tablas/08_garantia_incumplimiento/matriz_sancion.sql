-- matriz_sancion · módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- clase de dominio: MatrizSancion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS garantia.matriz_sancion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  politica_id                        UUID NOT NULL,
  tipo_incumplimiento                VARCHAR(40) NOT NULL,
  severidad                          VARCHAR(10) NOT NULL,
  numero_reincidencia                SMALLINT NOT NULL,
  tipo_sancion                       VARCHAR(35) NOT NULL,
  valor                              NUMERIC(12,2) NOT NULL,
  duracion_dias                      SMALLINT,
  es_automatica                      BOOLEAN DEFAULT FALSE NOT NULL,
  requiere_revision_humana           BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_matriz_sancion PRIMARY KEY (id),
  CONSTRAINT ck_matriz_sancion_severidad CHECK (severidad IN ('CRITICA', 'GRAVE', 'LEVE', 'MODERADA')),
  CONSTRAINT ck_matriz_sancion_tipo_sancion CHECK (tipo_sancion IN ('ADVERTENCIA', 'AFECTACION_REPUTACION', 'EXPULSION_DEL_GRUPO', 'INHABILITACION_PLATAFORMA', 'PERDIDA_DE_PRIORIDAD_DE_TURNO', 'RECARGO_MONETARIO', 'RESTRICCION_NUEVOS_GRUPOS', 'RETENCION_DE_ENTREGA', 'SUSPENSION_DE_VOTO'))
);

COMMENT ON TABLE garantia.matriz_sancion IS 'Módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones. El grupo no se detiene, pero la deuda no se perdona sola';
COMMENT ON COLUMN garantia.matriz_sancion.id IS 'PK';
COMMENT ON COLUMN garantia.matriz_sancion.politica_id IS 'FK, IDX';
COMMENT ON COLUMN garantia.matriz_sancion.tipo_incumplimiento IS 'UQ+severidad+numero_reincidencia';
COMMENT ON COLUMN garantia.matriz_sancion.severidad IS 'CK';
COMMENT ON COLUMN garantia.matriz_sancion.tipo_sancion IS 'CK';
COMMENT ON COLUMN garantia.matriz_sancion.duracion_dias IS 'NULL';
