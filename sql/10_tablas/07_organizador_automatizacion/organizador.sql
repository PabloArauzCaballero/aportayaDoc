-- organizador · módulo 07 — Organizador y Automatización
-- clase de dominio: Organizador
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS organizador.organizador (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  estado                             VARCHAR(25) NOT NULL,
  nivel                              VARCHAR(15) NOT NULL,
  limite_grupos_simultaneos          SMALLINT NOT NULL,
  limite_monto_administrado          NUMERIC(16,2) NOT NULL,
  grupos_activos                     SMALLINT NOT NULL,
  grupos_historicos                  SMALLINT NOT NULL,
  monto_administrado_actual          NUMERIC(16,2) DEFAULT 0 NOT NULL,
  calificacion_promedio              NUMERIC(3,2) NOT NULL,
  indice_morosidad_cartera           NUMERIC(5,2) NOT NULL,
  fecha_postulacion                  TIMESTAMPTZ NOT NULL,
  fecha_habilitacion                 TIMESTAMPTZ,
  fecha_suspension                   TIMESTAMPTZ,
  version                            INTEGER DEFAULT 0 NOT NULL,
  CONSTRAINT pk_organizador PRIMARY KEY (id),
  CONSTRAINT ck_organizador_estado CHECK (estado IN ('CAPACITACION_PENDIENTE', 'DESHABILITADO', 'EN_EVALUACION', 'HABILITADO', 'LIMITADO', 'POSTULADO', 'RETIRADO', 'SUSPENDIDO')),
  CONSTRAINT ck_organizador_nivel CHECK (nivel IN ('APRENDIZ', 'ESTANDAR', 'MAESTRO', 'SENIOR'))
);

COMMENT ON TABLE organizador.organizador IS 'Módulo 07 — Organizador y Automatización. Administrar es un rol, no un negocio: el organizador no cobra ni custodia';
COMMENT ON COLUMN organizador.organizador.id IS 'PK';
COMMENT ON COLUMN organizador.organizador.usuario_id IS 'FK, UQ';
COMMENT ON COLUMN organizador.organizador.estado IS 'CK, IDX';
COMMENT ON COLUMN organizador.organizador.nivel IS 'CK';
COMMENT ON COLUMN organizador.organizador.fecha_habilitacion IS 'NULL';
COMMENT ON COLUMN organizador.organizador.fecha_suspension IS 'NULL';
