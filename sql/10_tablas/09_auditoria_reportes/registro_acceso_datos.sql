-- registro_acceso_datos · módulo 09 — Auditoría, Reportes y Cumplimiento
-- clase de dominio: RegistroAccesoDatos
-- APPEND-ONLY: sin UPDATE ni DELETE (ver sql/40_reglas)
-- PARTICIONADA por rango de fecha_hora (mensual)
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS comun.registro_acceso_datos (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_consultor_id               UUID NOT NULL,
  usuario_afectado_id                UUID NOT NULL,
  tipo_dato                          VARCHAR(30) NOT NULL,
  operacion                          VARCHAR(15) NOT NULL,
  justificacion                      VARCHAR(300) NOT NULL,
  ticket_soporte_id                  VARCHAR(30),
  cantidad_registros                 INTEGER DEFAULT 0 NOT NULL,
  ip_origen                          INET NOT NULL,
  fecha_hora                         TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_registro_acceso_datos PRIMARY KEY (id, fecha_hora),
  CONSTRAINT ck_registro_acceso_datos_tipo_dato CHECK (tipo_dato IN ('CUENTA_BANCARIA', 'DOCUMENTO_IDENTIDAD', 'HISTORIAL_PAGOS', 'TELEFONO')),
  CONSTRAINT ck_registro_acceso_datos_operacion CHECK (operacion IN ('BUSQUEDA', 'EXPORTACION', 'LECTURA'))
) PARTITION BY RANGE (fecha_hora);

CREATE TABLE IF NOT EXISTS comun.registro_acceso_datos_desborde
  PARTITION OF comun.registro_acceso_datos DEFAULT;

DO $$
DECLARE d DATE := date_trunc('year', current_date)::date;
BEGIN
  FOR i IN 0..23 LOOP
    EXECUTE format(
      'CREATE TABLE IF NOT EXISTS comun.registro_acceso_datos_%s PARTITION OF comun.registro_acceso_datos FOR VALUES FROM (%L) TO (%L)',
      to_char(d + (i || ' month')::interval, 'YYYYMM'),
      d + (i || ' month')::interval,
      d + ((i + 1) || ' month')::interval);
  END LOOP;
END $$;

COMMENT ON TABLE comun.registro_acceso_datos IS 'Módulo 09 — Auditoría, Reportes y Cumplimiento. [append-only] Poder demostrar todo lo anterior ante un reclamo o un regulador';
COMMENT ON COLUMN comun.registro_acceso_datos.id IS 'PK';
COMMENT ON COLUMN comun.registro_acceso_datos.usuario_consultor_id IS 'FK, IDX';
COMMENT ON COLUMN comun.registro_acceso_datos.usuario_afectado_id IS 'FK, IDX';
COMMENT ON COLUMN comun.registro_acceso_datos.tipo_dato IS 'CK';
COMMENT ON COLUMN comun.registro_acceso_datos.operacion IS 'CK';
COMMENT ON COLUMN comun.registro_acceso_datos.ticket_soporte_id IS 'NULL';
COMMENT ON COLUMN comun.registro_acceso_datos.fecha_hora IS 'IDX';
