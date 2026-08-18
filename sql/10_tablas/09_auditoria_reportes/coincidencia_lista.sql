-- coincidencia_lista · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: CoincidenciaLista
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.coincidencia_lista (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  lista_id                           UUID NOT NULL,
  usuario_id                         UUID NOT NULL,
  revisada_por                       UUID,
  nombre_coincidente                 VARCHAR(150) NOT NULL,
  puntaje_similitud                  NUMERIC(5,4) NOT NULL,
  estado                             VARCHAR(20) NOT NULL,
  revisada_en                        TIMESTAMPTZ,
  CONSTRAINT pk_coincidencia_lista PRIMARY KEY (id),
  CONSTRAINT ck_coincidencia_lista_estado CHECK (estado IN ('CONFIRMADA', 'FALSO_POSITIVO', 'PENDIENTE'))
);

COMMENT ON TABLE auditoria.coincidencia_lista IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.coincidencia_lista.id IS 'PK';
COMMENT ON COLUMN auditoria.coincidencia_lista.lista_id IS 'FK, IDX';
COMMENT ON COLUMN auditoria.coincidencia_lista.usuario_id IS 'FK, IDX';
COMMENT ON COLUMN auditoria.coincidencia_lista.revisada_por IS 'FK, NULL';
COMMENT ON COLUMN auditoria.coincidencia_lista.estado IS 'CK, IDX';
COMMENT ON COLUMN auditoria.coincidencia_lista.revisada_en IS 'NULL';
