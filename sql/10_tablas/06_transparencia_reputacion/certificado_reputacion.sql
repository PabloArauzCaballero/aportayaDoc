-- certificado_reputacion · módulo 06 — Transparencia y Reputación
-- clase de dominio: CertificadoReputacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS transparencia.certificado_reputacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  snapshot_id                        UUID NOT NULL,
  codigo_verificacion                VARCHAR(40) NOT NULL,
  hash_contenido                     VARCHAR(64) NOT NULL,
  firma_digital                      VARCHAR(255) NOT NULL,
  url_publica                        VARCHAR(255) NOT NULL,
  emitido_en                         TIMESTAMPTZ DEFAULT now() NOT NULL,
  expira_en                          TIMESTAMPTZ NOT NULL,
  revocado_en                        TIMESTAMPTZ,
  CONSTRAINT pk_certificado_reputacion PRIMARY KEY (id)
);

COMMENT ON TABLE transparencia.certificado_reputacion IS 'Módulo 06 — Transparencia y Reputación. Que nadie tenga que "creerle" al organizador';
COMMENT ON COLUMN transparencia.certificado_reputacion.id IS 'PK';
COMMENT ON COLUMN transparencia.certificado_reputacion.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN transparencia.certificado_reputacion.snapshot_id IS 'FK, UQ';
COMMENT ON COLUMN transparencia.certificado_reputacion.codigo_verificacion IS 'UQ';
COMMENT ON COLUMN transparencia.certificado_reputacion.revocado_en IS 'NULL';
