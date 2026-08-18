-- solicitud_retiro · módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- clase de dominio: SolicitudRetiro
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS grupos.solicitud_retiro (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  participante_id                    UUID NOT NULL,
  motivo                             VARCHAR(200) NOT NULL,
  solicitado_en                      TIMESTAMPTZ NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  posicion                           VARCHAR(10),
  plan_regularizacion_id             UUID,
  requiere_reemplazo                 BOOLEAN DEFAULT FALSE NOT NULL,
  liquidacion_calculada              NUMERIC(14,2) NOT NULL,
  CONSTRAINT pk_solicitud_retiro PRIMARY KEY (id),
  CONSTRAINT ck_solicitud_retiro_estado CHECK (estado IN ('APROBADA', 'PENDIENTE', 'RECHAZADA')),
  CONSTRAINT ck_solicitud_retiro_posicion CHECK (posicion IN ('ACREEDORA', 'DEUDORA', 'NEUTRA'))
);

COMMENT ON TABLE grupos.solicitud_retiro IS 'Módulo 02 — Grupos, Cupos, Turnos y Gobernanza. Reglas del juego, orden de cobro y decisiones colectivas';
COMMENT ON COLUMN grupos.solicitud_retiro.id IS 'PK';
COMMENT ON COLUMN grupos.solicitud_retiro.participante_id IS 'FK, UQ parcial';
COMMENT ON COLUMN grupos.solicitud_retiro.estado IS 'CK';
COMMENT ON COLUMN grupos.solicitud_retiro.posicion IS 'CK, NULL';
COMMENT ON COLUMN grupos.solicitud_retiro.plan_regularizacion_id IS 'FK, NULL';
