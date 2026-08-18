-- documento_identidad · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: DocumentoIdentidad
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.documento_identidad (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  tipo                               VARCHAR(25) NOT NULL,
  numero_cifrado                     VARCHAR(255) NOT NULL,
  version_llave                      SMALLINT NOT NULL,
  hash_numero                        VARCHAR(64) NOT NULL,
  complemento                        VARCHAR(10),
  pais_emision                       CHAR(2) NOT NULL,
  fecha_emision                      DATE,
  fecha_expiracion                   DATE,
  url_anverso                        VARCHAR(255) NOT NULL,
  url_reverso                        VARCHAR(255),
  hash_archivo                       VARCHAR(64) NOT NULL,
  estado                             VARCHAR(20) NOT NULL,
  CONSTRAINT pk_documento_identidad PRIMARY KEY (id),
  CONSTRAINT ck_documento_identidad_tipo CHECK (tipo IN ('CARNET_EXTRANJERIA', 'CI', 'PASAPORTE')),
  CONSTRAINT ck_documento_identidad_estado CHECK (estado IN ('APROBADA', 'EN_REVISION', 'NO_INICIADA', 'PENDIENTE', 'RECHAZADA', 'VENCIDA'))
);

COMMENT ON TABLE identidad.documento_identidad IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.documento_identidad.id IS 'PK';
COMMENT ON COLUMN identidad.documento_identidad.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN identidad.documento_identidad.tipo IS 'CK';
COMMENT ON COLUMN identidad.documento_identidad.hash_numero IS 'UQ, busqueda sin descifrar';
COMMENT ON COLUMN identidad.documento_identidad.complemento IS 'NULL';
COMMENT ON COLUMN identidad.documento_identidad.fecha_emision IS 'NULL';
COMMENT ON COLUMN identidad.documento_identidad.fecha_expiracion IS 'NULL';
COMMENT ON COLUMN identidad.documento_identidad.url_reverso IS 'NULL';
COMMENT ON COLUMN identidad.documento_identidad.estado IS 'CK';
