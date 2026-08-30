-- Claves foráneas del módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- Generado por scripts/generar_ddl.py — no editar a mano.
-- Se aplican después de crear todas las tablas: el modelo tiene
-- referencias circulares entre módulos.
--
-- Cada una se borra si existe antes de crearse: PostgreSQL no tiene
-- ADD CONSTRAINT IF NOT EXISTS, y sql/aplicar.sql se aplica también
-- sobre una base que ya lo tiene. Borrar y volver a crear —en vez de
-- saltear si ya está— es lo que hace que un ON DELETE cambiado en el
-- modelo quede corregido al reaplicar.

ALTER TABLE grupos.aceptacion_reglamento DROP CONSTRAINT IF EXISTS fk_aceptacion_reglamento_participante_id;
ALTER TABLE grupos.aceptacion_reglamento
  ADD CONSTRAINT fk_aceptacion_reglamento_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.aceptacion_reglamento DROP CONSTRAINT IF EXISTS fk_aceptacion_reglamento_reglamento_id;
ALTER TABLE grupos.aceptacion_reglamento
  ADD CONSTRAINT fk_aceptacion_reglamento_reglamento_id
  FOREIGN KEY (reglamento_id) REFERENCES grupos.reglamento_grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.aceptacion_reglamento DROP CONSTRAINT IF EXISTS fk_aceptacion_reglamento_token_firma_id;
