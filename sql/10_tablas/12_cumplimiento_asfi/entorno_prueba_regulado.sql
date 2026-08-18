-- entorno_prueba_regulado · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: EntornoPruebaRegulado
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.entorno_prueba_regulado (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  licencia_regulatoria_id            UUID NOT NULL,
  servicio_en_prueba                 VARCHAR(120) NOT NULL,
  alcance                            JSONB NOT NULL,
  limite_usuarios                    INTEGER,
  limite_monto_operacion             NUMERIC(16,2),
  garantia_constituida               NUMERIC(16,2),
  fecha_inicio                       DATE NOT NULL,
  fecha_fin                          DATE NOT NULL,
  estado                             VARCHAR(15) NOT NULL,
  informes_remitidos                 SMALLINT DEFAULT 0 NOT NULL,
  CONSTRAINT pk_entorno_prueba_regulado PRIMARY KEY (id),
  CONSTRAINT ck_entorno_prueba_regulado_estado CHECK (estado IN ('ACTIVO', 'FINALIZADO', 'SOLICITADO', 'SUSPENDIDO'))
);

COMMENT ON TABLE cumplimiento.entorno_prueba_regulado IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.entorno_prueba_regulado.id IS 'PK';
COMMENT ON COLUMN cumplimiento.entorno_prueba_regulado.licencia_regulatoria_id IS 'FK, IDX';
COMMENT ON COLUMN cumplimiento.entorno_prueba_regulado.limite_usuarios IS 'NULL';
COMMENT ON COLUMN cumplimiento.entorno_prueba_regulado.limite_monto_operacion IS 'NULL';
COMMENT ON COLUMN cumplimiento.entorno_prueba_regulado.garantia_constituida IS 'NULL';
COMMENT ON COLUMN cumplimiento.entorno_prueba_regulado.estado IS 'CK, IDX';
