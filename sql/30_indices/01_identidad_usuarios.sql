-- Índices y restricciones de unicidad del módulo 01 — Identidad, Usuarios y Seguridad
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE UNIQUE INDEX IF NOT EXISTS uq_usuario_codigo_publico
  ON identidad.usuario (codigo_publico);

CREATE UNIQUE INDEX IF NOT EXISTS uq_usuario_telefono_e164
  ON identidad.usuario (telefono_e164);

CREATE INDEX IF NOT EXISTS ix_usuario_telefono_e164
  ON identidad.usuario (telefono_e164);

CREATE UNIQUE INDEX IF NOT EXISTS uq_usuario_correo
  ON identidad.usuario (correo);

CREATE UNIQUE INDEX IF NOT EXISTS uq_direccion_usuario_usuario_id
  ON identidad.direccion_usuario (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_perfil_financiero_usuario_id
  ON identidad.perfil_financiero (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_credencial_acceso_usuario_id
  ON identidad.credencial_acceso (usuario_id);

CREATE INDEX IF NOT EXISTS ix_historial_credencial_usuario_id
  ON identidad.historial_credencial (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_politica_token_vigente_desde_proposito
  ON identidad.politica_token (vigente_desde, proposito);

CREATE INDEX IF NOT EXISTS ix_token_verificacion_usuario_id
  ON identidad.token_verificacion (usuario_id);

CREATE INDEX IF NOT EXISTS ix_token_verificacion_proposito
  ON identidad.token_verificacion (proposito);

CREATE UNIQUE INDEX IF NOT EXISTS uq_token_verificacion_hash_token
  ON identidad.token_verificacion (hash_token);

CREATE INDEX IF NOT EXISTS ix_token_verificacion_estado
  ON identidad.token_verificacion (estado);

CREATE INDEX IF NOT EXISTS ix_token_verificacion_expira_en
  ON identidad.token_verificacion (expira_en);

CREATE INDEX IF NOT EXISTS ix_token_verificacion_correlation_id
  ON identidad.token_verificacion (correlation_id);

CREATE INDEX IF NOT EXISTS ix_intento_validacion_token_token_id
  ON identidad.intento_validacion_token (token_id);

CREATE INDEX IF NOT EXISTS ix_factor_mfa_usuario_id
  ON identidad.factor_mfa (usuario_id);

CREATE INDEX IF NOT EXISTS ix_dispositivo_usuario_id
  ON identidad.dispositivo (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_dispositivo_usuario_id_huella
  ON identidad.dispositivo (usuario_id, huella);

CREATE INDEX IF NOT EXISTS ix_sesion_usuario_id
  ON identidad.sesion (usuario_id);

CREATE INDEX IF NOT EXISTS ix_sesion_expira_en
  ON identidad.sesion (expira_en);

CREATE INDEX IF NOT EXISTS ix_intento_autenticacion_usuario_id
  ON identidad.intento_autenticacion (usuario_id);

CREATE INDEX IF NOT EXISTS ix_intento_autenticacion_identificador_usado
  ON identidad.intento_autenticacion (identificador_usado);

CREATE INDEX IF NOT EXISTS ix_intento_autenticacion_fecha_hora
  ON identidad.intento_autenticacion (fecha_hora);

CREATE INDEX IF NOT EXISTS ix_intento_autenticacion_ip_origen
  ON identidad.intento_autenticacion (ip_origen);

CREATE INDEX IF NOT EXISTS ix_bloqueo_cuenta_usuario_id
  ON identidad.bloqueo_cuenta (usuario_id);

CREATE INDEX IF NOT EXISTS ix_restriccion_usuario_usuario_id
  ON identidad.restriccion_usuario (usuario_id);

CREATE INDEX IF NOT EXISTS ix_documento_identidad_usuario_id
  ON identidad.documento_identidad (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_documento_identidad_hash_numero
  ON identidad.documento_identidad (hash_numero);

CREATE INDEX IF NOT EXISTS ix_verificacion_kyc_usuario_id
  ON identidad.verificacion_kyc (usuario_id);

CREATE INDEX IF NOT EXISTS ix_referencia_personal_usuario_id
  ON identidad.referencia_personal (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rol_codigo
  ON identidad.rol (codigo);

CREATE UNIQUE INDEX IF NOT EXISTS uq_permiso_codigo
  ON identidad.permiso (codigo);

CREATE INDEX IF NOT EXISTS ix_asignacion_rol_usuario_id
  ON identidad.asignacion_rol (usuario_id);

CREATE INDEX IF NOT EXISTS ix_consentimiento_usuario_id
  ON identidad.consentimiento (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_preferencia_notificacion_usuario_id
  ON identidad.preferencia_notificacion (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_reputacion_usuario_usuario_id
  ON identidad.reputacion_usuario (usuario_id);

CREATE INDEX IF NOT EXISTS ix_solicitud_baja_usuario_id
  ON identidad.solicitud_baja (usuario_id);
