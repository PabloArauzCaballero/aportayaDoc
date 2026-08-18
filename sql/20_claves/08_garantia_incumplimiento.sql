-- Claves foráneas del módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
-- Generado por scripts/generar_ddl.py — no editar a mano.
-- Se aplican después de crear todas las tablas: el modelo tiene
-- referencias circulares entre módulos.

ALTER TABLE garantia.abono_recuperacion
  ADD CONSTRAINT fk_abono_recuperacion_deuda_id
  FOREIGN KEY (deuda_id) REFERENCES garantia.deuda_participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.abono_recuperacion
  ADD CONSTRAINT fk_abono_recuperacion_entrega_id
  FOREIGN KEY (entrega_id) REFERENCES entregas.entrega_fondo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.abono_recuperacion
  ADD CONSTRAINT fk_abono_recuperacion_movimiento_fondo_id
  FOREIGN KEY (movimiento_fondo_id) REFERENCES garantia.movimiento_fondo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.abono_recuperacion
  ADD CONSTRAINT fk_abono_recuperacion_pago_id
  FOREIGN KEY (pago_id) REFERENCES aportes.pago (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.abono_recuperacion
  ADD CONSTRAINT fk_abono_recuperacion_registrado_por
  FOREIGN KEY (registrado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.accion_cobranza
  ADD CONSTRAINT fk_accion_cobranza_ejecutada_por
  FOREIGN KEY (ejecutada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.accion_cobranza
  ADD CONSTRAINT fk_accion_cobranza_gestion_id
  FOREIGN KEY (gestion_id) REFERENCES garantia.gestion_cobranza (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.accion_cobranza
  ADD CONSTRAINT fk_accion_cobranza_notificacion_id
  FOREIGN KEY (notificacion_id) REFERENCES notificaciones.notificacion (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.acuerdo_quita
  ADD CONSTRAINT fk_acuerdo_quita_acuerdo_grupo_id
  FOREIGN KEY (acuerdo_grupo_id) REFERENCES grupos.acuerdo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.acuerdo_quita
  ADD CONSTRAINT fk_acuerdo_quita_aprobado_por
  FOREIGN KEY (aprobado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.acuerdo_quita
  ADD CONSTRAINT fk_acuerdo_quita_registro_id
  FOREIGN KEY (registro_id) REFERENCES garantia.registro_incumplimiento (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.alerta_temprana
  ADD CONSTRAINT fk_alerta_temprana_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.alerta_temprana
  ADD CONSTRAINT fk_alerta_temprana_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.apelacion_sancion
  ADD CONSTRAINT fk_apelacion_sancion_apelante_id
  FOREIGN KEY (apelante_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.apelacion_sancion
  ADD CONSTRAINT fk_apelacion_sancion_resuelta_por
  FOREIGN KEY (resuelta_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.apelacion_sancion
  ADD CONSTRAINT fk_apelacion_sancion_sancion_id
  FOREIGN KEY (sancion_id) REFERENCES garantia.sancion (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.aval_participante
  ADD CONSTRAINT fk_aval_participante_avalista_usuario_id
  FOREIGN KEY (avalista_usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.aval_participante
  ADD CONSTRAINT fk_aval_participante_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.aval_participante
  ADD CONSTRAINT fk_aval_participante_participante_avalado_id
  FOREIGN KEY (participante_avalado_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.aval_participante
  ADD CONSTRAINT fk_aval_participante_token_aceptacion_id
  FOREIGN KEY (token_aceptacion_id) REFERENCES identidad.token_verificacion (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.candidato_reemplazo
  ADD CONSTRAINT fk_candidato_reemplazo_reemplazo_id
  FOREIGN KEY (reemplazo_id) REFERENCES garantia.reemplazo_participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.candidato_reemplazo
  ADD CONSTRAINT fk_candidato_reemplazo_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.castigo_deuda
  ADD CONSTRAINT fk_castigo_deuda_aprobado_por
  FOREIGN KEY (aprobado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.castigo_deuda
  ADD CONSTRAINT fk_castigo_deuda_asiento_contable_id
  FOREIGN KEY (asiento_contable_id) REFERENCES nucleo_financiero.asiento_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.castigo_deuda
  ADD CONSTRAINT fk_castigo_deuda_deuda_id
  FOREIGN KEY (deuda_id) REFERENCES garantia.deuda_participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.cobertura_incumplimiento
  ADD CONSTRAINT fk_cobertura_incumplimiento_aprobada_por
  FOREIGN KEY (aprobada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.cobertura_incumplimiento
  ADD CONSTRAINT fk_cobertura_incumplimiento_asiento_contable_id
  FOREIGN KEY (asiento_contable_id) REFERENCES nucleo_financiero.asiento_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.cobertura_incumplimiento
  ADD CONSTRAINT fk_cobertura_incumplimiento_fondo_id
  FOREIGN KEY (fondo_id) REFERENCES garantia.fondo_garantia (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.cobertura_incumplimiento
  ADD CONSTRAINT fk_cobertura_incumplimiento_movimiento_fondo_id
  FOREIGN KEY (movimiento_fondo_id) REFERENCES garantia.movimiento_fondo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.cobertura_incumplimiento
  ADD CONSTRAINT fk_cobertura_incumplimiento_obligacion_id
  FOREIGN KEY (obligacion_id) REFERENCES aportes.obligacion_aporte (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.cobertura_incumplimiento
  ADD CONSTRAINT fk_cobertura_incumplimiento_periodo_id
  FOREIGN KEY (periodo_id) REFERENCES grupos.periodo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.cobertura_incumplimiento
  ADD CONSTRAINT fk_cobertura_incumplimiento_registro_id
  FOREIGN KEY (registro_id) REFERENCES garantia.registro_incumplimiento (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.descargo_participante
  ADD CONSTRAINT fk_descargo_participante_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.descargo_participante
  ADD CONSTRAINT fk_descargo_participante_registro_id
  FOREIGN KEY (registro_id) REFERENCES garantia.registro_incumplimiento (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.descargo_participante
  ADD CONSTRAINT fk_descargo_participante_resuelto_por
  FOREIGN KEY (resuelto_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.deuda_participante
  ADD CONSTRAINT fk_deuda_participante_cobertura_id
  FOREIGN KEY (cobertura_id) REFERENCES garantia.cobertura_incumplimiento (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.deuda_participante
  ADD CONSTRAINT fk_deuda_participante_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.deuda_participante
  ADD CONSTRAINT fk_deuda_participante_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.deuda_participante
  ADD CONSTRAINT fk_deuda_participante_registro_id
  FOREIGN KEY (registro_id) REFERENCES garantia.registro_incumplimiento (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.deuda_participante
  ADD CONSTRAINT fk_deuda_participante_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.devolucion_fondo
  ADD CONSTRAINT fk_devolucion_fondo_fondo_id
  FOREIGN KEY (fondo_id) REFERENCES garantia.fondo_garantia (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.devolucion_fondo
  ADD CONSTRAINT fk_devolucion_fondo_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.disolucion_anticipada
  ADD CONSTRAINT fk_disolucion_anticipada_acuerdo_grupo_id
  FOREIGN KEY (acuerdo_grupo_id) REFERENCES grupos.acuerdo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.disolucion_anticipada
  ADD CONSTRAINT fk_disolucion_anticipada_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.ejecucion_aval
  ADD CONSTRAINT fk_ejecucion_aval_aval_id
  FOREIGN KEY (aval_id) REFERENCES garantia.aval_participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.ejecucion_aval
  ADD CONSTRAINT fk_ejecucion_aval_deuda_id
  FOREIGN KEY (deuda_id) REFERENCES garantia.deuda_participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.ejecucion_aval
  ADD CONSTRAINT fk_ejecucion_aval_pago_id
  FOREIGN KEY (pago_id) REFERENCES aportes.pago (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.ejecucion_aval
  ADD CONSTRAINT fk_ejecucion_aval_registro_id
  FOREIGN KEY (registro_id) REFERENCES garantia.registro_incumplimiento (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.evidencia_incumplimiento
  ADD CONSTRAINT fk_evidencia_incumplimiento_aportada_por
  FOREIGN KEY (aportada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.evidencia_incumplimiento
  ADD CONSTRAINT fk_evidencia_incumplimiento_registro_id
  FOREIGN KEY (registro_id) REFERENCES garantia.registro_incumplimiento (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.fondo_garantia
  ADD CONSTRAINT fk_fondo_garantia_cuenta_contable_id
  FOREIGN KEY (cuenta_contable_id) REFERENCES nucleo_financiero.cuenta_contable (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.fondo_garantia
  ADD CONSTRAINT fk_fondo_garantia_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.fondo_garantia
  ADD CONSTRAINT fk_fondo_garantia_politica_cobertura_id
  FOREIGN KEY (politica_cobertura_id) REFERENCES garantia.politica_cobertura (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.gestion_cobranza
  ADD CONSTRAINT fk_gestion_cobranza_estrategia_id
  FOREIGN KEY (estrategia_id) REFERENCES garantia.estrategia_cobranza (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.gestion_cobranza
  ADD CONSTRAINT fk_gestion_cobranza_gestor_asignado_id
  FOREIGN KEY (gestor_asignado_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.gestion_cobranza
  ADD CONSTRAINT fk_gestion_cobranza_registro_id
  FOREIGN KEY (registro_id) REFERENCES garantia.registro_incumplimiento (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.historial_estado_incumplimiento
  ADD CONSTRAINT fk_historial_estado_incumplimiento_ejecutado_por
  FOREIGN KEY (ejecutado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.historial_estado_incumplimiento
  ADD CONSTRAINT fk_historial_estado_incumplimiento_registro_id
  FOREIGN KEY (registro_id) REFERENCES garantia.registro_incumplimiento (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.historial_incumplimiento_usuario
  ADD CONSTRAINT fk_historial_incumplimiento_usuario_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.liquidacion_participante
  ADD CONSTRAINT fk_liquidacion_participante_disolucion_id
  FOREIGN KEY (disolucion_id) REFERENCES garantia.disolucion_anticipada (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.liquidacion_participante
  ADD CONSTRAINT fk_liquidacion_participante_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.lista_restriccion_interna
  ADD CONSTRAINT fk_lista_restriccion_interna_registro_origen_id
  FOREIGN KEY (registro_origen_id) REFERENCES garantia.registro_incumplimiento (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.lista_restriccion_interna
  ADD CONSTRAINT fk_lista_restriccion_interna_retirado_por
  FOREIGN KEY (retirado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.lista_restriccion_interna
  ADD CONSTRAINT fk_lista_restriccion_interna_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.matriz_sancion
  ADD CONSTRAINT fk_matriz_sancion_politica_id
  FOREIGN KEY (politica_id) REFERENCES garantia.politica_sancion (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.movimiento_fondo
  ADD CONSTRAINT fk_movimiento_fondo_asiento_contable_id
  FOREIGN KEY (asiento_contable_id) REFERENCES nucleo_financiero.asiento_contable (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.movimiento_fondo
  ADD CONSTRAINT fk_movimiento_fondo_fondo_id
  FOREIGN KEY (fondo_id) REFERENCES garantia.fondo_garantia (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.movimiento_fondo
  ADD CONSTRAINT fk_movimiento_fondo_registrado_por
  FOREIGN KEY (registrado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.plan_contingencia
  ADD CONSTRAINT fk_plan_contingencia_acuerdo_grupo_id
  FOREIGN KEY (acuerdo_grupo_id) REFERENCES grupos.acuerdo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.plan_contingencia
  ADD CONSTRAINT fk_plan_contingencia_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.politica_cobertura
  ADD CONSTRAINT fk_politica_cobertura_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.politica_sancion
  ADD CONSTRAINT fk_politica_sancion_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.promesa_pago
  ADD CONSTRAINT fk_promesa_pago_gestion_id
  FOREIGN KEY (gestion_id) REFERENCES garantia.gestion_cobranza (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.promesa_pago
  ADD CONSTRAINT fk_promesa_pago_registrada_por
  FOREIGN KEY (registrada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.reemplazo_participante
  ADD CONSTRAINT fk_reemplazo_participante_acuerdo_grupo_id
  FOREIGN KEY (acuerdo_grupo_id) REFERENCES grupos.acuerdo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.reemplazo_participante
  ADD CONSTRAINT fk_reemplazo_participante_cupo_id
  FOREIGN KEY (cupo_id) REFERENCES grupos.cupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.reemplazo_participante
  ADD CONSTRAINT fk_reemplazo_participante_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.reemplazo_participante
  ADD CONSTRAINT fk_reemplazo_participante_participante_entrante_id
  FOREIGN KEY (participante_entrante_id) REFERENCES grupos.participante (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.reemplazo_participante
  ADD CONSTRAINT fk_reemplazo_participante_participante_saliente_id
  FOREIGN KEY (participante_saliente_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.reemplazo_participante
  ADD CONSTRAINT fk_reemplazo_participante_registro_id
  FOREIGN KEY (registro_id) REFERENCES garantia.registro_incumplimiento (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.registro_incumplimiento
  ADD CONSTRAINT fk_registro_incumplimiento_cupo_id
  FOREIGN KEY (cupo_id) REFERENCES grupos.cupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.registro_incumplimiento
  ADD CONSTRAINT fk_registro_incumplimiento_entrega_afectada_id
  FOREIGN KEY (entrega_afectada_id) REFERENCES entregas.entrega_fondo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.registro_incumplimiento
  ADD CONSTRAINT fk_registro_incumplimiento_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.registro_incumplimiento
  ADD CONSTRAINT fk_registro_incumplimiento_obligacion_id
  FOREIGN KEY (obligacion_id) REFERENCES aportes.obligacion_aporte (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.registro_incumplimiento
  ADD CONSTRAINT fk_registro_incumplimiento_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.registro_incumplimiento
  ADD CONSTRAINT fk_registro_incumplimiento_periodo_id
  FOREIGN KEY (periodo_id) REFERENCES grupos.periodo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.registro_incumplimiento
  ADD CONSTRAINT fk_registro_incumplimiento_reportado_por
  FOREIGN KEY (reportado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.registro_incumplimiento
  ADD CONSTRAINT fk_registro_incumplimiento_responsable_gestion
  FOREIGN KEY (responsable_gestion) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.registro_incumplimiento
  ADD CONSTRAINT fk_registro_incumplimiento_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.sancion
  ADD CONSTRAINT fk_sancion_acuerdo_grupo_id
  FOREIGN KEY (acuerdo_grupo_id) REFERENCES grupos.acuerdo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.sancion
  ADD CONSTRAINT fk_sancion_aplicada_por
  FOREIGN KEY (aplicada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.sancion
  ADD CONSTRAINT fk_sancion_matriz_id
  FOREIGN KEY (matriz_id) REFERENCES garantia.matriz_sancion (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.sancion
  ADD CONSTRAINT fk_sancion_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.sancion
  ADD CONSTRAINT fk_sancion_registro_id
  FOREIGN KEY (registro_id) REFERENCES garantia.registro_incumplimiento (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.sancion
  ADD CONSTRAINT fk_sancion_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.score_riesgo_incumplimiento
  ADD CONSTRAINT fk_score_riesgo_incumplimiento_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE garantia.score_riesgo_incumplimiento
  ADD CONSTRAINT fk_score_riesgo_incumplimiento_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.subrogacion
  ADD CONSTRAINT fk_subrogacion_cobertura_id
  FOREIGN KEY (cobertura_id) REFERENCES garantia.cobertura_incumplimiento (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE garantia.subrogacion
  ADD CONSTRAINT fk_subrogacion_deuda_id
  FOREIGN KEY (deuda_id) REFERENCES garantia.deuda_participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;
