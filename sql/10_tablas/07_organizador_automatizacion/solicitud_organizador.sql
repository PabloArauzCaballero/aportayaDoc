-- solicitud_organizador · módulo 07 — Organizador y Automatización
-- clase de dominio: SolicitudOrganizador
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS organizador.solicitud_organizador (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  motivacion                         TEXT NOT NULL,
  experiencia_declarada              TEXT NOT NULL,
  kyc_reforzado_id                   UUID,
  puntaje_reputacion_al_solicitar    NUMERIC(6,2) NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  revisada_por                       UUID,
  motivo_rechazo                     VARCHAR(300),
  fecha_solicitud                    TIMESTAMPTZ NOT NULL,
  fecha_resolucion                   TIMESTAMPTZ,
  CONSTRAINT pk_solicitud_organizador PRIMARY KEY (id),
  CONSTRAINT ck_solicitud_organizador_estado CHECK (estado IN ('APROBADA', 'EN_REVISION', 'PENDIENTE', 'RECHAZADA'))
);

COMMENT ON TABLE organizador.solicitud_organizador IS 'Módulo 07 — Organizador y Automatización. Administrar es un rol, no un negocio: el organizador no cobra ni custodia';
COMMENT ON COLUMN organizador.solicitud_organizador.id IS 'PK';
COMMENT ON COLUMN organizador.solicitud_organizador.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN organizador.solicitud_organizador.kyc_reforzado_id IS 'FK, NULL, M1';
COMMENT ON COLUMN organizador.solicitud_organizador.estado IS 'CK';
COMMENT ON COLUMN organizador.solicitud_organizador.revisada_por IS 'FK, NULL';
COMMENT ON COLUMN organizador.solicitud_organizador.motivo_rechazo IS 'NULL';
COMMENT ON COLUMN organizador.solicitud_organizador.fecha_resolucion IS 'NULL';
