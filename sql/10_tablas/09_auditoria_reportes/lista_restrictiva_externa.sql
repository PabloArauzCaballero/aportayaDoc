-- lista_restrictiva_externa · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: ListaRestrictivaExterna
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.lista_restrictiva_externa (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  nombre_lista                       VARCHAR(30) NOT NULL,
  version                            VARCHAR(20) NOT NULL,
  fecha_actualizacion                DATE NOT NULL,
  registros                          INTEGER NOT NULL,
  CONSTRAINT pk_lista_restrictiva_externa PRIMARY KEY (id)
);

COMMENT ON TABLE auditoria.lista_restrictiva_externa IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.lista_restrictiva_externa.id IS 'PK';
COMMENT ON COLUMN auditoria.lista_restrictiva_externa.nombre_lista IS 'UQ+version';
