-- grupo · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- clase de dominio: Grupo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.grupo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo_publico                     VARCHAR(12) NOT NULL,
  nombre                             VARCHAR(120) NOT NULL,
  descripcion                        VARCHAR(400),
  monto_aporte                       NUMERIC(14,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  periodicidad                       VARCHAR(15) NOT NULL,
  dia_cobro                          SMALLINT NOT NULL,
  num_periodos                       SMALLINT NOT NULL,
  cupos_totales                      SMALLINT NOT NULL,
  cupos_ocupados                     SMALLINT NOT NULL,
  fecha_inicio                       DATE NOT NULL,
  fecha_fin_estimada                 DATE NOT NULL,
  estado                             VARCHAR(30) NOT NULL,
  tipo_conformacion                  VARCHAR(30) NOT NULL,
  modalidad_turnos                   VARCHAR(25) NOT NULL,
  visibilidad                        VARCHAR(20) NOT NULL,
  organizador_id                     UUID,
  es_autogestionado                  BOOLEAN DEFAULT FALSE NOT NULL,
  requiere_kyc_minimo                VARCHAR(15) NOT NULL,
  reputacion_minima                  NUMERIC(6,2) NOT NULL,
  dias_gracia                        SMALLINT NOT NULL,
  aplica_recargo_mora                BOOLEAN DEFAULT FALSE NOT NULL,
  usa_fondo_garantia                 BOOLEAN DEFAULT FALSE NOT NULL,
  porcentaje_fondo_garantia          NUMERIC(5,2) NOT NULL,
  quorum_decisiones                  NUMERIC(4,3) NOT NULL,
  cancelado_en                       TIMESTAMPTZ,
  CONSTRAINT pk_grupo PRIMARY KEY (id),
  CONSTRAINT ck_grupo_monto_aporte CHECK (monto_aporte > 0),
  CONSTRAINT ck_grupo_periodicidad CHECK (periodicidad IN ('BIMENSUAL', 'MENSUAL', 'QUINCENAL', 'SEMANAL')),
  CONSTRAINT ck_grupo_num_periodos CHECK (num_periodos >= 3),
  CONSTRAINT ck_grupo_estado CHECK (estado IN ('ABIERTO_A_INSCRIPCION', 'ACTIVO', 'BORRADOR', 'CANCELADO', 'CONFORMADO', 'DISUELTO_ANTICIPADAMENTE', 'EN_CURSO', 'FINALIZADO', 'SUSPENDIDO')),
  CONSTRAINT ck_grupo_tipo_conformacion CHECK (tipo_conformacion IN ('EMPAREJAMIENTO_AUTOMATICO', 'MANUAL_POR_INVITACION', 'MIXTA')),
  CONSTRAINT ck_grupo_modalidad_turnos CHECK (modalidad_turnos IN ('ACUERDO_MANUAL', 'ORDEN_DE_INGRESO', 'POR_REPUTACION', 'SORTEO_ALEATORIO', 'SUBASTA_DESCUENTO')),
  CONSTRAINT ck_grupo_visibilidad CHECK (visibilidad IN ('ENLACE_DIRECTO', 'PRIVADO', 'PUBLICO_DIRECTORIO'))
);

COMMENT ON TABLE grupos.grupo IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.grupo.id IS 'PK';
COMMENT ON COLUMN grupos.grupo.codigo_publico IS 'UQ';
COMMENT ON COLUMN grupos.grupo.descripcion IS 'NULL';
COMMENT ON COLUMN grupos.grupo.monto_aporte IS 'CK: > 0';
COMMENT ON COLUMN grupos.grupo.periodicidad IS 'CK';
COMMENT ON COLUMN grupos.grupo.num_periodos IS 'CK: >= 3';
COMMENT ON COLUMN grupos.grupo.estado IS 'CK, IDX';
COMMENT ON COLUMN grupos.grupo.tipo_conformacion IS 'CK';
COMMENT ON COLUMN grupos.grupo.modalidad_turnos IS 'CK';
COMMENT ON COLUMN grupos.grupo.visibilidad IS 'CK';
COMMENT ON COLUMN grupos.grupo.organizador_id IS 'FK, NULL';
COMMENT ON COLUMN grupos.grupo.cancelado_en IS 'NULL';
