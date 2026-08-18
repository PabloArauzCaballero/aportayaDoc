-- bitacora_evento · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: BitacoraEvento
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- PARTICIONADA por rango de fecha_hora (mensual)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS comun.bitacora_evento (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  secuencia                          BIGSERIAL NOT NULL,
  entidad                            VARCHAR(50) NOT NULL,
  entidad_id                         UUID NOT NULL,
  accion                             VARCHAR(30) NOT NULL,
  actor_usuario_id                   UUID,
  actor_rol                          VARCHAR(30),
  suplantando_a_usuario_id           UUID,
  origen                             VARCHAR(25) NOT NULL,
  ip_origen                          INET,
  agente_usuario                     VARCHAR(255),
  correlation_id                     UUID NOT NULL,
  request_id                         UUID,
  valor_anterior                     JSONB,
  valor_nuevo                        JSONB,
  campos_modificados                 VARCHAR(400),
  motivo                             VARCHAR(300),
  grupo_id                           UUID,
  hash_registro                      VARCHAR(64) NOT NULL,
  hash_anterior                      VARCHAR(64) NOT NULL,
  fecha_hora                         TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_bitacora_evento PRIMARY KEY (id, fecha_hora),
  CONSTRAINT ck_bitacora_evento_accion CHECK (accion IN ('ACCESO_DATOS', 'ACTUALIZACION', 'ANULACION', 'APROBACION', 'CAMBIO_CONFIGURACION', 'CAMBIO_ESTADO', 'CAMBIO_PERMISOS', 'COBERTURA', 'CONCILIACION', 'CREACION', 'DESEMBOLSO', 'ELIMINACION_LOGICA', 'EXPORTACION', 'INICIO_SESION', 'LIQUIDACION', 'RECHAZO', 'SANCION')),
  CONSTRAINT ck_bitacora_evento_origen CHECK (origen IN ('API_PUBLICA', 'APP_MOVIL', 'APP_WEB', 'MIGRACION', 'ORGANIZADOR_DIGITAL', 'PANEL_ADMIN', 'SOPORTE_INTERNO', 'TAREA_PROGRAMADA', 'WEBHOOK_ENTRANTE'))
) PARTITION BY RANGE (fecha_hora);

CREATE TABLE IF NOT EXISTS comun.bitacora_evento_desborde
  PARTITION OF comun.bitacora_evento DEFAULT;

DO $$
DECLARE d DATE := date_trunc('year', current_date)::date;
BEGIN
  FOR i IN 0..23 LOOP
    EXECUTE format(
      'CREATE TABLE IF NOT EXISTS comun.bitacora_evento_%s PARTITION OF comun.bitacora_evento FOR VALUES FROM (%L) TO (%L)',
      to_char(d + (i || ' month')::interval, 'YYYYMM'),
      d + (i || ' month')::interval,
      d + ((i + 1) || ' month')::interval);
  END LOOP;
END $$;

COMMENT ON TABLE comun.bitacora_evento IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. [append-only] Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN comun.bitacora_evento.id IS 'PK';
COMMENT ON COLUMN comun.bitacora_evento.secuencia IS 'UQ';
COMMENT ON COLUMN comun.bitacora_evento.entidad IS 'IDX';
COMMENT ON COLUMN comun.bitacora_evento.entidad_id IS 'IDX';
COMMENT ON COLUMN comun.bitacora_evento.accion IS 'CK, IDX';
COMMENT ON COLUMN comun.bitacora_evento.actor_usuario_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN comun.bitacora_evento.actor_rol IS 'NULL';
COMMENT ON COLUMN comun.bitacora_evento.suplantando_a_usuario_id IS 'FK, NULL';
COMMENT ON COLUMN comun.bitacora_evento.origen IS 'CK';
COMMENT ON COLUMN comun.bitacora_evento.ip_origen IS 'NULL';
COMMENT ON COLUMN comun.bitacora_evento.agente_usuario IS 'NULL';
COMMENT ON COLUMN comun.bitacora_evento.correlation_id IS 'IDX';
COMMENT ON COLUMN comun.bitacora_evento.request_id IS 'NULL';
COMMENT ON COLUMN comun.bitacora_evento.valor_anterior IS 'NULL';
COMMENT ON COLUMN comun.bitacora_evento.valor_nuevo IS 'NULL';
COMMENT ON COLUMN comun.bitacora_evento.campos_modificados IS 'NULL';
COMMENT ON COLUMN comun.bitacora_evento.motivo IS 'NULL';
COMMENT ON COLUMN comun.bitacora_evento.grupo_id IS 'FK, NULL, IDX';
COMMENT ON COLUMN comun.bitacora_evento.hash_registro IS 'UQ';
COMMENT ON COLUMN comun.bitacora_evento.fecha_hora IS 'IDX, particion';
