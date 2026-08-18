-- Índices y restricciones de unicidad del módulo 05 — Notificaciones y Comunicaciones
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE UNIQUE INDEX IF NOT EXISTS uq_evento_notificable_tipo
  ON notificaciones.evento_notificable (tipo);

CREATE INDEX IF NOT EXISTS ix_evento_notificable_categoria
  ON notificaciones.evento_notificable (categoria);

CREATE UNIQUE INDEX IF NOT EXISTS uq_plantilla_mensaje_codigo
  ON notificaciones.plantilla_mensaje (codigo);

CREATE INDEX IF NOT EXISTS ix_plantilla_mensaje_evento_id
  ON notificaciones.plantilla_mensaje (evento_id);

CREATE INDEX IF NOT EXISTS ix_version_plantilla_plantilla_id
  ON notificaciones.version_plantilla (plantilla_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_version_plantilla_plantilla_id_idioma_version
  ON notificaciones.version_plantilla (plantilla_id, idioma, version);

CREATE UNIQUE INDEX IF NOT EXISTS uq_proveedor_mensajeria_codigo
  ON notificaciones.proveedor_mensajeria (codigo);

CREATE INDEX IF NOT EXISTS ix_canal_vinculado_usuario_id
  ON notificaciones.canal_vinculado (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_canal_vinculado_tipo_identificador
  ON notificaciones.canal_vinculado (tipo, identificador);

CREATE INDEX IF NOT EXISTS ix_canal_vinculado_estado
  ON notificaciones.canal_vinculado (estado);

CREATE UNIQUE INDEX IF NOT EXISTS uq_lista_supresion_canal_identificador
  ON notificaciones.lista_supresion (canal, identificador);

CREATE INDEX IF NOT EXISTS ix_lista_supresion_categoria
  ON notificaciones.lista_supresion (categoria);

CREATE INDEX IF NOT EXISTS ix_lista_supresion_activa
  ON notificaciones.lista_supresion (activa);

CREATE INDEX IF NOT EXISTS ix_notificacion_usuario_id
  ON notificaciones.notificacion (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notificacion_clave_deduplicacion
  ON notificaciones.notificacion (clave_deduplicacion);

CREATE INDEX IF NOT EXISTS ix_notificacion_estado
  ON notificaciones.notificacion (estado);

CREATE INDEX IF NOT EXISTS ix_notificacion_programada_para
  ON notificaciones.notificacion (programada_para);

CREATE INDEX IF NOT EXISTS ix_notificacion_creada_en
  ON notificaciones.notificacion (creada_en);

CREATE INDEX IF NOT EXISTS ix_notificacion_correlation_id
  ON notificaciones.notificacion (correlation_id);

CREATE INDEX IF NOT EXISTS ix_envio_notificacion_notificacion_id
  ON notificaciones.envio_notificacion (notificacion_id);

CREATE INDEX IF NOT EXISTS ix_envio_notificacion_estado
  ON notificaciones.envio_notificacion (estado);

CREATE UNIQUE INDEX IF NOT EXISTS uq_envio_notificacion_id_mensaje_proveedor
  ON notificaciones.envio_notificacion (id_mensaje_proveedor);

CREATE INDEX IF NOT EXISTS ix_envio_notificacion_proximo_reintento_en
  ON notificaciones.envio_notificacion (proximo_reintento_en);

CREATE INDEX IF NOT EXISTS ix_evento_entrega_mensaje_envio_id
  ON notificaciones.evento_entrega_mensaje (envio_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cola_envio_envio_id
  ON notificaciones.cola_envio (envio_id);

CREATE INDEX IF NOT EXISTS ix_cola_envio_particion
  ON notificaciones.cola_envio (particion);

CREATE INDEX IF NOT EXISTS ix_cola_envio_disponible_en
  ON notificaciones.cola_envio (disponible_en);

CREATE INDEX IF NOT EXISTS ix_cola_muerta_envio_id
  ON notificaciones.cola_muerta (envio_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_enlace_pago_notificado_notificacion_id
  ON notificaciones.enlace_pago_notificado (notificacion_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_enlace_pago_notificado_token_id
  ON notificaciones.enlace_pago_notificado (token_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_enlace_pago_notificado_url_corta
  ON notificaciones.enlace_pago_notificado (url_corta);

CREATE INDEX IF NOT EXISTS ix_respuesta_entrante_canal_vinculado_id
  ON notificaciones.respuesta_entrante (canal_vinculado_id);

CREATE INDEX IF NOT EXISTS ix_respuesta_entrante_recibida_en
  ON notificaciones.respuesta_entrante (recibida_en);

CREATE INDEX IF NOT EXISTS ix_bandeja_entrada_usuario_id
  ON notificaciones.bandeja_entrada (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_bandeja_entrada_notificacion_id
  ON notificaciones.bandeja_entrada (notificacion_id);

CREATE INDEX IF NOT EXISTS ix_bandeja_entrada_leida
  ON notificaciones.bandeja_entrada (leida);