ALTER TABLE grupos.aceptacion_reglamento
  ADD CONSTRAINT fk_aceptacion_reglamento_token_firma_id
  FOREIGN KEY (token_firma_id) REFERENCES identidad.token_verificacion (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE grupos.acuerdo DROP CONSTRAINT IF EXISTS fk_acuerdo_grupo_id;
ALTER TABLE grupos.acuerdo
  ADD CONSTRAINT fk_acuerdo_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.acuerdo DROP CONSTRAINT IF EXISTS fk_acuerdo_propuesto_por;
ALTER TABLE grupos.acuerdo
  ADD CONSTRAINT fk_acuerdo_propuesto_por
  FOREIGN KEY (propuesto_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.configuracion_grupo DROP CONSTRAINT IF EXISTS fk_configuracion_grupo_grupo_id;
ALTER TABLE grupos.configuracion_grupo
  ADD CONSTRAINT fk_configuracion_grupo_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.configuracion_grupo DROP CONSTRAINT IF EXISTS fk_configuracion_grupo_politica_mora_id;
ALTER TABLE grupos.configuracion_grupo
  ADD CONSTRAINT fk_configuracion_grupo_politica_mora_id
  FOREIGN KEY (politica_mora_id) REFERENCES aportes.politica_mora (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE grupos.configuracion_grupo DROP CONSTRAINT IF EXISTS fk_configuracion_grupo_politica_sancion_id;
ALTER TABLE grupos.configuracion_grupo
  ADD CONSTRAINT fk_configuracion_grupo_politica_sancion_id
  FOREIGN KEY (politica_sancion_id) REFERENCES garantia.politica_sancion (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE grupos.cupo DROP CONSTRAINT IF EXISTS fk_cupo_grupo_id;
ALTER TABLE grupos.cupo
  ADD CONSTRAINT fk_cupo_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.cupo DROP CONSTRAINT IF EXISTS fk_cupo_participante_id;
ALTER TABLE grupos.cupo
  ADD CONSTRAINT fk_cupo_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE catalogo.dia_no_habil DROP CONSTRAINT IF EXISTS fk_dia_no_habil_grupo_id;
ALTER TABLE catalogo.dia_no_habil
  ADD CONSTRAINT fk_dia_no_habil_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE grupos.grupo DROP CONSTRAINT IF EXISTS fk_grupo_organizador_id;
ALTER TABLE grupos.grupo
  ADD CONSTRAINT fk_grupo_organizador_id
  FOREIGN KEY (organizador_id) REFERENCES organizador.organizador (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE grupos.historial_estado_grupo DROP CONSTRAINT IF EXISTS fk_historial_estado_grupo_ejecutado_por;
ALTER TABLE grupos.historial_estado_grupo
  ADD CONSTRAINT fk_historial_estado_grupo_ejecutado_por
  FOREIGN KEY (ejecutado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.historial_estado_grupo DROP CONSTRAINT IF EXISTS fk_historial_estado_grupo_grupo_id;
ALTER TABLE grupos.historial_estado_grupo
  ADD CONSTRAINT fk_historial_estado_grupo_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.invitacion DROP CONSTRAINT IF EXISTS fk_invitacion_emisor_id;
ALTER TABLE grupos.invitacion
  ADD CONSTRAINT fk_invitacion_emisor_id
  FOREIGN KEY (emisor_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.invitacion DROP CONSTRAINT IF EXISTS fk_invitacion_grupo_id;
ALTER TABLE grupos.invitacion
  ADD CONSTRAINT fk_invitacion_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.invitacion DROP CONSTRAINT IF EXISTS fk_invitacion_token_id;
ALTER TABLE grupos.invitacion
  ADD CONSTRAINT fk_invitacion_token_id
  FOREIGN KEY (token_id) REFERENCES identidad.token_verificacion (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.participante DROP CONSTRAINT IF EXISTS fk_participante_grupo_id;
ALTER TABLE grupos.participante
  ADD CONSTRAINT fk_participante_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.participante DROP CONSTRAINT IF EXISTS fk_participante_invitado_por_id;
ALTER TABLE grupos.participante
  ADD CONSTRAINT fk_participante_invitado_por_id
  FOREIGN KEY (invitado_por_id) REFERENCES grupos.participante (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE grupos.participante DROP CONSTRAINT IF EXISTS fk_participante_usuario_id;
ALTER TABLE grupos.participante
  ADD CONSTRAINT fk_participante_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.periodo DROP CONSTRAINT IF EXISTS fk_periodo_grupo_id;
ALTER TABLE grupos.periodo
  ADD CONSTRAINT fk_periodo_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.postulacion_emparejamiento DROP CONSTRAINT IF EXISTS fk_postulacion_emparejamiento_usuario_id;
ALTER TABLE grupos.postulacion_emparejamiento
  ADD CONSTRAINT fk_postulacion_emparejamiento_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.propuesta_grupo DROP CONSTRAINT IF EXISTS fk_propuesta_grupo_criterio_id;
ALTER TABLE grupos.propuesta_grupo
  ADD CONSTRAINT fk_propuesta_grupo_criterio_id
  FOREIGN KEY (criterio_id) REFERENCES grupos.criterio_emparejamiento (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.propuesta_grupo DROP CONSTRAINT IF EXISTS fk_propuesta_grupo_grupo_materializado_id;
ALTER TABLE grupos.propuesta_grupo
  ADD CONSTRAINT fk_propuesta_grupo_grupo_materializado_id
  FOREIGN KEY (grupo_materializado_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE grupos.propuesta_postulacion DROP CONSTRAINT IF EXISTS fk_propuesta_postulacion_postulacion_id;
ALTER TABLE grupos.propuesta_postulacion
  ADD CONSTRAINT fk_propuesta_postulacion_postulacion_id
  FOREIGN KEY (postulacion_id) REFERENCES grupos.postulacion_emparejamiento (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.propuesta_postulacion DROP CONSTRAINT IF EXISTS fk_propuesta_postulacion_propuesta_id;
ALTER TABLE grupos.propuesta_postulacion
  ADD CONSTRAINT fk_propuesta_postulacion_propuesta_id
  FOREIGN KEY (propuesta_id) REFERENCES grupos.propuesta_grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.reglamento_grupo DROP CONSTRAINT IF EXISTS fk_reglamento_grupo_grupo_id;
ALTER TABLE grupos.reglamento_grupo
  ADD CONSTRAINT fk_reglamento_grupo_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.reglamento_grupo DROP CONSTRAINT IF EXISTS fk_reglamento_grupo_redactado_por;
ALTER TABLE grupos.reglamento_grupo
  ADD CONSTRAINT fk_reglamento_grupo_redactado_por
  FOREIGN KEY (redactado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.solicitud_ingreso DROP CONSTRAINT IF EXISTS fk_solicitud_ingreso_grupo_id;
ALTER TABLE grupos.solicitud_ingreso
  ADD CONSTRAINT fk_solicitud_ingreso_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.solicitud_ingreso DROP CONSTRAINT IF EXISTS fk_solicitud_ingreso_revisada_por;
ALTER TABLE grupos.solicitud_ingreso
  ADD CONSTRAINT fk_solicitud_ingreso_revisada_por
  FOREIGN KEY (revisada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE grupos.solicitud_ingreso DROP CONSTRAINT IF EXISTS fk_solicitud_ingreso_usuario_id;
ALTER TABLE grupos.solicitud_ingreso
  ADD CONSTRAINT fk_solicitud_ingreso_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.solicitud_permuta DROP CONSTRAINT IF EXISTS fk_solicitud_permuta_contraparte_id;
ALTER TABLE grupos.solicitud_permuta
  ADD CONSTRAINT fk_solicitud_permuta_contraparte_id
  FOREIGN KEY (contraparte_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.solicitud_permuta DROP CONSTRAINT IF EXISTS fk_solicitud_permuta_solicitante_id;
ALTER TABLE grupos.solicitud_permuta
  ADD CONSTRAINT fk_solicitud_permuta_solicitante_id
  FOREIGN KEY (solicitante_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.solicitud_permuta DROP CONSTRAINT IF EXISTS fk_solicitud_permuta_turno_destino_id;
ALTER TABLE grupos.solicitud_permuta
  ADD CONSTRAINT fk_solicitud_permuta_turno_destino_id
  FOREIGN KEY (turno_destino_id) REFERENCES grupos.turno (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.solicitud_permuta DROP CONSTRAINT IF EXISTS fk_solicitud_permuta_turno_origen_id;
ALTER TABLE grupos.solicitud_permuta
  ADD CONSTRAINT fk_solicitud_permuta_turno_origen_id
  FOREIGN KEY (turno_origen_id) REFERENCES grupos.turno (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.solicitud_retiro DROP CONSTRAINT IF EXISTS fk_solicitud_retiro_participante_id;
ALTER TABLE grupos.solicitud_retiro
  ADD CONSTRAINT fk_solicitud_retiro_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.solicitud_retiro DROP CONSTRAINT IF EXISTS fk_solicitud_retiro_plan_regularizacion_id;
ALTER TABLE grupos.solicitud_retiro
  ADD CONSTRAINT fk_solicitud_retiro_plan_regularizacion_id
  FOREIGN KEY (plan_regularizacion_id) REFERENCES aportes.plan_regularizacion (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE grupos.sorteo_turnos DROP CONSTRAINT IF EXISTS fk_sorteo_turnos_ejecutado_por;
ALTER TABLE grupos.sorteo_turnos
  ADD CONSTRAINT fk_sorteo_turnos_ejecutado_por
  FOREIGN KEY (ejecutado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.sorteo_turnos DROP CONSTRAINT IF EXISTS fk_sorteo_turnos_grupo_id;
ALTER TABLE grupos.sorteo_turnos
  ADD CONSTRAINT fk_sorteo_turnos_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.traspaso_cupo DROP CONSTRAINT IF EXISTS fk_traspaso_cupo_aprobado_por_acuerdo_id;
ALTER TABLE grupos.traspaso_cupo
  ADD CONSTRAINT fk_traspaso_cupo_aprobado_por_acuerdo_id
  FOREIGN KEY (aprobado_por_acuerdo_id) REFERENCES grupos.acuerdo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE grupos.traspaso_cupo DROP CONSTRAINT IF EXISTS fk_traspaso_cupo_cupo_id;
ALTER TABLE grupos.traspaso_cupo
  ADD CONSTRAINT fk_traspaso_cupo_cupo_id
  FOREIGN KEY (cupo_id) REFERENCES grupos.cupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.traspaso_cupo DROP CONSTRAINT IF EXISTS fk_traspaso_cupo_participante_destino_id;
ALTER TABLE grupos.traspaso_cupo
  ADD CONSTRAINT fk_traspaso_cupo_participante_destino_id
  FOREIGN KEY (participante_destino_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.traspaso_cupo DROP CONSTRAINT IF EXISTS fk_traspaso_cupo_participante_origen_id;
ALTER TABLE grupos.traspaso_cupo
  ADD CONSTRAINT fk_traspaso_cupo_participante_origen_id
  FOREIGN KEY (participante_origen_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.turno DROP CONSTRAINT IF EXISTS fk_turno_cupo_id;
ALTER TABLE grupos.turno
  ADD CONSTRAINT fk_turno_cupo_id
  FOREIGN KEY (cupo_id) REFERENCES grupos.cupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.turno DROP CONSTRAINT IF EXISTS fk_turno_grupo_id;
ALTER TABLE grupos.turno
  ADD CONSTRAINT fk_turno_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.turno DROP CONSTRAINT IF EXISTS fk_turno_periodo_id;
ALTER TABLE grupos.turno
  ADD CONSTRAINT fk_turno_periodo_id
  FOREIGN KEY (periodo_id) REFERENCES grupos.periodo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.turno DROP CONSTRAINT IF EXISTS fk_turno_permutado_con_turno_id;
ALTER TABLE grupos.turno
  ADD CONSTRAINT fk_turno_permutado_con_turno_id
  FOREIGN KEY (permutado_con_turno_id) REFERENCES grupos.turno (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE grupos.voto_participante DROP CONSTRAINT IF EXISTS fk_voto_participante_acuerdo_id;
ALTER TABLE grupos.voto_participante
  ADD CONSTRAINT fk_voto_participante_acuerdo_id
  FOREIGN KEY (acuerdo_id) REFERENCES grupos.acuerdo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE grupos.voto_participante DROP CONSTRAINT IF EXISTS fk_voto_participante_participante_id;
ALTER TABLE grupos.voto_participante
  ADD CONSTRAINT fk_voto_participante_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;
