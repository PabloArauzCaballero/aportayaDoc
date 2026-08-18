-- preferencia_notificacion · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: PreferenciaNotificacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.preferencia_notificacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  canal_primario                     VARCHAR(20) NOT NULL,
  canal_respaldo                     VARCHAR(20),
  acepta_whatsapp                    BOOLEAN DEFAULT FALSE NOT NULL,
  acepta_correo                      BOOLEAN DEFAULT FALSE NOT NULL,
  acepta_sms                         BOOLEAN DEFAULT FALSE NOT NULL,
  acepta_push                        BOOLEAN DEFAULT FALSE NOT NULL,
  tope_diario_mensajes               SMALLINT NOT NULL,
  hora_no_molestar_desde             TIME,
  hora_no_molestar_hasta             TIME,
  frecuencia_resumen                 VARCHAR(15) NOT NULL,
  CONSTRAINT pk_preferencia_notificacion PRIMARY KEY (id),
  CONSTRAINT ck_preferencia_notificacion_canal_primario CHECK (canal_primario IN ('APP_AUTENTICADORA', 'CORREO', 'LLAMADA_VOZ', 'PUSH_APP', 'SMS', 'WHATSAPP')),
  CONSTRAINT ck_preferencia_notificacion_canal_respaldo CHECK (canal_respaldo IN ('APP_AUTENTICADORA', 'CORREO', 'LLAMADA_VOZ', 'PUSH_APP', 'SMS', 'WHATSAPP'))
);

COMMENT ON TABLE identidad.preferencia_notificacion IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.preferencia_notificacion.id IS 'PK';
COMMENT ON COLUMN identidad.preferencia_notificacion.usuario_id IS 'FK, UQ';
COMMENT ON COLUMN identidad.preferencia_notificacion.canal_primario IS 'CK';
COMMENT ON COLUMN identidad.preferencia_notificacion.canal_respaldo IS 'CK, NULL';
COMMENT ON COLUMN identidad.preferencia_notificacion.hora_no_molestar_desde IS 'NULL';
COMMENT ON COLUMN identidad.preferencia_notificacion.hora_no_molestar_hasta IS 'NULL';
