-- contrato_adhesion · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: ContratoAdhesion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.contrato_adhesion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(30) NOT NULL,
  version                            SMALLINT DEFAULT 0 NOT NULL,
  tipo                               VARCHAR(25) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  url_documento                      VARCHAR(255) NOT NULL,
  hash_documento                     VARCHAR(64) NOT NULL,
  registrado_ante_regulador          BOOLEAN DEFAULT FALSE NOT NULL,
  numero_registro                    VARCHAR(60),
  fecha_registro                     DATE,
  vigente_desde                      TIMESTAMPTZ NOT NULL,
  vigente_hasta                      TIMESTAMPTZ,
  aprobado_por                       UUID,
  CONSTRAINT pk_contrato_adhesion PRIMARY KEY (id),
  CONSTRAINT ck_contrato_adhesion_tipo CHECK (tipo IN ('BILLETERA', 'GRUPO_PASANAKU', 'TARIFAS', 'TRATAMIENTO_DATOS')),
  CONSTRAINT ck_contrato_adhesion_estado CHECK (estado IN ('ARCHIVADO', 'BORRADOR', 'EN_REGISTRO', 'SUSTITUIDO', 'VIGENTE'))
);

COMMENT ON TABLE cumplimiento.contrato_adhesion IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.contrato_adhesion.id IS 'PK';
COMMENT ON COLUMN cumplimiento.contrato_adhesion.codigo IS 'UQ+version';
COMMENT ON COLUMN cumplimiento.contrato_adhesion.tipo IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.contrato_adhesion.estado IS 'CK';
COMMENT ON COLUMN cumplimiento.contrato_adhesion.numero_registro IS 'NULL';
COMMENT ON COLUMN cumplimiento.contrato_adhesion.fecha_registro IS 'NULL';
COMMENT ON COLUMN cumplimiento.contrato_adhesion.vigente_hasta IS 'NULL';
COMMENT ON COLUMN cumplimiento.contrato_adhesion.aprobado_por IS 'FK, NULL';
