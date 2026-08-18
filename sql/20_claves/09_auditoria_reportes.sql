-- Claves foráneas del módulo 09 — Auditoría, Reportes y Cumplimiento
-- Generado por scripts/generar_ddl.py — no editar a mano.
-- Se aplican después de crear todas las tablas: el modelo tiene
-- referencias circulares entre módulos.

ALTER TABLE auditoria.alerta_cumplimiento
  ADD CONSTRAINT fk_alerta_cumplimiento_analista_id
  FOREIGN KEY (analista_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE auditoria.alerta_cumplimiento
  ADD CONSTRAINT fk_alerta_cumplimiento_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE auditoria.alerta_cumplimiento
  ADD CONSTRAINT fk_alerta_cumplimiento_regla_id
  FOREIGN KEY (regla_id) REFERENCES auditoria.regla_cumplimiento (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE auditoria.alerta_cumplimiento
  ADD CONSTRAINT fk_alerta_cumplimiento_reporte_sospechoso_id
  FOREIGN KEY (reporte_sospechoso_id) REFERENCES auditoria.reporte_operacion_sospechosa (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE auditoria.alerta_cumplimiento
  ADD CONSTRAINT fk_alerta_cumplimiento_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE comun.bitacora_evento
  ADD CONSTRAINT fk_bitacora_evento_actor_usuario_id
  FOREIGN KEY (actor_usuario_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE comun.bitacora_evento
  ADD CONSTRAINT fk_bitacora_evento_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE comun.bitacora_evento
  ADD CONSTRAINT fk_bitacora_evento_suplantando_a_usuario_id
  FOREIGN KEY (suplantando_a_usuario_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE auditoria.coincidencia_lista
  ADD CONSTRAINT fk_coincidencia_lista_lista_id
  FOREIGN KEY (lista_id) REFERENCES auditoria.lista_restrictiva_externa (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE auditoria.coincidencia_lista
  ADD CONSTRAINT fk_coincidencia_lista_revisada_por
  FOREIGN KEY (revisada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE auditoria.coincidencia_lista
  ADD CONSTRAINT fk_coincidencia_lista_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE auditoria.ejecucion_reporte
  ADD CONSTRAINT fk_ejecucion_reporte_definicion_id
  FOREIGN KEY (definicion_id) REFERENCES auditoria.definicion_reporte (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE auditoria.ejecucion_reporte
  ADD CONSTRAINT fk_ejecucion_reporte_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE auditoria.ejecucion_reporte
  ADD CONSTRAINT fk_ejecucion_reporte_solicitado_por
  FOREIGN KEY (solicitado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE auditoria.exportacion_reporte
  ADD CONSTRAINT fk_exportacion_reporte_ejecucion_id
  FOREIGN KEY (ejecucion_id) REFERENCES auditoria.ejecucion_reporte (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE auditoria.proceso_anonimizacion
  ADD CONSTRAINT fk_proceso_anonimizacion_solicitud_id
  FOREIGN KEY (solicitud_id) REFERENCES auditoria.solicitud_datos_personales (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE auditoria.proceso_anonimizacion
  ADD CONSTRAINT fk_proceso_anonimizacion_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE auditoria.programacion_reporte
  ADD CONSTRAINT fk_programacion_reporte_definicion_id
  FOREIGN KEY (definicion_id) REFERENCES auditoria.definicion_reporte (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE comun.registro_acceso_datos
  ADD CONSTRAINT fk_registro_acceso_datos_usuario_afectado_id
  FOREIGN KEY (usuario_afectado_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE comun.registro_acceso_datos
  ADD CONSTRAINT fk_registro_acceso_datos_usuario_consultor_id
  FOREIGN KEY (usuario_consultor_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE auditoria.reporte_operacion_sospechosa
  ADD CONSTRAINT fk_reporte_operacion_sospechosa_aprobado_por
  FOREIGN KEY (aprobado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE auditoria.reporte_operacion_sospechosa
  ADD CONSTRAINT fk_reporte_operacion_sospechosa_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE auditoria.solicitud_datos_personales
  ADD CONSTRAINT fk_solicitud_datos_personales_atendida_por
  FOREIGN KEY (atendida_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE auditoria.solicitud_datos_personales
  ADD CONSTRAINT fk_solicitud_datos_personales_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE auditoria.ticket_soporte
  ADD CONSTRAINT fk_ticket_soporte_asignado_a
  FOREIGN KEY (asignado_a) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE auditoria.ticket_soporte
  ADD CONSTRAINT fk_ticket_soporte_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;
