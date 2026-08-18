-- ticket_soporte · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: TicketSoporte
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.ticket_soporte (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  asignado_a                         UUID,
  categoria                          VARCHAR(40) NOT NULL,
  asunto                             VARCHAR(160) NOT NULL,
  descripcion                        TEXT NOT NULL,
  prioridad                          VARCHAR(10) NOT NULL,
  estado                             VARCHAR(20) NOT NULL,
  referencia_entidad                 VARCHAR(40),
  referencia_id                      UUID,
  sla_horas                          SMALLINT NOT NULL,
  abierto_en                         TIMESTAMPTZ DEFAULT now() NOT NULL,
  resuelto_en                        TIMESTAMPTZ,
  CONSTRAINT pk_ticket_soporte PRIMARY KEY (id),
  CONSTRAINT ck_ticket_soporte_prioridad CHECK (prioridad IN ('ALTA', 'BAJA', 'MEDIA', 'URGENTE')),
  CONSTRAINT ck_ticket_soporte_estado CHECK (estado IN ('ABIERTO', 'CERRADO', 'EN_ATENCION', 'ESPERANDO_USUARIO', 'RESUELTO'))
);

COMMENT ON TABLE auditoria.ticket_soporte IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.ticket_soporte.id IS 'PK';
COMMENT ON COLUMN auditoria.ticket_soporte.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN auditoria.ticket_soporte.asignado_a IS 'FK, NULL, IDX';
COMMENT ON COLUMN auditoria.ticket_soporte.prioridad IS 'CK';
COMMENT ON COLUMN auditoria.ticket_soporte.estado IS 'CK, IDX';
COMMENT ON COLUMN auditoria.ticket_soporte.referencia_entidad IS 'NULL';
COMMENT ON COLUMN auditoria.ticket_soporte.referencia_id IS 'NULL';
COMMENT ON COLUMN auditoria.ticket_soporte.resuelto_en IS 'NULL';
