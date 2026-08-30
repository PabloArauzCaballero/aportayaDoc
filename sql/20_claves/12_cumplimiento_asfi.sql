-- Claves foráneas del módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- Generado por scripts/generar_ddl.py — no editar a mano.
-- Se aplican después de crear todas las tablas: el modelo tiene
-- referencias circulares entre módulos.
--
-- Cada una se borra si existe antes de crearse: PostgreSQL no tiene
-- ADD CONSTRAINT IF NOT EXISTS, y sql/aplicar.sql se aplica también
-- sobre una base que ya lo tiene. Borrar y volver a crear —en vez de
-- saltear si ya está— es lo que hace que un ON DELETE cambiado en el
-- modelo quede corregido al reaplicar.

ALTER TABLE cumplimiento.aceptacion_contrato DROP CONSTRAINT IF EXISTS fk_aceptacion_contrato_contrato_adhesion_id;
ALTER TABLE cumplimiento.aceptacion_contrato
  ADD CONSTRAINT fk_aceptacion_contrato_contrato_adhesion_id
  FOREIGN KEY (contrato_adhesion_id) REFERENCES cumplimiento.contrato_adhesion (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.aceptacion_contrato DROP CONSTRAINT IF EXISTS fk_aceptacion_contrato_dispositivo_id;
ALTER TABLE cumplimiento.aceptacion_contrato
  ADD CONSTRAINT fk_aceptacion_contrato_dispositivo_id
  FOREIGN KEY (dispositivo_id) REFERENCES identidad.dispositivo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.aceptacion_contrato DROP CONSTRAINT IF EXISTS fk_aceptacion_contrato_token_firma_id;
ALTER TABLE cumplimiento.aceptacion_contrato
  ADD CONSTRAINT fk_aceptacion_contrato_token_firma_id
  FOREIGN KEY (token_firma_id) REFERENCES identidad.token_verificacion (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.aceptacion_contrato DROP CONSTRAINT IF EXISTS fk_aceptacion_contrato_usuario_id;
ALTER TABLE cumplimiento.aceptacion_contrato
  ADD CONSTRAINT fk_aceptacion_contrato_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.acta_comite DROP CONSTRAINT IF EXISTS fk_acta_comite_comite_gobierno_id;
ALTER TABLE cumplimiento.acta_comite
  ADD CONSTRAINT fk_acta_comite_comite_gobierno_id
  FOREIGN KEY (comite_gobierno_id) REFERENCES cumplimiento.comite_gobierno (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.acta_comite DROP CONSTRAINT IF EXISTS fk_acta_comite_elaborada_por;
ALTER TABLE cumplimiento.acta_comite
  ADD CONSTRAINT fk_acta_comite_elaborada_por
  FOREIGN KEY (elaborada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.activo_informacion DROP CONSTRAINT IF EXISTS fk_activo_informacion_contrato_tercero_id;
ALTER TABLE cumplimiento.activo_informacion
  ADD CONSTRAINT fk_activo_informacion_contrato_tercero_id
  FOREIGN KEY (contrato_tercero_id) REFERENCES cumplimiento.contrato_tercero (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.activo_informacion DROP CONSTRAINT IF EXISTS fk_activo_informacion_custodio_id;
ALTER TABLE cumplimiento.activo_informacion
  ADD CONSTRAINT fk_activo_informacion_custodio_id
  FOREIGN KEY (custodio_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.activo_informacion DROP CONSTRAINT IF EXISTS fk_activo_informacion_propietario_id;
ALTER TABLE cumplimiento.activo_informacion
  ADD CONSTRAINT fk_activo_informacion_propietario_id
  FOREIGN KEY (propietario_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.alerta_monitoreo_lft DROP CONSTRAINT IF EXISTS fk_alerta_monitoreo_lft_asignada_a;
ALTER TABLE cumplimiento.alerta_monitoreo_lft
  ADD CONSTRAINT fk_alerta_monitoreo_lft_asignada_a
  FOREIGN KEY (asignada_a) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.alerta_monitoreo_lft DROP CONSTRAINT IF EXISTS fk_alerta_monitoreo_lft_caso_id;
ALTER TABLE cumplimiento.alerta_monitoreo_lft
  ADD CONSTRAINT fk_alerta_monitoreo_lft_caso_id
  FOREIGN KEY (caso_id) REFERENCES cumplimiento.caso_investigacion_lft (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.alerta_monitoreo_lft DROP CONSTRAINT IF EXISTS fk_alerta_monitoreo_lft_cuenta_billetera_id;
ALTER TABLE cumplimiento.alerta_monitoreo_lft
  ADD CONSTRAINT fk_alerta_monitoreo_lft_cuenta_billetera_id
  FOREIGN KEY (cuenta_billetera_id) REFERENCES nucleo_financiero.cuenta_billetera (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.alerta_monitoreo_lft DROP CONSTRAINT IF EXISTS fk_alerta_monitoreo_lft_regla_monitoreo_id;
ALTER TABLE cumplimiento.alerta_monitoreo_lft
  ADD CONSTRAINT fk_alerta_monitoreo_lft_regla_monitoreo_id
  FOREIGN KEY (regla_monitoreo_id) REFERENCES cumplimiento.regla_monitoreo_lft (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.alerta_monitoreo_lft DROP CONSTRAINT IF EXISTS fk_alerta_monitoreo_lft_transaccion_id;
ALTER TABLE cumplimiento.alerta_monitoreo_lft
  ADD CONSTRAINT fk_alerta_monitoreo_lft_transaccion_id
  FOREIGN KEY (transaccion_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.alerta_monitoreo_lft DROP CONSTRAINT IF EXISTS fk_alerta_monitoreo_lft_usuario_id;
ALTER TABLE cumplimiento.alerta_monitoreo_lft
  ADD CONSTRAINT fk_alerta_monitoreo_lft_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.beneficiario_final DROP CONSTRAINT IF EXISTS fk_beneficiario_final_usuario_id;
ALTER TABLE cumplimiento.beneficiario_final
  ADD CONSTRAINT fk_beneficiario_final_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.calificacion_riesgo_cliente DROP CONSTRAINT IF EXISTS fk_calificacion_riesgo_cliente_calificado_por;
ALTER TABLE cumplimiento.calificacion_riesgo_cliente
  ADD CONSTRAINT fk_calificacion_riesgo_cliente_calificado_por
  FOREIGN KEY (calificado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.calificacion_riesgo_cliente DROP CONSTRAINT IF EXISTS fk_calificacion_riesgo_cliente_matriz_riesgo_id;
ALTER TABLE cumplimiento.calificacion_riesgo_cliente
  ADD CONSTRAINT fk_calificacion_riesgo_cliente_matriz_riesgo_id
  FOREIGN KEY (matriz_riesgo_id) REFERENCES cumplimiento.matriz_riesgo_lft (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.calificacion_riesgo_cliente DROP CONSTRAINT IF EXISTS fk_calificacion_riesgo_cliente_usuario_id;
ALTER TABLE cumplimiento.calificacion_riesgo_cliente
  ADD CONSTRAINT fk_calificacion_riesgo_cliente_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.capacitacion_cumplimiento DROP CONSTRAINT IF EXISTS fk_capacitacion_cumplimiento_usuario_id;
ALTER TABLE cumplimiento.capacitacion_cumplimiento
  ADD CONSTRAINT fk_capacitacion_cumplimiento_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.caso_investigacion_lft DROP CONSTRAINT IF EXISTS fk_caso_investigacion_lft_analista_id;
ALTER TABLE cumplimiento.caso_investigacion_lft
  ADD CONSTRAINT fk_caso_investigacion_lft_analista_id
  FOREIGN KEY (analista_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.caso_investigacion_lft DROP CONSTRAINT IF EXISTS fk_caso_investigacion_lft_reporte_operacion_sospechosa_id;
ALTER TABLE cumplimiento.caso_investigacion_lft
  ADD CONSTRAINT fk_caso_investigacion_lft_reporte_operacion_sospechosa_id
  FOREIGN KEY (reporte_operacion_sospechosa_id) REFERENCES auditoria.reporte_operacion_sospechosa (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.caso_investigacion_lft DROP CONSTRAINT IF EXISTS fk_caso_investigacion_lft_revisado_por;
ALTER TABLE cumplimiento.caso_investigacion_lft
  ADD CONSTRAINT fk_caso_investigacion_lft_revisado_por
  FOREIGN KEY (revisado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.caso_investigacion_lft DROP CONSTRAINT IF EXISTS fk_caso_investigacion_lft_usuario_id;
ALTER TABLE cumplimiento.caso_investigacion_lft
  ADD CONSTRAINT fk_caso_investigacion_lft_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.contrato_adhesion DROP CONSTRAINT IF EXISTS fk_contrato_adhesion_aprobado_por;
ALTER TABLE cumplimiento.contrato_adhesion
  ADD CONSTRAINT fk_contrato_adhesion_aprobado_por
  FOREIGN KEY (aprobado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.contrato_tercero DROP CONSTRAINT IF EXISTS fk_contrato_tercero_responsable_id;
ALTER TABLE cumplimiento.contrato_tercero
  ADD CONSTRAINT fk_contrato_tercero_responsable_id
  FOREIGN KEY (responsable_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.control_interno DROP CONSTRAINT IF EXISTS fk_control_interno_responsable_id;
ALTER TABLE cumplimiento.control_interno
  ADD CONSTRAINT fk_control_interno_responsable_id
  FOREIGN KEY (responsable_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.debida_diligencia DROP CONSTRAINT IF EXISTS fk_debida_diligencia_aprobada_por;
ALTER TABLE cumplimiento.debida_diligencia
  ADD CONSTRAINT fk_debida_diligencia_aprobada_por
  FOREIGN KEY (aprobada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.debida_diligencia DROP CONSTRAINT IF EXISTS fk_debida_diligencia_calificacion_riesgo_id;
ALTER TABLE cumplimiento.debida_diligencia
  ADD CONSTRAINT fk_debida_diligencia_calificacion_riesgo_id
  FOREIGN KEY (calificacion_riesgo_id) REFERENCES cumplimiento.calificacion_riesgo_cliente (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.debida_diligencia DROP CONSTRAINT IF EXISTS fk_debida_diligencia_segunda_revision_por;
ALTER TABLE cumplimiento.debida_diligencia
  ADD CONSTRAINT fk_debida_diligencia_segunda_revision_por
  FOREIGN KEY (segunda_revision_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.debida_diligencia DROP CONSTRAINT IF EXISTS fk_debida_diligencia_usuario_id;
ALTER TABLE cumplimiento.debida_diligencia
  ADD CONSTRAINT fk_debida_diligencia_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.debida_diligencia DROP CONSTRAINT IF EXISTS fk_debida_diligencia_verificacion_kyc_id;
ALTER TABLE cumplimiento.debida_diligencia
  ADD CONSTRAINT fk_debida_diligencia_verificacion_kyc_id
  FOREIGN KEY (verificacion_kyc_id) REFERENCES identidad.verificacion_kyc (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.declaracion_origen_fondos DROP CONSTRAINT IF EXISTS fk_declaracion_origen_fondos_transaccion_id;
ALTER TABLE cumplimiento.declaracion_origen_fondos
  ADD CONSTRAINT fk_declaracion_origen_fondos_transaccion_id
  FOREIGN KEY (transaccion_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.declaracion_origen_fondos DROP CONSTRAINT IF EXISTS fk_declaracion_origen_fondos_usuario_id;
ALTER TABLE cumplimiento.declaracion_origen_fondos
  ADD CONSTRAINT fk_declaracion_origen_fondos_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.declaracion_origen_fondos DROP CONSTRAINT IF EXISTS fk_declaracion_origen_fondos_verificada_por;
ALTER TABLE cumplimiento.declaracion_origen_fondos
  ADD CONSTRAINT fk_declaracion_origen_fondos_verificada_por
  FOREIGN KEY (verificada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.declaracion_pep DROP CONSTRAINT IF EXISTS fk_declaracion_pep_usuario_id;
ALTER TABLE cumplimiento.declaracion_pep
  ADD CONSTRAINT fk_declaracion_pep_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.declaracion_pep DROP CONSTRAINT IF EXISTS fk_declaracion_pep_verificada_por;
ALTER TABLE cumplimiento.declaracion_pep
  ADD CONSTRAINT fk_declaracion_pep_verificada_por
  FOREIGN KEY (verificada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.designacion_regulatoria DROP CONSTRAINT IF EXISTS fk_designacion_regulatoria_acta_comite_id;
ALTER TABLE cumplimiento.designacion_regulatoria
  ADD CONSTRAINT fk_designacion_regulatoria_acta_comite_id
  FOREIGN KEY (acta_comite_id) REFERENCES cumplimiento.acta_comite (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.designacion_regulatoria DROP CONSTRAINT IF EXISTS fk_designacion_regulatoria_usuario_id;
ALTER TABLE cumplimiento.designacion_regulatoria
  ADD CONSTRAINT fk_designacion_regulatoria_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.desvio_perfil DROP CONSTRAINT IF EXISTS fk_desvio_perfil_alerta_monitoreo_id;
ALTER TABLE cumplimiento.desvio_perfil
  ADD CONSTRAINT fk_desvio_perfil_alerta_monitoreo_id
  FOREIGN KEY (alerta_monitoreo_id) REFERENCES cumplimiento.alerta_monitoreo_lft (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.desvio_perfil DROP CONSTRAINT IF EXISTS fk_desvio_perfil_perfil_transaccional_id;
ALTER TABLE cumplimiento.desvio_perfil
  ADD CONSTRAINT fk_desvio_perfil_perfil_transaccional_id
  FOREIGN KEY (perfil_transaccional_id) REFERENCES cumplimiento.perfil_transaccional (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.desvio_perfil DROP CONSTRAINT IF EXISTS fk_desvio_perfil_usuario_id;
ALTER TABLE cumplimiento.desvio_perfil
  ADD CONSTRAINT fk_desvio_perfil_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.documento_publicado DROP CONSTRAINT IF EXISTS fk_documento_publicado_publicado_por;
ALTER TABLE cumplimiento.documento_publicado
  ADD CONSTRAINT fk_documento_publicado_publicado_por
  FOREIGN KEY (publicado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.entorno_prueba_regulado DROP CONSTRAINT IF EXISTS fk_entorno_prueba_regulado_licencia_regulatoria_id;
ALTER TABLE cumplimiento.entorno_prueba_regulado
  ADD CONSTRAINT fk_entorno_prueba_regulado_licencia_regulatoria_id
  FOREIGN KEY (licencia_regulatoria_id) REFERENCES catalogo.licencia_regulatoria (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.envio_regulatorio DROP CONSTRAINT IF EXISTS fk_envio_regulatorio_enviado_por;
ALTER TABLE cumplimiento.envio_regulatorio
  ADD CONSTRAINT fk_envio_regulatorio_enviado_por
  FOREIGN KEY (enviado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.envio_regulatorio DROP CONSTRAINT IF EXISTS fk_envio_regulatorio_reporte_regulatorio_id;
ALTER TABLE cumplimiento.envio_regulatorio
  ADD CONSTRAINT fk_envio_regulatorio_reporte_regulatorio_id
  FOREIGN KEY (reporte_regulatorio_id) REFERENCES cumplimiento.reporte_regulatorio (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.evaluacion_riesgo_producto DROP CONSTRAINT IF EXISTS fk_evaluacion_riesgo_producto_aprobada_por;
ALTER TABLE cumplimiento.evaluacion_riesgo_producto
  ADD CONSTRAINT fk_evaluacion_riesgo_producto_aprobada_por
  FOREIGN KEY (aprobada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.evaluacion_tercero DROP CONSTRAINT IF EXISTS fk_evaluacion_tercero_contrato_tercero_id;
ALTER TABLE cumplimiento.evaluacion_tercero
  ADD CONSTRAINT fk_evaluacion_tercero_contrato_tercero_id
  FOREIGN KEY (contrato_tercero_id) REFERENCES cumplimiento.contrato_tercero (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.evaluacion_tercero DROP CONSTRAINT IF EXISTS fk_evaluacion_tercero_evaluado_por;
ALTER TABLE cumplimiento.evaluacion_tercero
  ADD CONSTRAINT fk_evaluacion_tercero_evaluado_por
  FOREIGN KEY (evaluado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.evento_riesgo_operativo DROP CONSTRAINT IF EXISTS fk_evento_riesgo_operativo_incidente_operativo_id;
ALTER TABLE cumplimiento.evento_riesgo_operativo
  ADD CONSTRAINT fk_evento_riesgo_operativo_incidente_operativo_id
  FOREIGN KEY (incidente_operativo_id) REFERENCES auditoria.incidente_operativo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.evento_riesgo_operativo DROP CONSTRAINT IF EXISTS fk_evento_riesgo_operativo_registrado_por;
ALTER TABLE cumplimiento.evento_riesgo_operativo
  ADD CONSTRAINT fk_evento_riesgo_operativo_registrado_por
  FOREIGN KEY (registrado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.expediente_cliente DROP CONSTRAINT IF EXISTS fk_expediente_cliente_responsable_id;
ALTER TABLE cumplimiento.expediente_cliente
  ADD CONSTRAINT fk_expediente_cliente_responsable_id
  FOREIGN KEY (responsable_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.expediente_cliente DROP CONSTRAINT IF EXISTS fk_expediente_cliente_usuario_id;
ALTER TABLE cumplimiento.expediente_cliente
  ADD CONSTRAINT fk_expediente_cliente_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.factor_riesgo_evaluado DROP CONSTRAINT IF EXISTS fk_factor_riesgo_evaluado_matriz_riesgo_id;
ALTER TABLE cumplimiento.factor_riesgo_evaluado
  ADD CONSTRAINT fk_factor_riesgo_evaluado_matriz_riesgo_id
  FOREIGN KEY (matriz_riesgo_id) REFERENCES cumplimiento.matriz_riesgo_lft (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.factor_riesgo_evaluado DROP CONSTRAINT IF EXISTS fk_factor_riesgo_evaluado_usuario_id;
ALTER TABLE cumplimiento.factor_riesgo_evaluado
  ADD CONSTRAINT fk_factor_riesgo_evaluado_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.hallazgo_auditoria DROP CONSTRAINT IF EXISTS fk_hallazgo_auditoria_responsable_id;
ALTER TABLE cumplimiento.hallazgo_auditoria
  ADD CONSTRAINT fk_hallazgo_auditoria_responsable_id
  FOREIGN KEY (responsable_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.incidente_seguridad DROP CONSTRAINT IF EXISTS fk_incidente_seguridad_activo_informacion_id;
ALTER TABLE cumplimiento.incidente_seguridad
  ADD CONSTRAINT fk_incidente_seguridad_activo_informacion_id
  FOREIGN KEY (activo_informacion_id) REFERENCES cumplimiento.activo_informacion (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.incidente_seguridad DROP CONSTRAINT IF EXISTS fk_incidente_seguridad_evento_riesgo_id;
ALTER TABLE cumplimiento.incidente_seguridad
  ADD CONSTRAINT fk_incidente_seguridad_evento_riesgo_id
  FOREIGN KEY (evento_riesgo_id) REFERENCES cumplimiento.evento_riesgo_operativo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.incidente_seguridad DROP CONSTRAINT IF EXISTS fk_incidente_seguridad_incidente_operativo_id;
ALTER TABLE cumplimiento.incidente_seguridad
  ADD CONSTRAINT fk_incidente_seguridad_incidente_operativo_id
  FOREIGN KEY (incidente_operativo_id) REFERENCES auditoria.incidente_operativo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.incidente_seguridad DROP CONSTRAINT IF EXISTS fk_incidente_seguridad_responsable_id;
ALTER TABLE cumplimiento.incidente_seguridad
  ADD CONSTRAINT fk_incidente_seguridad_responsable_id
  FOREIGN KEY (responsable_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.instancia_reclamo DROP CONSTRAINT IF EXISTS fk_instancia_reclamo_reclamo_id;
ALTER TABLE cumplimiento.instancia_reclamo
  ADD CONSTRAINT fk_instancia_reclamo_reclamo_id
  FOREIGN KEY (reclamo_id) REFERENCES cumplimiento.reclamo_cliente (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE catalogo.licencia_regulatoria DROP CONSTRAINT IF EXISTS fk_licencia_regulatoria_responsable_id;
ALTER TABLE catalogo.licencia_regulatoria
  ADD CONSTRAINT fk_licencia_regulatoria_responsable_id
  FOREIGN KEY (responsable_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.matriz_riesgo_lft DROP CONSTRAINT IF EXISTS fk_matriz_riesgo_lft_aprobada_por;
ALTER TABLE cumplimiento.matriz_riesgo_lft
  ADD CONSTRAINT fk_matriz_riesgo_lft_aprobada_por
  FOREIGN KEY (aprobada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.observacion_regulatoria DROP CONSTRAINT IF EXISTS fk_observacion_regulatoria_envio_regulatorio_id;
ALTER TABLE cumplimiento.observacion_regulatoria
  ADD CONSTRAINT fk_observacion_regulatoria_envio_regulatorio_id
  FOREIGN KEY (envio_regulatorio_id) REFERENCES cumplimiento.envio_regulatorio (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.observacion_regulatoria DROP CONSTRAINT IF EXISTS fk_observacion_regulatoria_responsable_id;
ALTER TABLE cumplimiento.observacion_regulatoria
  ADD CONSTRAINT fk_observacion_regulatoria_responsable_id
  FOREIGN KEY (responsable_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.oficial_cumplimiento DROP CONSTRAINT IF EXISTS fk_oficial_cumplimiento_usuario_id;
ALTER TABLE cumplimiento.oficial_cumplimiento
  ADD CONSTRAINT fk_oficial_cumplimiento_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.perfil_transaccional DROP CONSTRAINT IF EXISTS fk_perfil_transaccional_usuario_id;
ALTER TABLE cumplimiento.perfil_transaccional
  ADD CONSTRAINT fk_perfil_transaccional_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.plan_accion_riesgo DROP CONSTRAINT IF EXISTS fk_plan_accion_riesgo_evento_riesgo_id;
ALTER TABLE cumplimiento.plan_accion_riesgo
  ADD CONSTRAINT fk_plan_accion_riesgo_evento_riesgo_id
  FOREIGN KEY (evento_riesgo_id) REFERENCES cumplimiento.evento_riesgo_operativo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.plan_accion_riesgo DROP CONSTRAINT IF EXISTS fk_plan_accion_riesgo_hallazgo_id;
ALTER TABLE cumplimiento.plan_accion_riesgo
  ADD CONSTRAINT fk_plan_accion_riesgo_hallazgo_id
  FOREIGN KEY (hallazgo_id) REFERENCES cumplimiento.hallazgo_auditoria (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.plan_accion_riesgo DROP CONSTRAINT IF EXISTS fk_plan_accion_riesgo_responsable_id;
ALTER TABLE cumplimiento.plan_accion_riesgo
  ADD CONSTRAINT fk_plan_accion_riesgo_responsable_id
  FOREIGN KEY (responsable_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.plan_continuidad DROP CONSTRAINT IF EXISTS fk_plan_continuidad_politica_interna_id;
ALTER TABLE cumplimiento.plan_continuidad
  ADD CONSTRAINT fk_plan_continuidad_politica_interna_id
  FOREIGN KEY (politica_interna_id) REFERENCES cumplimiento.politica_interna (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.plan_continuidad DROP CONSTRAINT IF EXISTS fk_plan_continuidad_responsable_id;
ALTER TABLE cumplimiento.plan_continuidad
  ADD CONSTRAINT fk_plan_continuidad_responsable_id
  FOREIGN KEY (responsable_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.politica_interna DROP CONSTRAINT IF EXISTS fk_politica_interna_acta_comite_id;
ALTER TABLE cumplimiento.politica_interna
  ADD CONSTRAINT fk_politica_interna_acta_comite_id
  FOREIGN KEY (acta_comite_id) REFERENCES cumplimiento.acta_comite (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.politica_interna DROP CONSTRAINT IF EXISTS fk_politica_interna_responsable_id;
ALTER TABLE cumplimiento.politica_interna
  ADD CONSTRAINT fk_politica_interna_responsable_id
  FOREIGN KEY (responsable_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.prueba_continuidad DROP CONSTRAINT IF EXISTS fk_prueba_continuidad_acta_comite_id;
ALTER TABLE cumplimiento.prueba_continuidad
  ADD CONSTRAINT fk_prueba_continuidad_acta_comite_id
  FOREIGN KEY (acta_comite_id) REFERENCES cumplimiento.acta_comite (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.prueba_continuidad DROP CONSTRAINT IF EXISTS fk_prueba_continuidad_ejecutada_por;
ALTER TABLE cumplimiento.prueba_continuidad
  ADD CONSTRAINT fk_prueba_continuidad_ejecutada_por
  FOREIGN KEY (ejecutada_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.prueba_continuidad DROP CONSTRAINT IF EXISTS fk_prueba_continuidad_plan_continuidad_id;
ALTER TABLE cumplimiento.prueba_continuidad
  ADD CONSTRAINT fk_prueba_continuidad_plan_continuidad_id
  FOREIGN KEY (plan_continuidad_id) REFERENCES cumplimiento.plan_continuidad (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.prueba_control DROP CONSTRAINT IF EXISTS fk_prueba_control_control_id;
ALTER TABLE cumplimiento.prueba_control
  ADD CONSTRAINT fk_prueba_control_control_id
  FOREIGN KEY (control_id) REFERENCES cumplimiento.control_interno (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.prueba_control DROP CONSTRAINT IF EXISTS fk_prueba_control_ejecutada_por;
ALTER TABLE cumplimiento.prueba_control
  ADD CONSTRAINT fk_prueba_control_ejecutada_por
  FOREIGN KEY (ejecutada_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.punto_reclamo DROP CONSTRAINT IF EXISTS fk_punto_reclamo_responsable_id;
ALTER TABLE cumplimiento.punto_reclamo
  ADD CONSTRAINT fk_punto_reclamo_responsable_id
  FOREIGN KEY (responsable_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.reclamo_cliente DROP CONSTRAINT IF EXISTS fk_reclamo_cliente_devolucion_comision_id;
ALTER TABLE cumplimiento.reclamo_cliente
  ADD CONSTRAINT fk_reclamo_cliente_devolucion_comision_id
  FOREIGN KEY (devolucion_comision_id) REFERENCES tarifas.devolucion_comision (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.reclamo_cliente DROP CONSTRAINT IF EXISTS fk_reclamo_cliente_punto_reclamo_id;
ALTER TABLE cumplimiento.reclamo_cliente
  ADD CONSTRAINT fk_reclamo_cliente_punto_reclamo_id
  FOREIGN KEY (punto_reclamo_id) REFERENCES cumplimiento.punto_reclamo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.reclamo_cliente DROP CONSTRAINT IF EXISTS fk_reclamo_cliente_responsable_id;
ALTER TABLE cumplimiento.reclamo_cliente
  ADD CONSTRAINT fk_reclamo_cliente_responsable_id
  FOREIGN KEY (responsable_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.reclamo_cliente DROP CONSTRAINT IF EXISTS fk_reclamo_cliente_ticket_soporte_id;
ALTER TABLE cumplimiento.reclamo_cliente
  ADD CONSTRAINT fk_reclamo_cliente_ticket_soporte_id
  FOREIGN KEY (ticket_soporte_id) REFERENCES auditoria.ticket_soporte (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.reclamo_cliente DROP CONSTRAINT IF EXISTS fk_reclamo_cliente_usuario_id;
ALTER TABLE cumplimiento.reclamo_cliente
  ADD CONSTRAINT fk_reclamo_cliente_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.registro_operacion_relevante DROP CONSTRAINT IF EXISTS fk_registro_operacion_relevante_declaracion_origen_fondos_id;
ALTER TABLE cumplimiento.registro_operacion_relevante
  ADD CONSTRAINT fk_registro_operacion_relevante_declaracion_origen_fondos_id
  FOREIGN KEY (declaracion_origen_fondos_id) REFERENCES cumplimiento.declaracion_origen_fondos (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.registro_operacion_relevante DROP CONSTRAINT IF EXISTS fk_registro_operacion_relevante_operacion_inicio_ventana_id;
ALTER TABLE cumplimiento.registro_operacion_relevante
  ADD CONSTRAINT fk_registro_operacion_relevante_operacion_inicio_ventana_id
  FOREIGN KEY (operacion_inicio_ventana_id) REFERENCES cumplimiento.registro_operacion_relevante (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.registro_operacion_relevante DROP CONSTRAINT IF EXISTS fk_registro_operacion_relevante_reporte_regulatorio_id;
ALTER TABLE cumplimiento.registro_operacion_relevante
  ADD CONSTRAINT fk_registro_operacion_relevante_reporte_regulatorio_id
  FOREIGN KEY (reporte_regulatorio_id) REFERENCES cumplimiento.reporte_regulatorio (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.registro_operacion_relevante DROP CONSTRAINT IF EXISTS fk_registro_operacion_relevante_transaccion_id;
ALTER TABLE cumplimiento.registro_operacion_relevante
  ADD CONSTRAINT fk_registro_operacion_relevante_transaccion_id
  FOREIGN KEY (transaccion_id) REFERENCES nucleo_financiero.transaccion_billetera (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.registro_operacion_relevante DROP CONSTRAINT IF EXISTS fk_registro_operacion_relevante_umbral_reporte_id;
ALTER TABLE cumplimiento.registro_operacion_relevante
  ADD CONSTRAINT fk_registro_operacion_relevante_umbral_reporte_id
  FOREIGN KEY (umbral_reporte_id) REFERENCES catalogo.umbral_reporte_uif (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.registro_operacion_relevante DROP CONSTRAINT IF EXISTS fk_registro_operacion_relevante_usuario_id;
ALTER TABLE cumplimiento.registro_operacion_relevante
  ADD CONSTRAINT fk_registro_operacion_relevante_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.regla_monitoreo_lft DROP CONSTRAINT IF EXISTS fk_regla_monitoreo_lft_aprobada_por;
ALTER TABLE cumplimiento.regla_monitoreo_lft
  ADD CONSTRAINT fk_regla_monitoreo_lft_aprobada_por
  FOREIGN KEY (aprobada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.reporte_regulatorio DROP CONSTRAINT IF EXISTS fk_reporte_regulatorio_aprobado_por;
ALTER TABLE cumplimiento.reporte_regulatorio
  ADD CONSTRAINT fk_reporte_regulatorio_aprobado_por
  FOREIGN KEY (aprobado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.reporte_regulatorio DROP CONSTRAINT IF EXISTS fk_reporte_regulatorio_catalogo_reporte_id;
ALTER TABLE cumplimiento.reporte_regulatorio
  ADD CONSTRAINT fk_reporte_regulatorio_catalogo_reporte_id
  FOREIGN KEY (catalogo_reporte_id) REFERENCES cumplimiento.catalogo_reporte_regulatorio (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE cumplimiento.reporte_regulatorio DROP CONSTRAINT IF EXISTS fk_reporte_regulatorio_generado_por;
ALTER TABLE cumplimiento.reporte_regulatorio
  ADD CONSTRAINT fk_reporte_regulatorio_generado_por
  FOREIGN KEY (generado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.reporte_regulatorio DROP CONSTRAINT IF EXISTS fk_reporte_regulatorio_revisado_por;
ALTER TABLE cumplimiento.reporte_regulatorio
  ADD CONSTRAINT fk_reporte_regulatorio_revisado_por
  FOREIGN KEY (revisado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.requerimiento_autoridad DROP CONSTRAINT IF EXISTS fk_requerimiento_autoridad_bloqueo_saldo_id;
ALTER TABLE cumplimiento.requerimiento_autoridad
  ADD CONSTRAINT fk_requerimiento_autoridad_bloqueo_saldo_id
  FOREIGN KEY (bloqueo_saldo_id) REFERENCES nucleo_financiero.bloqueo_saldo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.requerimiento_autoridad DROP CONSTRAINT IF EXISTS fk_requerimiento_autoridad_respondido_por;
ALTER TABLE cumplimiento.requerimiento_autoridad
  ADD CONSTRAINT fk_requerimiento_autoridad_respondido_por
  FOREIGN KEY (respondido_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.requerimiento_autoridad DROP CONSTRAINT IF EXISTS fk_requerimiento_autoridad_usuario_afectado_id;
ALTER TABLE cumplimiento.requerimiento_autoridad
  ADD CONSTRAINT fk_requerimiento_autoridad_usuario_afectado_id
  FOREIGN KEY (usuario_afectado_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.revision_periodica_kyc DROP CONSTRAINT IF EXISTS fk_revision_periodica_kyc_calificacion_riesgo_id;
ALTER TABLE cumplimiento.revision_periodica_kyc
  ADD CONSTRAINT fk_revision_periodica_kyc_calificacion_riesgo_id
  FOREIGN KEY (calificacion_riesgo_id) REFERENCES cumplimiento.calificacion_riesgo_cliente (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.revision_periodica_kyc DROP CONSTRAINT IF EXISTS fk_revision_periodica_kyc_ejecutada_por;
ALTER TABLE cumplimiento.revision_periodica_kyc
  ADD CONSTRAINT fk_revision_periodica_kyc_ejecutada_por
  FOREIGN KEY (ejecutada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE cumplimiento.revision_periodica_kyc DROP CONSTRAINT IF EXISTS fk_revision_periodica_kyc_usuario_id;
ALTER TABLE cumplimiento.revision_periodica_kyc
  ADD CONSTRAINT fk_revision_periodica_kyc_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;
