-- politica_retencion · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: PoliticaRetencion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS auditoria.politica_retencion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  entidad                            VARCHAR(50) NOT NULL,
  meses_retencion_activa             SMALLINT NOT NULL,
  meses_retencion_historica          SMALLINT NOT NULL,
  accion_al_vencer                   VARCHAR(15) NOT NULL,
  base_legal                         VARCHAR(200) NOT NULL,
  vigente_desde                      DATE NOT NULL,
  CONSTRAINT pk_politica_retencion PRIMARY KEY (id),
  CONSTRAINT ck_politica_retencion_accion_al_vencer CHECK (accion_al_vencer IN ('ANONIMIZAR', 'ARCHIVAR', 'ELIMINAR'))
);

COMMENT ON TABLE auditoria.politica_retencion IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN auditoria.politica_retencion.id IS 'PK';
COMMENT ON COLUMN auditoria.politica_retencion.entidad IS 'UQ';
COMMENT ON COLUMN auditoria.politica_retencion.accion_al_vencer IS 'CK';
