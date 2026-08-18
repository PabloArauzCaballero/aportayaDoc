-- requisito_habilitacion · módulo 07 — Organizador y Automatización
-- clase de dominio: RequisitoHabilitacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS organizador.requisito_habilitacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(40) NOT NULL,
  descripcion                        VARCHAR(200) NOT NULL,
  tipo                               VARCHAR(25) NOT NULL,
  valor_minimo                       NUMERIC(12,2) NOT NULL,
  es_obligatorio                     BOOLEAN DEFAULT FALSE NOT NULL,
  nivel_requerido                    VARCHAR(15) NOT NULL,
  activo                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_requisito_habilitacion PRIMARY KEY (id),
  CONSTRAINT ck_requisito_habilitacion_tipo CHECK (tipo IN ('ANTIGUEDAD', 'CAPACITACION', 'GARANTIA_ECONOMICA', 'KYC', 'REPUTACION')),
  CONSTRAINT ck_requisito_habilitacion_nivel_requerido CHECK (nivel_requerido IN ('APRENDIZ', 'ESTANDAR', 'MAESTRO', 'SENIOR'))
);

COMMENT ON TABLE organizador.requisito_habilitacion IS 'Módulo 07 — Organizador y Automatización. Administrar es un rol, no un negocio: el organizador no cobra ni custodia';
COMMENT ON COLUMN organizador.requisito_habilitacion.id IS 'PK';
COMMENT ON COLUMN organizador.requisito_habilitacion.codigo IS 'UQ';
COMMENT ON COLUMN organizador.requisito_habilitacion.tipo IS 'CK';
COMMENT ON COLUMN organizador.requisito_habilitacion.nivel_requerido IS 'CK';
