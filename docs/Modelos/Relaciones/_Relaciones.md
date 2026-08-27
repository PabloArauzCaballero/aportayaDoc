---
tags:
  - moc
  - indice
relaciones_fk: 630
cross_modulo: 326
---

# Índice de relaciones (claves foráneas)

Las **630 claves foráneas** del modelo. **326** cruzan módulos.

[[Index|← Índice general]] · [[_Entidades|Entidades →]]

## Referencias que cruzan módulos

Son las que acoplan el sistema: conviene revisarlas antes de tocar un módulo.

| Origen | Columna | Destino | Módulos | Opcional | Relación |
| --- | --- | --- | :-: | :-: | --- |
| [[token_verificacion]] | `politica_id` | [[politica_sancion]] | 01 → 08 | no | [[token_verificacion.politica_id → politica_sancion\|ver]] |
| [[aceptacion_reglamento]] | `token_firma_id` | [[token_verificacion]] | 02 → 01 | sí | [[aceptacion_reglamento.token_firma_id → token_verificacion\|ver]] |
| [[acuerdo]] | `propuesto_por` | [[usuario]] | 02 → 01 | no | [[acuerdo.propuesto_por → usuario\|ver]] |
| [[configuracion_grupo]] | `politica_mora_id` | [[politica_mora]] | 02 → 03 | sí | [[configuracion_grupo.politica_mora_id → politica_mora\|ver]] |
| [[configuracion_grupo]] | `politica_sancion_id` | [[politica_sancion]] | 02 → 08 | sí | [[configuracion_grupo.politica_sancion_id → politica_sancion\|ver]] |
| [[grupo]] | `organizador_id` | [[organizador]] | 02 → 07 | sí | [[grupo.organizador_id → organizador\|ver]] |
| [[historial_estado_grupo]] | `ejecutado_por` | [[usuario]] | 02 → 01 | no | [[historial_estado_grupo.ejecutado_por → usuario\|ver]] |
| [[invitacion]] | `emisor_id` | [[usuario]] | 02 → 01 | no | [[invitacion.emisor_id → usuario\|ver]] |
| [[invitacion]] | `token_id` | [[token_verificacion]] | 02 → 01 | no | [[invitacion.token_id → token_verificacion\|ver]] |
| [[participante]] | `usuario_id` | [[usuario]] | 02 → 01 | no | [[participante.usuario_id → usuario\|ver]] |
| [[postulacion_emparejamiento]] | `usuario_id` | [[usuario]] | 02 → 01 | no | [[postulacion_emparejamiento.usuario_id → usuario\|ver]] |
| [[reglamento_grupo]] | `redactado_por` | [[usuario]] | 02 → 01 | no | [[reglamento_grupo.redactado_por → usuario\|ver]] |
| [[solicitud_ingreso]] | `revisada_por` | [[usuario]] | 02 → 01 | sí | [[solicitud_ingreso.revisada_por → usuario\|ver]] |
| [[solicitud_ingreso]] | `usuario_id` | [[usuario]] | 02 → 01 | no | [[solicitud_ingreso.usuario_id → usuario\|ver]] |
| [[solicitud_retiro]] | `plan_regularizacion_id` | [[plan_regularizacion]] | 02 → 03 | sí | [[solicitud_retiro.plan_regularizacion_id → plan_regularizacion\|ver]] |
| [[sorteo_turnos]] | `ejecutado_por` | [[usuario]] | 02 → 01 | no | [[sorteo_turnos.ejecutado_por → usuario\|ver]] |
| [[asiento_contable]] | `grupo_id` | [[grupo]] | 03 → 02 | sí | [[asiento_contable.grupo_id → grupo\|ver]] |
| [[asiento_contable]] | `periodo_contable_id` | [[periodo_contable]] | 03 → 13 | sí | [[asiento_contable.periodo_contable_id → periodo_contable\|ver]] |
| [[asiento_contable]] | `registrado_por` | [[usuario]] | 03 → 01 | sí | [[asiento_contable.registrado_por → usuario\|ver]] |
| [[cierre_diario]] | `cerrado_por` | [[usuario]] | 03 → 01 | no | [[cierre_diario.cerrado_por → usuario\|ver]] |
| [[comprobante_manual]] | `revisado_por` | [[usuario]] | 03 → 01 | sí | [[comprobante_manual.revisado_por → usuario\|ver]] |
| [[comprobante_manual]] | `segunda_revision_por` | [[usuario]] | 03 → 01 | sí | [[comprobante_manual.segunda_revision_por → usuario\|ver]] |
| [[conciliacion]] | `conciliado_por` | [[usuario]] | 03 → 01 | sí | [[conciliacion.conciliado_por → usuario\|ver]] |
| [[cuenta_contable]] | `grupo_id` | [[grupo]] | 03 → 02 | sí | [[cuenta_contable.grupo_id → grupo\|ver]] |
| [[cuenta_contable]] | `participante_id` | [[participante]] | 03 → 02 | sí | [[cuenta_contable.participante_id → participante\|ver]] |
| [[enlace_pago_rapido]] | `token_id` | [[token_verificacion]] | 03 → 01 | no | [[enlace_pago_rapido.token_id → token_verificacion\|ver]] |
| [[excepcion_conciliacion]] | `asignada_a` | [[usuario]] | 03 → 01 | sí | [[excepcion_conciliacion.asignada_a → usuario\|ver]] |
| [[extracto_bancario]] | `importado_por` | [[usuario]] | 03 → 01 | no | [[extracto_bancario.importado_por → usuario\|ver]] |
| [[obligacion_aporte]] | `cupo_id` | [[cupo]] | 03 → 02 | no | [[obligacion_aporte.cupo_id → cupo\|ver]] |
| [[obligacion_aporte]] | `grupo_id` | [[grupo]] | 03 → 02 | no | [[obligacion_aporte.grupo_id → grupo\|ver]] |
| [[obligacion_aporte]] | `participante_id` | [[participante]] | 03 → 02 | no | [[obligacion_aporte.participante_id → participante\|ver]] |
| [[obligacion_aporte]] | `periodo_id` | [[periodo]] | 03 → 02 | no | [[obligacion_aporte.periodo_id → periodo\|ver]] |
| [[pago]] | `registrado_por` | [[usuario]] | 03 → 01 | sí | [[pago.registrado_por → usuario\|ver]] |
| [[plan_regularizacion]] | `aprobado_por` | [[usuario]] | 03 → 01 | no | [[plan_regularizacion.aprobado_por → usuario\|ver]] |
| [[plan_regularizacion]] | `participante_id` | [[participante]] | 03 → 02 | no | [[plan_regularizacion.participante_id → participante\|ver]] |
| [[politica_mora]] | `grupo_id` | [[grupo]] | 03 → 02 | sí | [[politica_mora.grupo_id → grupo\|ver]] |
| [[reembolso]] | `aprobado_por` | [[usuario]] | 03 → 01 | sí | [[reembolso.aprobado_por → usuario\|ver]] |
| [[reembolso]] | `solicitado_por` | [[usuario]] | 03 → 01 | no | [[reembolso.solicitado_por → usuario\|ver]] |
| [[confirmacion_recepcion]] | `token_confirmacion_id` | [[token_verificacion]] | 04 → 01 | sí | [[confirmacion_recepcion.token_confirmacion_id → token_verificacion\|ver]] |
| [[cuenta_bancaria_beneficiario]] | `usuario_id` | [[usuario]] | 04 → 01 | no | [[cuenta_bancaria_beneficiario.usuario_id → usuario\|ver]] |
| [[entrega_fondo]] | `autorizada_por` | [[usuario]] | 04 → 01 | sí | [[entrega_fondo.autorizada_por → usuario\|ver]] |
| [[entrega_fondo]] | `beneficiario_participante_id` | [[participante]] | 04 → 02 | no | [[entrega_fondo.beneficiario_participante_id → participante\|ver]] |
| [[entrega_fondo]] | `cupo_id` | [[cupo]] | 04 → 02 | no | [[entrega_fondo.cupo_id → cupo\|ver]] |
| [[entrega_fondo]] | `ejecutada_por` | [[usuario]] | 04 → 01 | sí | [[entrega_fondo.ejecutada_por → usuario\|ver]] |
| [[entrega_fondo]] | `grupo_id` | [[grupo]] | 04 → 02 | no | [[entrega_fondo.grupo_id → grupo\|ver]] |
| [[entrega_fondo]] | `periodo_id` | [[periodo]] | 04 → 02 | no | [[entrega_fondo.periodo_id → periodo\|ver]] |
| [[entrega_fondo]] | `turno_id` | [[turno]] | 04 → 02 | no | [[entrega_fondo.turno_id → turno\|ver]] |
| [[historial_estado_entrega]] | `ejecutado_por` | [[usuario]] | 04 → 01 | sí | [[historial_estado_entrega.ejecutado_por → usuario\|ver]] |
| [[incidencia_entrega]] | `asignada_a` | [[usuario]] | 04 → 01 | sí | [[incidencia_entrega.asignada_a → usuario\|ver]] |
| [[incidencia_entrega]] | `reportada_por` | [[usuario]] | 04 → 01 | no | [[incidencia_entrega.reportada_por → usuario\|ver]] |
| [[orden_desembolso]] | `proveedor_id` | [[proveedor_pago]] | 04 → 03 | no | [[orden_desembolso.proveedor_id → proveedor_pago\|ver]] |
| [[validacion_pre_entrega]] | `omitida_por` | [[usuario]] | 04 → 01 | sí | [[validacion_pre_entrega.omitida_por → usuario\|ver]] |
| [[bandeja_entrada]] | `usuario_id` | [[usuario]] | 05 → 01 | no | [[bandeja_entrada.usuario_id → usuario\|ver]] |
| [[canal_vinculado]] | `usuario_id` | [[usuario]] | 05 → 01 | no | [[canal_vinculado.usuario_id → usuario\|ver]] |
| [[enlace_pago_notificado]] | `orden_cobro_id` | [[orden_cobro]] | 05 → 03 | no | [[enlace_pago_notificado.orden_cobro_id → orden_cobro\|ver]] |
| [[enlace_pago_notificado]] | `token_id` | [[token_verificacion]] | 05 → 01 | no | [[enlace_pago_notificado.token_id → token_verificacion\|ver]] |
| [[notificacion]] | `usuario_id` | [[usuario]] | 05 → 01 | no | [[notificacion.usuario_id → usuario\|ver]] |
| [[programacion_recordatorio]] | `grupo_id` | [[grupo]] | 05 → 02 | sí | [[programacion_recordatorio.grupo_id → grupo\|ver]] |
| [[bloque_transparencia]] | `grupo_id` | [[grupo]] | 06 → 02 | no | [[bloque_transparencia.grupo_id → grupo\|ver]] |
| [[certificado_reputacion]] | `usuario_id` | [[usuario]] | 06 → 01 | no | [[certificado_reputacion.usuario_id → usuario\|ver]] |
| [[evento_reputacion]] | `grupo_id` | [[grupo]] | 06 → 02 | sí | [[evento_reputacion.grupo_id → grupo\|ver]] |
| [[evento_reputacion]] | `participante_id` | [[participante]] | 06 → 02 | sí | [[evento_reputacion.participante_id → participante\|ver]] |
| [[evento_reputacion]] | `usuario_id` | [[usuario]] | 06 → 01 | no | [[evento_reputacion.usuario_id → usuario\|ver]] |
| [[insignia_otorgada]] | `usuario_id` | [[usuario]] | 06 → 01 | no | [[insignia_otorgada.usuario_id → usuario\|ver]] |
| [[metrica_grupo]] | `grupo_id` | [[grupo]] | 06 → 02 | no | [[metrica_grupo.grupo_id → grupo\|ver]] |
| [[metrica_grupo]] | `periodo_id` | [[periodo]] | 06 → 02 | sí | [[metrica_grupo.periodo_id → periodo\|ver]] |
| [[puntaje_reputacion]] | `usuario_id` | [[usuario]] | 06 → 01 | no | [[puntaje_reputacion.usuario_id → usuario\|ver]] |
| [[resena_participante]] | `autor_participante_id` | [[participante]] | 06 → 02 | no | [[resena_participante.autor_participante_id → participante\|ver]] |
| [[resena_participante]] | `evaluado_usuario_id` | [[usuario]] | 06 → 01 | no | [[resena_participante.evaluado_usuario_id → usuario\|ver]] |
| [[resena_participante]] | `grupo_id` | [[grupo]] | 06 → 02 | no | [[resena_participante.grupo_id → grupo\|ver]] |
| [[resena_participante]] | `moderada_por` | [[usuario]] | 06 → 01 | sí | [[resena_participante.moderada_por → usuario\|ver]] |
| [[snapshot_reputacion]] | `usuario_id` | [[usuario]] | 06 → 01 | no | [[snapshot_reputacion.usuario_id → usuario\|ver]] |
| [[apelacion_sancion_org]] | `resuelta_por` | [[usuario]] | 07 → 01 | sí | [[apelacion_sancion_org.resuelta_por → usuario\|ver]] |
| [[contrato_organizador]] | `token_firma_id` | [[token_verificacion]] | 07 → 01 | sí | [[contrato_organizador.token_firma_id → token_verificacion\|ver]] |
| [[organizador]] | `usuario_id` | [[usuario]] | 07 → 01 | no | [[organizador.usuario_id → usuario\|ver]] |
| [[sancion_organizador]] | `aplicada_por` | [[usuario]] | 07 → 01 | no | [[sancion_organizador.aplicada_por → usuario\|ver]] |
| [[solicitud_organizador]] | `kyc_reforzado_id` | [[verificacion_kyc]] | 07 → 01 | sí | [[solicitud_organizador.kyc_reforzado_id → verificacion_kyc\|ver]] |
| [[solicitud_organizador]] | `revisada_por` | [[usuario]] | 07 → 01 | sí | [[solicitud_organizador.revisada_por → usuario\|ver]] |
| [[solicitud_organizador]] | `usuario_id` | [[usuario]] | 07 → 01 | no | [[solicitud_organizador.usuario_id → usuario\|ver]] |
| [[tarea_automatizada]] | `grupo_id` | [[grupo]] | 07 → 02 | no | [[tarea_automatizada.grupo_id → grupo\|ver]] |
| [[abono_recuperacion]] | `entrega_id` | [[entrega_fondo]] | 08 → 04 | sí | [[abono_recuperacion.entrega_id → entrega_fondo\|ver]] |
| [[abono_recuperacion]] | `pago_id` | [[pago]] | 08 → 03 | sí | [[abono_recuperacion.pago_id → pago\|ver]] |
| [[abono_recuperacion]] | `registrado_por` | [[usuario]] | 08 → 01 | sí | [[abono_recuperacion.registrado_por → usuario\|ver]] |
| [[accion_cobranza]] | `ejecutada_por` | [[usuario]] | 08 → 01 | sí | [[accion_cobranza.ejecutada_por → usuario\|ver]] |
| [[accion_cobranza]] | `notificacion_id` | [[notificacion]] | 08 → 05 | sí | [[accion_cobranza.notificacion_id → notificacion\|ver]] |
| [[acuerdo_quita]] | `acuerdo_grupo_id` | [[acuerdo]] | 08 → 02 | sí | [[acuerdo_quita.acuerdo_grupo_id → acuerdo\|ver]] |
| [[acuerdo_quita]] | `aprobado_por` | [[usuario]] | 08 → 01 | no | [[acuerdo_quita.aprobado_por → usuario\|ver]] |
| [[alerta_temprana]] | `grupo_id` | [[grupo]] | 08 → 02 | sí | [[alerta_temprana.grupo_id → grupo\|ver]] |
| [[alerta_temprana]] | `usuario_id` | [[usuario]] | 08 → 01 | no | [[alerta_temprana.usuario_id → usuario\|ver]] |
| [[apelacion_sancion]] | `apelante_id` | [[usuario]] | 08 → 01 | no | [[apelacion_sancion.apelante_id → usuario\|ver]] |
| [[apelacion_sancion]] | `resuelta_por` | [[usuario]] | 08 → 01 | sí | [[apelacion_sancion.resuelta_por → usuario\|ver]] |
| [[aval_participante]] | `avalista_usuario_id` | [[usuario]] | 08 → 01 | no | [[aval_participante.avalista_usuario_id → usuario\|ver]] |
| [[aval_participante]] | `grupo_id` | [[grupo]] | 08 → 02 | no | [[aval_participante.grupo_id → grupo\|ver]] |
| [[aval_participante]] | `participante_avalado_id` | [[participante]] | 08 → 02 | no | [[aval_participante.participante_avalado_id → participante\|ver]] |
| [[aval_participante]] | `token_aceptacion_id` | [[token_verificacion]] | 08 → 01 | sí | [[aval_participante.token_aceptacion_id → token_verificacion\|ver]] |
| [[candidato_reemplazo]] | `usuario_id` | [[usuario]] | 08 → 01 | no | [[candidato_reemplazo.usuario_id → usuario\|ver]] |
| [[castigo_deuda]] | `aprobado_por` | [[usuario]] | 08 → 01 | no | [[castigo_deuda.aprobado_por → usuario\|ver]] |
| [[castigo_deuda]] | `asiento_contable_id` | [[asiento_contable]] | 08 → 03 | sí | [[castigo_deuda.asiento_contable_id → asiento_contable\|ver]] |
| [[cobertura_incumplimiento]] | `aprobada_por` | [[usuario]] | 08 → 01 | sí | [[cobertura_incumplimiento.aprobada_por → usuario\|ver]] |
| [[cobertura_incumplimiento]] | `asiento_contable_id` | [[asiento_contable]] | 08 → 03 | sí | [[cobertura_incumplimiento.asiento_contable_id → asiento_contable\|ver]] |
| [[cobertura_incumplimiento]] | `obligacion_id` | [[obligacion_aporte]] | 08 → 03 | no | [[cobertura_incumplimiento.obligacion_id → obligacion_aporte\|ver]] |
| [[cobertura_incumplimiento]] | `periodo_id` | [[periodo]] | 08 → 02 | no | [[cobertura_incumplimiento.periodo_id → periodo\|ver]] |
| [[descargo_participante]] | `participante_id` | [[participante]] | 08 → 02 | no | [[descargo_participante.participante_id → participante\|ver]] |
| [[descargo_participante]] | `resuelto_por` | [[usuario]] | 08 → 01 | sí | [[descargo_participante.resuelto_por → usuario\|ver]] |
| [[deuda_participante]] | `grupo_id` | [[grupo]] | 08 → 02 | no | [[deuda_participante.grupo_id → grupo\|ver]] |
| [[deuda_participante]] | `participante_id` | [[participante]] | 08 → 02 | no | [[deuda_participante.participante_id → participante\|ver]] |
| [[deuda_participante]] | `usuario_id` | [[usuario]] | 08 → 01 | no | [[deuda_participante.usuario_id → usuario\|ver]] |
| [[devolucion_fondo]] | `participante_id` | [[participante]] | 08 → 02 | no | [[devolucion_fondo.participante_id → participante\|ver]] |
| [[disolucion_anticipada]] | `acuerdo_grupo_id` | [[acuerdo]] | 08 → 02 | sí | [[disolucion_anticipada.acuerdo_grupo_id → acuerdo\|ver]] |
| [[disolucion_anticipada]] | `grupo_id` | [[grupo]] | 08 → 02 | no | [[disolucion_anticipada.grupo_id → grupo\|ver]] |
| [[ejecucion_aval]] | `pago_id` | [[pago]] | 08 → 03 | sí | [[ejecucion_aval.pago_id → pago\|ver]] |
| [[evidencia_incumplimiento]] | `aportada_por` | [[usuario]] | 08 → 01 | sí | [[evidencia_incumplimiento.aportada_por → usuario\|ver]] |
| [[fondo_garantia]] | `cuenta_contable_id` | [[cuenta_contable]] | 08 → 03 | no | [[fondo_garantia.cuenta_contable_id → cuenta_contable\|ver]] |
| [[fondo_garantia]] | `grupo_id` | [[grupo]] | 08 → 02 | sí | [[fondo_garantia.grupo_id → grupo\|ver]] |
| [[gestion_cobranza]] | `gestor_asignado_id` | [[usuario]] | 08 → 01 | sí | [[gestion_cobranza.gestor_asignado_id → usuario\|ver]] |
| [[historial_estado_incumplimiento]] | `ejecutado_por` | [[usuario]] | 08 → 01 | sí | [[historial_estado_incumplimiento.ejecutado_por → usuario\|ver]] |
| [[historial_incumplimiento_usuario]] | `usuario_id` | [[usuario]] | 08 → 01 | no | [[historial_incumplimiento_usuario.usuario_id → usuario\|ver]] |
| [[liquidacion_participante]] | `participante_id` | [[participante]] | 08 → 02 | no | [[liquidacion_participante.participante_id → participante\|ver]] |
| [[lista_restriccion_interna]] | `retirado_por` | [[usuario]] | 08 → 01 | sí | [[lista_restriccion_interna.retirado_por → usuario\|ver]] |
| [[lista_restriccion_interna]] | `usuario_id` | [[usuario]] | 08 → 01 | no | [[lista_restriccion_interna.usuario_id → usuario\|ver]] |
| [[movimiento_fondo]] | `asiento_contable_id` | [[asiento_contable]] | 08 → 03 | sí | [[movimiento_fondo.asiento_contable_id → asiento_contable\|ver]] |
| [[movimiento_fondo]] | `registrado_por` | [[usuario]] | 08 → 01 | sí | [[movimiento_fondo.registrado_por → usuario\|ver]] |
| [[plan_contingencia]] | `acuerdo_grupo_id` | [[acuerdo]] | 08 → 02 | sí | [[plan_contingencia.acuerdo_grupo_id → acuerdo\|ver]] |
| [[plan_contingencia]] | `grupo_id` | [[grupo]] | 08 → 02 | no | [[plan_contingencia.grupo_id → grupo\|ver]] |
| [[politica_cobertura]] | `grupo_id` | [[grupo]] | 08 → 02 | sí | [[politica_cobertura.grupo_id → grupo\|ver]] |
| [[politica_sancion]] | `grupo_id` | [[grupo]] | 08 → 02 | sí | [[politica_sancion.grupo_id → grupo\|ver]] |
| [[promesa_pago]] | `registrada_por` | [[usuario]] | 08 → 01 | sí | [[promesa_pago.registrada_por → usuario\|ver]] |
| [[reemplazo_participante]] | `acuerdo_grupo_id` | [[acuerdo]] | 08 → 02 | sí | [[reemplazo_participante.acuerdo_grupo_id → acuerdo\|ver]] |
| [[reemplazo_participante]] | `cupo_id` | [[cupo]] | 08 → 02 | no | [[reemplazo_participante.cupo_id → cupo\|ver]] |
| [[reemplazo_participante]] | `grupo_id` | [[grupo]] | 08 → 02 | no | [[reemplazo_participante.grupo_id → grupo\|ver]] |
| [[reemplazo_participante]] | `participante_entrante_id` | [[participante]] | 08 → 02 | sí | [[reemplazo_participante.participante_entrante_id → participante\|ver]] |
| [[reemplazo_participante]] | `participante_saliente_id` | [[participante]] | 08 → 02 | no | [[reemplazo_participante.participante_saliente_id → participante\|ver]] |
| [[registro_incumplimiento]] | `cupo_id` | [[cupo]] | 08 → 02 | sí | [[registro_incumplimiento.cupo_id → cupo\|ver]] |
| [[registro_incumplimiento]] | `entrega_afectada_id` | [[entrega_fondo]] | 08 → 04 | sí | [[registro_incumplimiento.entrega_afectada_id → entrega_fondo\|ver]] |
| [[registro_incumplimiento]] | `grupo_id` | [[grupo]] | 08 → 02 | no | [[registro_incumplimiento.grupo_id → grupo\|ver]] |
| [[registro_incumplimiento]] | `obligacion_id` | [[obligacion_aporte]] | 08 → 03 | sí | [[registro_incumplimiento.obligacion_id → obligacion_aporte\|ver]] |
| [[registro_incumplimiento]] | `participante_id` | [[participante]] | 08 → 02 | no | [[registro_incumplimiento.participante_id → participante\|ver]] |
| [[registro_incumplimiento]] | `periodo_id` | [[periodo]] | 08 → 02 | sí | [[registro_incumplimiento.periodo_id → periodo\|ver]] |
| [[registro_incumplimiento]] | `reportado_por` | [[usuario]] | 08 → 01 | sí | [[registro_incumplimiento.reportado_por → usuario\|ver]] |
| [[registro_incumplimiento]] | `responsable_gestion` | [[usuario]] | 08 → 01 | sí | [[registro_incumplimiento.responsable_gestion → usuario\|ver]] |
| [[registro_incumplimiento]] | `usuario_id` | [[usuario]] | 08 → 01 | no | [[registro_incumplimiento.usuario_id → usuario\|ver]] |
| [[sancion]] | `acuerdo_grupo_id` | [[acuerdo]] | 08 → 02 | sí | [[sancion.acuerdo_grupo_id → acuerdo\|ver]] |
| [[sancion]] | `aplicada_por` | [[usuario]] | 08 → 01 | sí | [[sancion.aplicada_por → usuario\|ver]] |
| [[sancion]] | `participante_id` | [[participante]] | 08 → 02 | sí | [[sancion.participante_id → participante\|ver]] |
| [[sancion]] | `usuario_id` | [[usuario]] | 08 → 01 | no | [[sancion.usuario_id → usuario\|ver]] |
| [[score_riesgo_incumplimiento]] | `grupo_id` | [[grupo]] | 08 → 02 | sí | [[score_riesgo_incumplimiento.grupo_id → grupo\|ver]] |
| [[score_riesgo_incumplimiento]] | `usuario_id` | [[usuario]] | 08 → 01 | no | [[score_riesgo_incumplimiento.usuario_id → usuario\|ver]] |
| [[alerta_cumplimiento]] | `analista_id` | [[usuario]] | 09 → 01 | sí | [[alerta_cumplimiento.analista_id → usuario\|ver]] |
| [[alerta_cumplimiento]] | `grupo_id` | [[grupo]] | 09 → 02 | sí | [[alerta_cumplimiento.grupo_id → grupo\|ver]] |
| [[alerta_cumplimiento]] | `usuario_id` | [[usuario]] | 09 → 01 | no | [[alerta_cumplimiento.usuario_id → usuario\|ver]] |
| [[bitacora_evento]] | `actor_usuario_id` | [[usuario]] | 09 → 01 | sí | [[bitacora_evento.actor_usuario_id → usuario\|ver]] |
| [[bitacora_evento]] | `grupo_id` | [[grupo]] | 09 → 02 | sí | [[bitacora_evento.grupo_id → grupo\|ver]] |
| [[bitacora_evento]] | `suplantando_a_usuario_id` | [[usuario]] | 09 → 01 | sí | [[bitacora_evento.suplantando_a_usuario_id → usuario\|ver]] |
| [[coincidencia_lista]] | `revisada_por` | [[usuario]] | 09 → 01 | sí | [[coincidencia_lista.revisada_por → usuario\|ver]] |
| [[coincidencia_lista]] | `usuario_id` | [[usuario]] | 09 → 01 | no | [[coincidencia_lista.usuario_id → usuario\|ver]] |
| [[ejecucion_reporte]] | `grupo_id` | [[grupo]] | 09 → 02 | sí | [[ejecucion_reporte.grupo_id → grupo\|ver]] |
| [[ejecucion_reporte]] | `solicitado_por` | [[usuario]] | 09 → 01 | no | [[ejecucion_reporte.solicitado_por → usuario\|ver]] |
| [[proceso_anonimizacion]] | `usuario_id` | [[usuario]] | 09 → 01 | no | [[proceso_anonimizacion.usuario_id → usuario\|ver]] |
| [[registro_acceso_datos]] | `usuario_afectado_id` | [[usuario]] | 09 → 01 | no | [[registro_acceso_datos.usuario_afectado_id → usuario\|ver]] |
| [[registro_acceso_datos]] | `usuario_consultor_id` | [[usuario]] | 09 → 01 | no | [[registro_acceso_datos.usuario_consultor_id → usuario\|ver]] |
| [[reporte_operacion_sospechosa]] | `aprobado_por` | [[usuario]] | 09 → 01 | sí | [[reporte_operacion_sospechosa.aprobado_por → usuario\|ver]] |
| [[reporte_operacion_sospechosa]] | `usuario_id` | [[usuario]] | 09 → 01 | no | [[reporte_operacion_sospechosa.usuario_id → usuario\|ver]] |
| [[solicitud_datos_personales]] | `atendida_por` | [[usuario]] | 09 → 01 | sí | [[solicitud_datos_personales.atendida_por → usuario\|ver]] |
| [[solicitud_datos_personales]] | `usuario_id` | [[usuario]] | 09 → 01 | no | [[solicitud_datos_personales.usuario_id → usuario\|ver]] |
| [[ticket_soporte]] | `asignado_a` | [[usuario]] | 09 → 01 | sí | [[ticket_soporte.asignado_a → usuario\|ver]] |
| [[ticket_soporte]] | `usuario_id` | [[usuario]] | 09 → 01 | no | [[ticket_soporte.usuario_id → usuario\|ver]] |
| [[bloqueo_saldo]] | `levantada_por` | [[usuario]] | 10 → 01 | sí | [[bloqueo_saldo.levantada_por → usuario\|ver]] |
| [[certificado_saldo]] | `solicitado_por` | [[usuario]] | 10 → 01 | no | [[certificado_saldo.solicitado_por → usuario\|ver]] |
| [[conciliacion_custodia]] | `cierre_diario_id` | [[cierre_diario]] | 10 → 03 | sí | [[conciliacion_custodia.cierre_diario_id → cierre_diario\|ver]] |
| [[conciliacion_custodia]] | `ejecutada_por` | [[usuario]] | 10 → 01 | sí | [[conciliacion_custodia.ejecutada_por → usuario\|ver]] |
| [[cuenta_billetera]] | `cuenta_contable_id` | [[cuenta_contable]] | 10 → 03 | sí | [[cuenta_billetera.cuenta_contable_id → cuenta_contable\|ver]] |
| [[cuenta_billetera]] | `grupo_id` | [[grupo]] | 10 → 02 | sí | [[cuenta_billetera.grupo_id → grupo\|ver]] |
| [[cuenta_billetera]] | `usuario_id` | [[usuario]] | 10 → 01 | sí | [[cuenta_billetera.usuario_id → usuario\|ver]] |
| [[descuadre_custodia]] | `incidente_operativo_id` | [[incidente_operativo]] | 10 → 09 | sí | [[descuadre_custodia.incidente_operativo_id → incidente_operativo\|ver]] |
| [[descuadre_custodia]] | `resuelto_por` | [[usuario]] | 10 → 01 | sí | [[descuadre_custodia.resuelto_por → usuario\|ver]] |
| [[evaluacion_antifraude]] | `revisada_por` | [[usuario]] | 10 → 01 | sí | [[evaluacion_antifraude.revisada_por → usuario\|ver]] |
| [[instrumento_fondeo]] | `usuario_id` | [[usuario]] | 10 → 01 | no | [[instrumento_fondeo.usuario_id → usuario\|ver]] |
| [[movimiento_custodia]] | `movimiento_bancario_id` | [[movimiento_bancario]] | 10 → 03 | sí | [[movimiento_custodia.movimiento_bancario_id → movimiento_bancario\|ver]] |
| [[orden_recarga]] | `pago_id` | [[pago]] | 10 → 03 | sí | [[orden_recarga.pago_id → pago\|ver]] |
| [[orden_recarga]] | `proveedor_id` | [[proveedor_pago]] | 10 → 03 | sí | [[orden_recarga.proveedor_id → proveedor_pago\|ver]] |
| [[orden_retiro]] | `aprobada_por` | [[usuario]] | 10 → 01 | sí | [[orden_retiro.aprobada_por → usuario\|ver]] |
| [[orden_retiro]] | `proveedor_id` | [[proveedor_pago]] | 10 → 03 | sí | [[orden_retiro.proveedor_id → proveedor_pago\|ver]] |
| [[orden_retiro]] | `solicitada_por` | [[usuario]] | 10 → 01 | no | [[orden_retiro.solicitada_por → usuario\|ver]] |
| [[politica_billetera]] | `aprobada_por` | [[usuario]] | 10 → 01 | sí | [[politica_billetera.aprobada_por → usuario\|ver]] |
| [[regla_antifraude]] | `aprobada_por` | [[usuario]] | 10 → 01 | sí | [[regla_antifraude.aprobada_por → usuario\|ver]] |
| [[respuesta_idempotente]] | `usuario_id` | [[usuario]] | 10 → 01 | no | [[respuesta_idempotente.usuario_id → usuario\|ver]] |
| [[retencion_saldo]] | `liberada_por` | [[usuario]] | 10 → 01 | sí | [[retencion_saldo.liberada_por → usuario\|ver]] |
| [[reverso_transaccion]] | `autorizada_por` | [[usuario]] | 10 → 01 | no | [[reverso_transaccion.autorizada_por → usuario\|ver]] |
| [[solicitud_cierre_billetera]] | `aprobada_por` | [[usuario]] | 10 → 01 | sí | [[solicitud_cierre_billetera.aprobada_por → usuario\|ver]] |
| [[transaccion_billetera]] | `asiento_contable_id` | [[asiento_contable]] | 10 → 03 | sí | [[transaccion_billetera.asiento_contable_id → asiento_contable\|ver]] |
| [[transaccion_billetera]] | `dispositivo_id` | [[dispositivo]] | 10 → 01 | sí | [[transaccion_billetera.dispositivo_id → dispositivo\|ver]] |
| [[transaccion_billetera]] | `grupo_id` | [[grupo]] | 10 → 02 | sí | [[transaccion_billetera.grupo_id → grupo\|ver]] |
| [[transaccion_billetera]] | `iniciada_por` | [[usuario]] | 10 → 01 | sí | [[transaccion_billetera.iniciada_por → usuario\|ver]] |
| [[transaccion_billetera]] | `sesion_id` | [[sesion]] | 10 → 01 | sí | [[transaccion_billetera.sesion_id → sesion\|ver]] |
| [[transferencia_p2p]] | `grupo_id` | [[grupo]] | 10 → 02 | sí | [[transferencia_p2p.grupo_id → grupo\|ver]] |
| [[transferencia_p2p]] | `obligacion_id` | [[obligacion_aporte]] | 10 → 03 | sí | [[transferencia_p2p.obligacion_id → obligacion_aporte\|ver]] |
| [[asignacion_tarifario]] | `autorizado_por` | [[usuario]] | 11 → 01 | sí | [[asignacion_tarifario.autorizado_por → usuario\|ver]] |
| [[asignacion_tarifario]] | `grupo_id` | [[grupo]] | 11 → 02 | sí | [[asignacion_tarifario.grupo_id → grupo\|ver]] |
| [[asignacion_tarifario]] | `usuario_id` | [[usuario]] | 11 → 01 | sí | [[asignacion_tarifario.usuario_id → usuario\|ver]] |
| [[cambio_tarifario]] | `aprobado_por` | [[usuario]] | 11 → 01 | no | [[cambio_tarifario.aprobado_por → usuario\|ver]] |
| [[campana_promocional]] | `aprobada_por` | [[usuario]] | 11 → 01 | no | [[campana_promocional.aprobada_por → usuario\|ver]] |
| [[cargo_comision]] | `deduccion_entrega_id` | [[deduccion_entrega]] | 11 → 04 | sí | [[cargo_comision.deduccion_entrega_id → deduccion_entrega\|ver]] |
| [[cargo_comision]] | `obligacion_id` | [[obligacion_aporte]] | 11 → 03 | sí | [[cargo_comision.obligacion_id → obligacion_aporte\|ver]] |
| [[cargo_comision]] | `transaccion_id` | [[transaccion_billetera]] | 11 → 10 | sí | [[cargo_comision.transaccion_id → transaccion_billetera\|ver]] |
| [[concepto_tarifa]] | `cuenta_ingreso_id` | [[cuenta_contable]] | 11 → 03 | sí | [[concepto_tarifa.cuenta_ingreso_id → cuenta_contable\|ver]] |
| [[costo_proveedor_operacion]] | `proveedor_id` | [[proveedor_pago]] | 11 → 03 | no | [[costo_proveedor_operacion.proveedor_id → proveedor_pago\|ver]] |
| [[costo_proveedor_operacion]] | `transaccion_id` | [[transaccion_billetera]] | 11 → 10 | sí | [[costo_proveedor_operacion.transaccion_id → transaccion_billetera\|ver]] |
| [[cuenta_por_cobrar_comision]] | `gestion_cobranza_id` | [[gestion_cobranza]] | 11 → 08 | sí | [[cuenta_por_cobrar_comision.gestion_cobranza_id → gestion_cobranza\|ver]] |
| [[cuenta_por_cobrar_comision]] | `usuario_id` | [[usuario]] | 11 → 01 | no | [[cuenta_por_cobrar_comision.usuario_id → usuario\|ver]] |
| [[datos_facturacion]] | `usuario_id` | [[usuario]] | 11 → 01 | no | [[datos_facturacion.usuario_id → usuario\|ver]] |
| [[devengo_comision]] | `asiento_contable_id` | [[asiento_contable]] | 11 → 03 | sí | [[devengo_comision.asiento_contable_id → asiento_contable\|ver]] |
| [[devengo_comision]] | `grupo_id` | [[grupo]] | 11 → 02 | sí | [[devengo_comision.grupo_id → grupo\|ver]] |
| [[devengo_comision]] | `participante_id` | [[participante]] | 11 → 02 | sí | [[devengo_comision.participante_id → participante\|ver]] |
| [[devengo_comision]] | `usuario_obligado_id` | [[usuario]] | 11 → 01 | no | [[devengo_comision.usuario_obligado_id → usuario\|ver]] |
| [[devolucion_comision]] | `autorizada_por` | [[usuario]] | 11 → 01 | no | [[devolucion_comision.autorizada_por → usuario\|ver]] |
| [[devolucion_comision]] | `reclamo_id` | [[reclamo_cliente]] | 11 → 12 | sí | [[devolucion_comision.reclamo_id → reclamo_cliente\|ver]] |
| [[devolucion_comision]] | `transaccion_id` | [[transaccion_billetera]] | 11 → 10 | sí | [[devolucion_comision.transaccion_id → transaccion_billetera\|ver]] |
| [[evento_significativo_sin]] | `registrado_por` | [[usuario]] | 11 → 01 | sí | [[evento_significativo_sin.registrado_por → usuario\|ver]] |
| [[exencion_comision]] | `autorizada_por` | [[usuario]] | 11 → 01 | no | [[exencion_comision.autorizada_por → usuario\|ver]] |
| [[exencion_comision]] | `grupo_id` | [[grupo]] | 11 → 02 | sí | [[exencion_comision.grupo_id → grupo\|ver]] |
| [[exencion_comision]] | `usuario_id` | [[usuario]] | 11 → 01 | sí | [[exencion_comision.usuario_id → usuario\|ver]] |
| [[factura_electronica]] | `usuario_id` | [[usuario]] | 11 → 01 | no | [[factura_electronica.usuario_id → usuario\|ver]] |
| [[impuesto]] | `cuenta_contable_id` | [[cuenta_contable]] | 11 → 03 | sí | [[impuesto.cuenta_contable_id → cuenta_contable\|ver]] |
| [[liquidacion_ingresos]] | `asiento_contable_id` | [[asiento_contable]] | 11 → 03 | sí | [[liquidacion_ingresos.asiento_contable_id → asiento_contable\|ver]] |
| [[liquidacion_ingresos]] | `cerrada_por` | [[usuario]] | 11 → 01 | sí | [[liquidacion_ingresos.cerrada_por → usuario\|ver]] |
| [[simulacion_tarifa]] | `ejecutada_por` | [[usuario]] | 11 → 01 | no | [[simulacion_tarifa.ejecutada_por → usuario\|ver]] |
| [[tarifa_congelada_grupo]] | `acuerdo_id` | [[acuerdo]] | 11 → 02 | sí | [[tarifa_congelada_grupo.acuerdo_id → acuerdo\|ver]] |
| [[tarifa_congelada_grupo]] | `grupo_id` | [[grupo]] | 11 → 02 | no | [[tarifa_congelada_grupo.grupo_id → grupo\|ver]] |
| [[tarifario]] | `aprobado_por` | [[usuario]] | 11 → 01 | sí | [[tarifario.aprobado_por → usuario\|ver]] |
| [[aceptacion_contrato]] | `dispositivo_id` | [[dispositivo]] | 12 → 01 | sí | [[aceptacion_contrato.dispositivo_id → dispositivo\|ver]] |
| [[aceptacion_contrato]] | `token_firma_id` | [[token_verificacion]] | 12 → 01 | sí | [[aceptacion_contrato.token_firma_id → token_verificacion\|ver]] |
| [[aceptacion_contrato]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[aceptacion_contrato.usuario_id → usuario\|ver]] |
| [[acta_comite]] | `elaborada_por` | [[usuario]] | 12 → 01 | sí | [[acta_comite.elaborada_por → usuario\|ver]] |
| [[activo_informacion]] | `custodio_id` | [[usuario]] | 12 → 01 | sí | [[activo_informacion.custodio_id → usuario\|ver]] |
| [[activo_informacion]] | `propietario_id` | [[usuario]] | 12 → 01 | sí | [[activo_informacion.propietario_id → usuario\|ver]] |
| [[alerta_monitoreo_lft]] | `asignada_a` | [[usuario]] | 12 → 01 | sí | [[alerta_monitoreo_lft.asignada_a → usuario\|ver]] |
| [[alerta_monitoreo_lft]] | `cuenta_billetera_id` | [[cuenta_billetera]] | 12 → 10 | sí | [[alerta_monitoreo_lft.cuenta_billetera_id → cuenta_billetera\|ver]] |
| [[alerta_monitoreo_lft]] | `transaccion_id` | [[transaccion_billetera]] | 12 → 10 | sí | [[alerta_monitoreo_lft.transaccion_id → transaccion_billetera\|ver]] |
| [[alerta_monitoreo_lft]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[alerta_monitoreo_lft.usuario_id → usuario\|ver]] |
| [[beneficiario_final]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[beneficiario_final.usuario_id → usuario\|ver]] |
| [[calificacion_riesgo_cliente]] | `calificado_por` | [[usuario]] | 12 → 01 | sí | [[calificacion_riesgo_cliente.calificado_por → usuario\|ver]] |
| [[calificacion_riesgo_cliente]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[calificacion_riesgo_cliente.usuario_id → usuario\|ver]] |
| [[capacitacion_cumplimiento]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[capacitacion_cumplimiento.usuario_id → usuario\|ver]] |
| [[caso_investigacion_lft]] | `analista_id` | [[usuario]] | 12 → 01 | no | [[caso_investigacion_lft.analista_id → usuario\|ver]] |
| [[caso_investigacion_lft]] | `reporte_operacion_sospechosa_id` | [[reporte_operacion_sospechosa]] | 12 → 09 | sí | [[caso_investigacion_lft.reporte_operacion_sospechosa_id → reporte_operacion_sospechosa\|ver]] |
| [[caso_investigacion_lft]] | `revisado_por` | [[usuario]] | 12 → 01 | sí | [[caso_investigacion_lft.revisado_por → usuario\|ver]] |
| [[caso_investigacion_lft]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[caso_investigacion_lft.usuario_id → usuario\|ver]] |
| [[contrato_adhesion]] | `aprobado_por` | [[usuario]] | 12 → 01 | sí | [[contrato_adhesion.aprobado_por → usuario\|ver]] |
| [[contrato_tercero]] | `responsable_id` | [[usuario]] | 12 → 01 | sí | [[contrato_tercero.responsable_id → usuario\|ver]] |
| [[control_interno]] | `responsable_id` | [[usuario]] | 12 → 01 | sí | [[control_interno.responsable_id → usuario\|ver]] |
| [[debida_diligencia]] | `aprobada_por` | [[usuario]] | 12 → 01 | sí | [[debida_diligencia.aprobada_por → usuario\|ver]] |
| [[debida_diligencia]] | `segunda_revision_por` | [[usuario]] | 12 → 01 | sí | [[debida_diligencia.segunda_revision_por → usuario\|ver]] |
| [[debida_diligencia]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[debida_diligencia.usuario_id → usuario\|ver]] |
| [[debida_diligencia]] | `verificacion_kyc_id` | [[verificacion_kyc]] | 12 → 01 | sí | [[debida_diligencia.verificacion_kyc_id → verificacion_kyc\|ver]] |
| [[declaracion_origen_fondos]] | `transaccion_id` | [[transaccion_billetera]] | 12 → 10 | sí | [[declaracion_origen_fondos.transaccion_id → transaccion_billetera\|ver]] |
| [[declaracion_origen_fondos]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[declaracion_origen_fondos.usuario_id → usuario\|ver]] |
| [[declaracion_origen_fondos]] | `verificada_por` | [[usuario]] | 12 → 01 | sí | [[declaracion_origen_fondos.verificada_por → usuario\|ver]] |
| [[declaracion_pep]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[declaracion_pep.usuario_id → usuario\|ver]] |
| [[declaracion_pep]] | `verificada_por` | [[usuario]] | 12 → 01 | sí | [[declaracion_pep.verificada_por → usuario\|ver]] |
| [[designacion_regulatoria]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[designacion_regulatoria.usuario_id → usuario\|ver]] |
| [[desvio_perfil]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[desvio_perfil.usuario_id → usuario\|ver]] |
| [[documento_publicado]] | `publicado_por` | [[usuario]] | 12 → 01 | sí | [[documento_publicado.publicado_por → usuario\|ver]] |
| [[envio_regulatorio]] | `enviado_por` | [[usuario]] | 12 → 01 | sí | [[envio_regulatorio.enviado_por → usuario\|ver]] |
| [[evaluacion_riesgo_producto]] | `aprobada_por` | [[usuario]] | 12 → 01 | sí | [[evaluacion_riesgo_producto.aprobada_por → usuario\|ver]] |
| [[evaluacion_tercero]] | `evaluado_por` | [[usuario]] | 12 → 01 | no | [[evaluacion_tercero.evaluado_por → usuario\|ver]] |
| [[evento_riesgo_operativo]] | `incidente_operativo_id` | [[incidente_operativo]] | 12 → 09 | sí | [[evento_riesgo_operativo.incidente_operativo_id → incidente_operativo\|ver]] |
| [[evento_riesgo_operativo]] | `registrado_por` | [[usuario]] | 12 → 01 | no | [[evento_riesgo_operativo.registrado_por → usuario\|ver]] |
| [[expediente_cliente]] | `responsable_id` | [[usuario]] | 12 → 01 | sí | [[expediente_cliente.responsable_id → usuario\|ver]] |
| [[expediente_cliente]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[expediente_cliente.usuario_id → usuario\|ver]] |
| [[factor_riesgo_evaluado]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[factor_riesgo_evaluado.usuario_id → usuario\|ver]] |
| [[hallazgo_auditoria]] | `responsable_id` | [[usuario]] | 12 → 01 | sí | [[hallazgo_auditoria.responsable_id → usuario\|ver]] |
| [[incidente_seguridad]] | `incidente_operativo_id` | [[incidente_operativo]] | 12 → 09 | sí | [[incidente_seguridad.incidente_operativo_id → incidente_operativo\|ver]] |
| [[incidente_seguridad]] | `responsable_id` | [[usuario]] | 12 → 01 | sí | [[incidente_seguridad.responsable_id → usuario\|ver]] |
| [[licencia_regulatoria]] | `responsable_id` | [[usuario]] | 12 → 01 | sí | [[licencia_regulatoria.responsable_id → usuario\|ver]] |
| [[matriz_riesgo_lft]] | `aprobada_por` | [[usuario]] | 12 → 01 | sí | [[matriz_riesgo_lft.aprobada_por → usuario\|ver]] |
| [[observacion_regulatoria]] | `responsable_id` | [[usuario]] | 12 → 01 | sí | [[observacion_regulatoria.responsable_id → usuario\|ver]] |
| [[oficial_cumplimiento]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[oficial_cumplimiento.usuario_id → usuario\|ver]] |
| [[perfil_transaccional]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[perfil_transaccional.usuario_id → usuario\|ver]] |
| [[plan_accion_riesgo]] | `responsable_id` | [[usuario]] | 12 → 01 | no | [[plan_accion_riesgo.responsable_id → usuario\|ver]] |
| [[plan_continuidad]] | `responsable_id` | [[usuario]] | 12 → 01 | sí | [[plan_continuidad.responsable_id → usuario\|ver]] |
| [[politica_interna]] | `responsable_id` | [[usuario]] | 12 → 01 | sí | [[politica_interna.responsable_id → usuario\|ver]] |
| [[prueba_continuidad]] | `ejecutada_por` | [[usuario]] | 12 → 01 | no | [[prueba_continuidad.ejecutada_por → usuario\|ver]] |
| [[prueba_control]] | `ejecutada_por` | [[usuario]] | 12 → 01 | no | [[prueba_control.ejecutada_por → usuario\|ver]] |
| [[punto_reclamo]] | `responsable_id` | [[usuario]] | 12 → 01 | sí | [[punto_reclamo.responsable_id → usuario\|ver]] |
| [[reclamo_cliente]] | `devolucion_comision_id` | [[devolucion_comision]] | 12 → 11 | sí | [[reclamo_cliente.devolucion_comision_id → devolucion_comision\|ver]] |
| [[reclamo_cliente]] | `responsable_id` | [[usuario]] | 12 → 01 | sí | [[reclamo_cliente.responsable_id → usuario\|ver]] |
| [[reclamo_cliente]] | `ticket_soporte_id` | [[ticket_soporte]] | 12 → 09 | sí | [[reclamo_cliente.ticket_soporte_id → ticket_soporte\|ver]] |
| [[reclamo_cliente]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[reclamo_cliente.usuario_id → usuario\|ver]] |
| [[registro_operacion_relevante]] | `transaccion_id` | [[transaccion_billetera]] | 12 → 10 | no | [[registro_operacion_relevante.transaccion_id → transaccion_billetera\|ver]] |
| [[registro_operacion_relevante]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[registro_operacion_relevante.usuario_id → usuario\|ver]] |
| [[regla_monitoreo_lft]] | `aprobada_por` | [[usuario]] | 12 → 01 | sí | [[regla_monitoreo_lft.aprobada_por → usuario\|ver]] |
| [[reporte_regulatorio]] | `aprobado_por` | [[usuario]] | 12 → 01 | sí | [[reporte_regulatorio.aprobado_por → usuario\|ver]] |
| [[reporte_regulatorio]] | `generado_por` | [[usuario]] | 12 → 01 | sí | [[reporte_regulatorio.generado_por → usuario\|ver]] |
| [[reporte_regulatorio]] | `revisado_por` | [[usuario]] | 12 → 01 | sí | [[reporte_regulatorio.revisado_por → usuario\|ver]] |
| [[requerimiento_autoridad]] | `bloqueo_saldo_id` | [[bloqueo_saldo]] | 12 → 10 | sí | [[requerimiento_autoridad.bloqueo_saldo_id → bloqueo_saldo\|ver]] |
| [[requerimiento_autoridad]] | `respondido_por` | [[usuario]] | 12 → 01 | sí | [[requerimiento_autoridad.respondido_por → usuario\|ver]] |
| [[requerimiento_autoridad]] | `usuario_afectado_id` | [[usuario]] | 12 → 01 | sí | [[requerimiento_autoridad.usuario_afectado_id → usuario\|ver]] |
| [[revision_periodica_kyc]] | `ejecutada_por` | [[usuario]] | 12 → 01 | sí | [[revision_periodica_kyc.ejecutada_por → usuario\|ver]] |
| [[revision_periodica_kyc]] | `usuario_id` | [[usuario]] | 12 → 01 | no | [[revision_periodica_kyc.usuario_id → usuario\|ver]] |
| [[asiento_plantilla]] | `creada_por` | [[usuario]] | 13 → 01 | no | [[asiento_plantilla.creada_por → usuario\|ver]] |
| [[categoria_activo_fijo]] | `cuenta_activo_id` | [[cuenta_contable]] | 13 → 03 | no | [[categoria_activo_fijo.cuenta_activo_id → cuenta_contable\|ver]] |
| [[categoria_activo_fijo]] | `cuenta_depreciacion_id` | [[cuenta_contable]] | 13 → 03 | no | [[categoria_activo_fijo.cuenta_depreciacion_id → cuenta_contable\|ver]] |
| [[categoria_activo_fijo]] | `cuenta_gasto_depreciacion_id` | [[cuenta_contable]] | 13 → 03 | no | [[categoria_activo_fijo.cuenta_gasto_depreciacion_id → cuenta_contable\|ver]] |
| [[cierre_periodo_contable]] | `cerrado_por` | [[usuario]] | 13 → 01 | no | [[cierre_periodo_contable.cerrado_por → usuario\|ver]] |
| [[cobro_cuenta_por_cobrar]] | `asiento_contable_id` | [[asiento_contable]] | 13 → 03 | sí | [[cobro_cuenta_por_cobrar.asiento_contable_id → asiento_contable\|ver]] |
| [[depreciacion_activo]] | `asiento_contable_id` | [[asiento_contable]] | 13 → 03 | sí | [[depreciacion_activo.asiento_contable_id → asiento_contable\|ver]] |
| [[ejercicio_fiscal]] | `cerrado_por` | [[usuario]] | 13 → 01 | sí | [[ejercicio_fiscal.cerrado_por → usuario\|ver]] |
| [[estado_financiero_generado]] | `generado_por` | [[usuario]] | 13 → 01 | no | [[estado_financiero_generado.generado_por → usuario\|ver]] |
| [[factura_proveedor]] | `aprobada_por` | [[usuario]] | 13 → 01 | sí | [[factura_proveedor.aprobada_por → usuario\|ver]] |
| [[factura_proveedor]] | `asiento_contable_id` | [[asiento_contable]] | 13 → 03 | sí | [[factura_proveedor.asiento_contable_id → asiento_contable\|ver]] |
| [[linea_plantilla_asiento]] | `cuenta_contable_id` | [[cuenta_contable]] | 13 → 03 | no | [[linea_plantilla_asiento.cuenta_contable_id → cuenta_contable\|ver]] |
| [[orden_compra]] | `aprobada_por` | [[usuario]] | 13 → 01 | sí | [[orden_compra.aprobada_por → usuario\|ver]] |
| [[pago_a_proveedor]] | `asiento_contable_id` | [[asiento_contable]] | 13 → 03 | sí | [[pago_a_proveedor.asiento_contable_id → asiento_contable\|ver]] |
| [[pago_a_proveedor]] | `autorizado_por` | [[usuario]] | 13 → 01 | no | [[pago_a_proveedor.autorizado_por → usuario\|ver]] |
| [[partida_presupuestaria]] | `cuenta_contable_id` | [[cuenta_contable]] | 13 → 03 | no | [[partida_presupuestaria.cuenta_contable_id → cuenta_contable\|ver]] |
| [[presupuesto]] | `aprobado_por` | [[usuario]] | 13 → 01 | sí | [[presupuesto.aprobado_por → usuario\|ver]] |
| [[tercero_comercial]] | `cuenta_contable_id` | [[cuenta_contable]] | 13 → 03 | sí | [[tercero_comercial.cuenta_contable_id → cuenta_contable\|ver]] |
| [[anunciante]] | `organizador_id` | [[organizador]] | 14 → 07 | sí | [[anunciante.organizador_id → organizador\|ver]] |
| [[campana_publicitaria]] | `aprobada_por` | [[usuario]] | 14 → 01 | sí | [[campana_publicitaria.aprobada_por → usuario\|ver]] |
| [[clic_anuncio]] | `usuario_id` | [[usuario]] | 14 → 01 | sí | [[clic_anuncio.usuario_id → usuario\|ver]] |
| [[factura_publicidad]] | `cuenta_por_cobrar_id` | [[cuenta_por_cobrar]] | 14 → 13 | sí | [[factura_publicidad.cuenta_por_cobrar_id → cuenta_por_cobrar\|ver]] |
| [[factura_publicidad]] | `factura_electronica_id` | [[factura_electronica]] | 14 → 11 | sí | [[factura_publicidad.factura_electronica_id → factura_electronica\|ver]] |
| [[impresion_anuncio]] | `usuario_id` | [[usuario]] | 14 → 01 | sí | [[impresion_anuncio.usuario_id → usuario\|ver]] |
| [[revision_creativa]] | `revisada_por` | [[usuario]] | 14 → 01 | no | [[revision_creativa.revisada_por → usuario\|ver]] |
| [[segmento_audiencia]] | `creado_por` | [[usuario]] | 14 → 01 | no | [[segmento_audiencia.creado_por → usuario\|ver]] |
| [[socio_comercial]] | `verificado_por` | [[usuario]] | 14 → 01 | sí | [[socio_comercial.verificado_por → usuario\|ver]] |

## 01 — Identidad, Usuarios y Seguridad

> [[_Relaciones 01|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[asignacion_rol.otorgada_por → usuario]] | [[usuario]] | — | no |
| [[asignacion_rol.rol_id → rol]] | [[rol]] | — | no |
| [[asignacion_rol.usuario_id → usuario]] | [[usuario]] | — | no |
| [[bloqueo_cuenta.liberada_por → usuario]] | [[usuario]] | — | sí |
| [[bloqueo_cuenta.usuario_id → usuario]] | [[usuario]] | — | no |
| [[consentimiento.usuario_id → usuario]] | [[usuario]] | — | no |
| [[credencial_acceso.usuario_id → usuario]] | [[usuario]] | — | no |
| [[direccion_usuario.usuario_id → usuario]] | [[usuario]] | — | no |
| [[dispositivo.usuario_id → usuario]] | [[usuario]] | — | no |
| [[documento_identidad.usuario_id → usuario]] | [[usuario]] | — | no |
| [[factor_mfa.usuario_id → usuario]] | [[usuario]] | — | no |
| [[historial_credencial.usuario_id → usuario]] | [[usuario]] | — | no |
| [[intento_autenticacion.usuario_id → usuario]] | [[usuario]] | — | sí |
| [[intento_validacion_token.token_id → token_verificacion]] | [[token_verificacion]] | — | no |
| [[perfil_financiero.usuario_id → usuario]] | [[usuario]] | — | no |
| [[preferencia_notificacion.usuario_id → usuario]] | [[usuario]] | — | no |
| [[referencia_personal.usuario_id → usuario]] | [[usuario]] | — | no |
| [[reputacion_usuario.usuario_id → usuario]] | [[usuario]] | — | no |
| [[restriccion_usuario.levantada_por → usuario]] | [[usuario]] | — | sí |
| [[restriccion_usuario.usuario_id → usuario]] | [[usuario]] | — | no |
| [[rol_permiso.permiso_id → permiso]] | [[permiso]] | — | no |
| [[rol_permiso.rol_id → rol]] | [[rol]] | — | no |
| [[sesion.dispositivo_id → dispositivo]] | [[dispositivo]] | — | no |
| [[sesion.usuario_id → usuario]] | [[usuario]] | — | no |
| [[solicitud_baja.usuario_id → usuario]] | [[usuario]] | — | no |
| [[token_verificacion.dispositivo_id → dispositivo]] | [[dispositivo]] | — | sí |
| [[token_verificacion.politica_id → politica_sancion]] | [[politica_sancion]] | ↗ | no |
| [[token_verificacion.rotado_de_id → token_verificacion]] | [[token_verificacion]] | — | sí |
| [[token_verificacion.usuario_id → usuario]] | [[usuario]] | — | sí |
| [[verificacion_kyc.documento_id → documento_identidad]] | [[documento_identidad]] | — | sí |
| [[verificacion_kyc.revisada_por → usuario]] | [[usuario]] | — | sí |
| [[verificacion_kyc.usuario_id → usuario]] | [[usuario]] | — | no |

## 02 — Grupos, Cupos, Turnos y Gobernanza

> [[_Relaciones 02|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[aceptacion_reglamento.participante_id → participante]] | [[participante]] | — | no |
| [[aceptacion_reglamento.reglamento_id → reglamento_grupo]] | [[reglamento_grupo]] | — | no |
| [[aceptacion_reglamento.token_firma_id → token_verificacion]] | [[token_verificacion]] | ↗ | sí |
| [[acuerdo.grupo_id → grupo]] | [[grupo]] | — | no |
| [[acuerdo.propuesto_por → usuario]] | [[usuario]] | ↗ | no |
| [[configuracion_grupo.grupo_id → grupo]] | [[grupo]] | — | no |
| [[configuracion_grupo.politica_mora_id → politica_mora]] | [[politica_mora]] | ↗ | sí |
| [[configuracion_grupo.politica_sancion_id → politica_sancion]] | [[politica_sancion]] | ↗ | sí |
| [[cupo.grupo_id → grupo]] | [[grupo]] | — | no |
| [[cupo.participante_id → participante]] | [[participante]] | — | sí |
| [[dia_no_habil.grupo_id → grupo]] | [[grupo]] | — | sí |
| [[grupo.organizador_id → organizador]] | [[organizador]] | ↗ | sí |
| [[historial_estado_grupo.ejecutado_por → usuario]] | [[usuario]] | ↗ | no |
| [[historial_estado_grupo.grupo_id → grupo]] | [[grupo]] | — | no |
| [[invitacion.emisor_id → usuario]] | [[usuario]] | ↗ | no |
| [[invitacion.grupo_id → grupo]] | [[grupo]] | — | no |
| [[invitacion.token_id → token_verificacion]] | [[token_verificacion]] | ↗ | no |
| [[participante.grupo_id → grupo]] | [[grupo]] | — | no |
| [[participante.invitado_por_id → participante]] | [[participante]] | — | sí |
| [[participante.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[periodo.grupo_id → grupo]] | [[grupo]] | — | no |
| [[postulacion_emparejamiento.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[propuesta_grupo.criterio_id → criterio_emparejamiento]] | [[criterio_emparejamiento]] | — | no |
| [[propuesta_grupo.grupo_materializado_id → grupo]] | [[grupo]] | — | sí |
| [[propuesta_postulacion.postulacion_id → postulacion_emparejamiento]] | [[postulacion_emparejamiento]] | — | no |
| [[propuesta_postulacion.propuesta_id → propuesta_grupo]] | [[propuesta_grupo]] | — | no |
| [[reglamento_grupo.grupo_id → grupo]] | [[grupo]] | — | no |
| [[reglamento_grupo.redactado_por → usuario]] | [[usuario]] | ↗ | no |
| [[solicitud_ingreso.grupo_id → grupo]] | [[grupo]] | — | no |
| [[solicitud_ingreso.revisada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[solicitud_ingreso.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[solicitud_permuta.contraparte_id → participante]] | [[participante]] | — | no |
| [[solicitud_permuta.solicitante_id → participante]] | [[participante]] | — | no |
| [[solicitud_permuta.turno_destino_id → turno]] | [[turno]] | — | no |
| [[solicitud_permuta.turno_origen_id → turno]] | [[turno]] | — | no |
| [[solicitud_retiro.participante_id → participante]] | [[participante]] | — | no |
| [[solicitud_retiro.plan_regularizacion_id → plan_regularizacion]] | [[plan_regularizacion]] | ↗ | sí |
| [[sorteo_turnos.ejecutado_por → usuario]] | [[usuario]] | ↗ | no |
| [[sorteo_turnos.grupo_id → grupo]] | [[grupo]] | — | no |
| [[traspaso_cupo.aprobado_por_acuerdo_id → acuerdo]] | [[acuerdo]] | — | sí |
| [[traspaso_cupo.cupo_id → cupo]] | [[cupo]] | — | no |
| [[traspaso_cupo.participante_destino_id → participante]] | [[participante]] | — | no |
| [[traspaso_cupo.participante_origen_id → participante]] | [[participante]] | — | no |
| [[turno.cupo_id → cupo]] | [[cupo]] | — | no |
| [[turno.grupo_id → grupo]] | [[grupo]] | — | no |
| [[turno.periodo_id → periodo]] | [[periodo]] | — | no |
| [[turno.permutado_con_turno_id → turno]] | [[turno]] | — | sí |
| [[voto_participante.acuerdo_id → acuerdo]] | [[acuerdo]] | — | no |
| [[voto_participante.participante_id → participante]] | [[participante]] | — | no |

## 03 — Aportes, Pagos QR y Conciliación

> [[_Relaciones 03|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[asiento_contable.asiento_reversa_id → asiento_contable]] | [[asiento_contable]] | — | sí |
| [[asiento_contable.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[asiento_contable.periodo_contable_id → periodo_contable]] | [[periodo_contable]] | ↗ | sí |
| [[asiento_contable.registrado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[cierre_diario.cerrado_por → usuario]] | [[usuario]] | ↗ | no |
| [[comprobante_manual.pago_id → pago]] | [[pago]] | — | no |
| [[comprobante_manual.revisado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[comprobante_manual.segunda_revision_por → usuario]] | [[usuario]] | ↗ | sí |
| [[conciliacion.conciliado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[conciliacion.movimiento_bancario_id → movimiento_bancario]] | [[movimiento_bancario]] | — | sí |
| [[conciliacion.pago_id → pago]] | [[pago]] | — | no |
| [[constancia_pago.pago_id → pago]] | [[pago]] | — | no |
| [[cuenta_contable.cuenta_padre_id → cuenta_contable]] | [[cuenta_contable]] | — | sí |
| [[cuenta_contable.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[cuenta_contable.participante_id → participante]] | [[participante]] | ↗ | sí |
| [[disputa_pago.pago_id → pago]] | [[pago]] | — | no |
| [[enlace_pago_rapido.orden_cobro_id → orden_cobro]] | [[orden_cobro]] | — | no |
| [[enlace_pago_rapido.token_id → token_verificacion]] | [[token_verificacion]] | ↗ | no |
| [[excepcion_conciliacion.asignada_a → usuario]] | [[usuario]] | ↗ | sí |
| [[excepcion_conciliacion.conciliacion_id → conciliacion]] | [[conciliacion]] | — | no |
| [[extracto_bancario.importado_por → usuario]] | [[usuario]] | ↗ | no |
| [[extracto_bancario.proveedor_id → proveedor_pago]] | [[proveedor_pago]] | — | no |
| [[intento_pago.orden_cobro_id → orden_cobro]] | [[orden_cobro]] | — | no |
| [[movimiento_bancario.extracto_id → extracto_bancario]] | [[extracto_bancario]] | — | no |
| [[movimiento_contable.asiento_id → asiento_contable]] | [[asiento_contable]] | — | no |
| [[movimiento_contable.cuenta_id → cuenta_contable]] | [[cuenta_contable]] | — | no |
| [[obligacion_aporte.cupo_id → cupo]] | [[cupo]] | ↗ | no |
| [[obligacion_aporte.grupo_id → grupo]] | [[grupo]] | ↗ | no |
| [[obligacion_aporte.obligacion_origen_id → obligacion_aporte]] | [[obligacion_aporte]] | — | sí |
| [[obligacion_aporte.participante_id → participante]] | [[participante]] | ↗ | no |
| [[obligacion_aporte.periodo_id → periodo]] | [[periodo]] | ↗ | no |
| [[obligacion_aporte.plan_regularizacion_id → plan_regularizacion]] | [[plan_regularizacion]] | — | sí |
| [[obligacion_aporte.politica_mora_id → politica_mora]] | [[politica_mora]] | — | sí |
| [[orden_cobro.obligacion_id → obligacion_aporte]] | [[obligacion_aporte]] | — | no |
| [[orden_cobro.proveedor_id → proveedor_pago]] | [[proveedor_pago]] | — | no |
| [[pago.intento_pago_id → intento_pago]] | [[intento_pago]] | — | sí |
| [[pago.obligacion_id → obligacion_aporte]] | [[obligacion_aporte]] | — | no |
| [[pago.proveedor_id → proveedor_pago]] | [[proveedor_pago]] | — | sí |
| [[pago.registrado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[plan_regularizacion.aprobado_por → usuario]] | [[usuario]] | ↗ | no |
| [[plan_regularizacion.participante_id → participante]] | [[participante]] | ↗ | no |
| [[politica_mora.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[qr_cobro.orden_cobro_id → orden_cobro]] | [[orden_cobro]] | — | no |
| [[reembolso.aprobado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[reembolso.pago_id → pago]] | [[pago]] | — | no |
| [[reembolso.solicitado_por → usuario]] | [[usuario]] | ↗ | no |
| [[webhook_pasarela.pago_id → pago]] | [[pago]] | — | sí |
| [[webhook_pasarela.proveedor_id → proveedor_pago]] | [[proveedor_pago]] | — | no |

## 04 — Entregas de Fondo

> [[_Relaciones 04|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[confirmacion_recepcion.entrega_id → entrega_fondo]] | [[entrega_fondo]] | — | no |
| [[confirmacion_recepcion.token_confirmacion_id → token_verificacion]] | [[token_verificacion]] | ↗ | sí |
| [[cuenta_bancaria_beneficiario.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[deduccion_entrega.entrega_id → entrega_fondo]] | [[entrega_fondo]] | — | no |
| [[entrega_fondo.autorizada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[entrega_fondo.beneficiario_participante_id → participante]] | [[participante]] | ↗ | no |
| [[entrega_fondo.cuenta_destino_id → cuenta_bancaria_beneficiario]] | [[cuenta_bancaria_beneficiario]] | — | sí |
| [[entrega_fondo.cupo_id → cupo]] | [[cupo]] | ↗ | no |
| [[entrega_fondo.ejecutada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[entrega_fondo.grupo_id → grupo]] | [[grupo]] | ↗ | no |
| [[entrega_fondo.periodo_id → periodo]] | [[periodo]] | ↗ | no |
| [[entrega_fondo.turno_id → turno]] | [[turno]] | ↗ | no |
| [[historial_estado_entrega.ejecutado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[historial_estado_entrega.entrega_id → entrega_fondo]] | [[entrega_fondo]] | — | no |
| [[incidencia_entrega.asignada_a → usuario]] | [[usuario]] | ↗ | sí |
| [[incidencia_entrega.entrega_id → entrega_fondo]] | [[entrega_fondo]] | — | no |
| [[incidencia_entrega.reportada_por → usuario]] | [[usuario]] | ↗ | no |
| [[intento_desembolso.orden_desembolso_id → orden_desembolso]] | [[orden_desembolso]] | — | no |
| [[orden_desembolso.cuenta_destino_id → cuenta_bancaria_beneficiario]] | [[cuenta_bancaria_beneficiario]] | — | no |
| [[orden_desembolso.entrega_id → entrega_fondo]] | [[entrega_fondo]] | — | no |
| [[orden_desembolso.proveedor_id → proveedor_pago]] | [[proveedor_pago]] | ↗ | no |
| [[validacion_pre_entrega.entrega_id → entrega_fondo]] | [[entrega_fondo]] | — | no |
| [[validacion_pre_entrega.omitida_por → usuario]] | [[usuario]] | ↗ | sí |
| [[validacion_pre_entrega.regla_id → regla_entrega]] | [[regla_entrega]] | — | no |

## 05 — Notificaciones y Comunicaciones

> [[_Relaciones 05|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[bandeja_entrada.notificacion_id → notificacion]] | [[notificacion]] | — | no |
| [[bandeja_entrada.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[canal_vinculado.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[cola_envio.envio_id → envio_notificacion]] | [[envio_notificacion]] | — | no |
| [[cola_muerta.envio_id → envio_notificacion]] | [[envio_notificacion]] | — | no |
| [[enlace_pago_notificado.notificacion_id → notificacion]] | [[notificacion]] | — | no |
| [[enlace_pago_notificado.orden_cobro_id → orden_cobro]] | [[orden_cobro]] | ↗ | no |
| [[enlace_pago_notificado.token_id → token_verificacion]] | [[token_verificacion]] | ↗ | no |
| [[envio_notificacion.canal_vinculado_id → canal_vinculado]] | [[canal_vinculado]] | — | sí |
| [[envio_notificacion.notificacion_id → notificacion]] | [[notificacion]] | — | no |
| [[envio_notificacion.proveedor_id → proveedor_mensajeria]] | [[proveedor_mensajeria]] | — | no |
| [[envio_notificacion.version_plantilla_id → version_plantilla]] | [[version_plantilla]] | — | no |
| [[evento_entrega_mensaje.envio_id → envio_notificacion]] | [[envio_notificacion]] | — | no |
| [[notificacion.evento_id → evento_notificable]] | [[evento_notificable]] | — | no |
| [[notificacion.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[plantilla_mensaje.evento_id → evento_notificable]] | [[evento_notificable]] | — | no |
| [[programacion_recordatorio.evento_id → evento_notificable]] | [[evento_notificable]] | — | no |
| [[programacion_recordatorio.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[respuesta_entrante.canal_vinculado_id → canal_vinculado]] | [[canal_vinculado]] | — | no |
| [[respuesta_entrante.notificacion_relacionada_id → notificacion]] | [[notificacion]] | — | sí |
| [[version_plantilla.plantilla_id → plantilla_mensaje]] | [[plantilla_mensaje]] | — | no |

## 06 — Transparencia y Reputación

> [[_Relaciones 06|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[bloque_transparencia.grupo_id → grupo]] | [[grupo]] | ↗ | no |
| [[certificado_reputacion.snapshot_id → snapshot_reputacion]] | [[snapshot_reputacion]] | — | no |
| [[certificado_reputacion.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[componente_score.puntaje_id → puntaje_reputacion]] | [[puntaje_reputacion]] | — | no |
| [[evento_reputacion.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[evento_reputacion.participante_id → participante]] | [[participante]] | ↗ | sí |
| [[evento_reputacion.revertido_por_id → evento_reputacion]] | [[evento_reputacion]] | — | sí |
| [[evento_reputacion.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[insignia_otorgada.insignia_id → insignia_logro]] | [[insignia_logro]] | — | no |
| [[insignia_otorgada.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[metrica_grupo.grupo_id → grupo]] | [[grupo]] | ↗ | no |
| [[metrica_grupo.periodo_id → periodo]] | [[periodo]] | ↗ | sí |
| [[peso_factor.modelo_id → modelo_scoring]] | [[modelo_scoring]] | — | no |
| [[puntaje_reputacion.modelo_id → modelo_scoring]] | [[modelo_scoring]] | — | no |
| [[puntaje_reputacion.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[registro_sellado.bloque_id → bloque_transparencia]] | [[bloque_transparencia]] | — | no |
| [[regla_impacto_evento.modelo_id → modelo_scoring]] | [[modelo_scoring]] | — | no |
| [[resena_participante.autor_participante_id → participante]] | [[participante]] | ↗ | no |
| [[resena_participante.evaluado_usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[resena_participante.grupo_id → grupo]] | [[grupo]] | ↗ | no |
| [[resena_participante.moderada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[snapshot_reputacion.usuario_id → usuario]] | [[usuario]] | ↗ | no |

## 07 — Organizador y Automatización

> [[_Relaciones 07|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[apelacion_sancion_org.resuelta_por → usuario]] | [[usuario]] | ↗ | sí |
| [[apelacion_sancion_org.sancion_organizador_id → sancion_organizador]] | [[sancion_organizador]] | — | no |
| [[capacitacion_organizador.organizador_id → organizador]] | [[organizador]] | — | no |
| [[contrato_organizador.organizador_id → organizador]] | [[organizador]] | — | no |
| [[contrato_organizador.token_firma_id → token_verificacion]] | [[token_verificacion]] | ↗ | sí |
| [[ejecucion_tarea.tarea_id → tarea_automatizada]] | [[tarea_automatizada]] | — | no |
| [[evaluacion_desempeno.organizador_id → organizador]] | [[organizador]] | — | no |
| [[metrica_organizador.evaluacion_id → evaluacion_desempeno]] | [[evaluacion_desempeno]] | — | no |
| [[organizador.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[sancion_organizador.aplicada_por → usuario]] | [[usuario]] | ↗ | no |
| [[sancion_organizador.evaluacion_id → evaluacion_desempeno]] | [[evaluacion_desempeno]] | — | sí |
| [[sancion_organizador.organizador_id → organizador]] | [[organizador]] | — | no |
| [[solicitud_organizador.kyc_reforzado_id → verificacion_kyc]] | [[verificacion_kyc]] | ↗ | sí |
| [[solicitud_organizador.revisada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[solicitud_organizador.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[tarea_automatizada.grupo_id → grupo]] | [[grupo]] | ↗ | no |
| [[tarea_automatizada.regla_id → regla_automatizacion]] | [[regla_automatizacion]] | — | no |

## 08 — Garantía, Incumplimiento, Cobranza y Sanciones

> [[_Relaciones 08|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[abono_recuperacion.deuda_id → deuda_participante]] | [[deuda_participante]] | — | no |
| [[abono_recuperacion.entrega_id → entrega_fondo]] | [[entrega_fondo]] | ↗ | sí |
| [[abono_recuperacion.movimiento_fondo_id → movimiento_fondo]] | [[movimiento_fondo]] | — | sí |
| [[abono_recuperacion.pago_id → pago]] | [[pago]] | ↗ | sí |
| [[abono_recuperacion.registrado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[accion_cobranza.ejecutada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[accion_cobranza.gestion_id → gestion_cobranza]] | [[gestion_cobranza]] | — | no |
| [[accion_cobranza.notificacion_id → notificacion]] | [[notificacion]] | ↗ | sí |
| [[acuerdo_quita.acuerdo_grupo_id → acuerdo]] | [[acuerdo]] | ↗ | sí |
| [[acuerdo_quita.aprobado_por → usuario]] | [[usuario]] | ↗ | no |
| [[acuerdo_quita.registro_id → registro_incumplimiento]] | [[registro_incumplimiento]] | — | no |
| [[alerta_temprana.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[alerta_temprana.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[apelacion_sancion.apelante_id → usuario]] | [[usuario]] | ↗ | no |
| [[apelacion_sancion.resuelta_por → usuario]] | [[usuario]] | ↗ | sí |
| [[apelacion_sancion.sancion_id → sancion]] | [[sancion]] | — | no |
| [[aval_participante.avalista_usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[aval_participante.grupo_id → grupo]] | [[grupo]] | ↗ | no |
| [[aval_participante.participante_avalado_id → participante]] | [[participante]] | ↗ | no |
| [[aval_participante.token_aceptacion_id → token_verificacion]] | [[token_verificacion]] | ↗ | sí |
| [[candidato_reemplazo.reemplazo_id → reemplazo_participante]] | [[reemplazo_participante]] | — | no |
| [[candidato_reemplazo.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[castigo_deuda.aprobado_por → usuario]] | [[usuario]] | ↗ | no |
| [[castigo_deuda.asiento_contable_id → asiento_contable]] | [[asiento_contable]] | ↗ | sí |
| [[castigo_deuda.deuda_id → deuda_participante]] | [[deuda_participante]] | — | no |
| [[cobertura_incumplimiento.aprobada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[cobertura_incumplimiento.asiento_contable_id → asiento_contable]] | [[asiento_contable]] | ↗ | sí |
| [[cobertura_incumplimiento.fondo_id → fondo_garantia]] | [[fondo_garantia]] | — | no |
| [[cobertura_incumplimiento.movimiento_fondo_id → movimiento_fondo]] | [[movimiento_fondo]] | — | sí |
| [[cobertura_incumplimiento.obligacion_id → obligacion_aporte]] | [[obligacion_aporte]] | ↗ | no |
| [[cobertura_incumplimiento.periodo_id → periodo]] | [[periodo]] | ↗ | no |
| [[cobertura_incumplimiento.registro_id → registro_incumplimiento]] | [[registro_incumplimiento]] | — | no |
| [[descargo_participante.participante_id → participante]] | [[participante]] | ↗ | no |
| [[descargo_participante.registro_id → registro_incumplimiento]] | [[registro_incumplimiento]] | — | no |
| [[descargo_participante.resuelto_por → usuario]] | [[usuario]] | ↗ | sí |
| [[deuda_participante.cobertura_id → cobertura_incumplimiento]] | [[cobertura_incumplimiento]] | — | sí |
| [[deuda_participante.grupo_id → grupo]] | [[grupo]] | ↗ | no |
| [[deuda_participante.participante_id → participante]] | [[participante]] | ↗ | no |
| [[deuda_participante.registro_id → registro_incumplimiento]] | [[registro_incumplimiento]] | — | no |
| [[deuda_participante.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[devolucion_fondo.fondo_id → fondo_garantia]] | [[fondo_garantia]] | — | no |
| [[devolucion_fondo.participante_id → participante]] | [[participante]] | ↗ | no |
| [[disolucion_anticipada.acuerdo_grupo_id → acuerdo]] | [[acuerdo]] | ↗ | sí |
| [[disolucion_anticipada.grupo_id → grupo]] | [[grupo]] | ↗ | no |
| [[ejecucion_aval.aval_id → aval_participante]] | [[aval_participante]] | — | no |
| [[ejecucion_aval.deuda_id → deuda_participante]] | [[deuda_participante]] | — | no |
| [[ejecucion_aval.pago_id → pago]] | [[pago]] | ↗ | sí |
| [[ejecucion_aval.registro_id → registro_incumplimiento]] | [[registro_incumplimiento]] | — | no |
| [[evidencia_incumplimiento.aportada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[evidencia_incumplimiento.registro_id → registro_incumplimiento]] | [[registro_incumplimiento]] | — | no |
| [[fondo_garantia.cuenta_contable_id → cuenta_contable]] | [[cuenta_contable]] | ↗ | no |
| [[fondo_garantia.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[fondo_garantia.politica_cobertura_id → politica_cobertura]] | [[politica_cobertura]] | — | no |
| [[gestion_cobranza.estrategia_id → estrategia_cobranza]] | [[estrategia_cobranza]] | — | no |
| [[gestion_cobranza.gestor_asignado_id → usuario]] | [[usuario]] | ↗ | sí |
| [[gestion_cobranza.registro_id → registro_incumplimiento]] | [[registro_incumplimiento]] | — | no |
| [[historial_estado_incumplimiento.ejecutado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[historial_estado_incumplimiento.registro_id → registro_incumplimiento]] | [[registro_incumplimiento]] | — | no |
| [[historial_incumplimiento_usuario.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[liquidacion_participante.disolucion_id → disolucion_anticipada]] | [[disolucion_anticipada]] | — | no |
| [[liquidacion_participante.participante_id → participante]] | [[participante]] | ↗ | no |
| [[lista_restriccion_interna.registro_origen_id → registro_incumplimiento]] | [[registro_incumplimiento]] | — | sí |
| [[lista_restriccion_interna.retirado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[lista_restriccion_interna.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[matriz_sancion.politica_id → politica_sancion]] | [[politica_sancion]] | — | no |
| [[movimiento_fondo.asiento_contable_id → asiento_contable]] | [[asiento_contable]] | ↗ | sí |
| [[movimiento_fondo.fondo_id → fondo_garantia]] | [[fondo_garantia]] | — | no |
| [[movimiento_fondo.registrado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[plan_contingencia.acuerdo_grupo_id → acuerdo]] | [[acuerdo]] | ↗ | sí |
| [[plan_contingencia.grupo_id → grupo]] | [[grupo]] | ↗ | no |
| [[politica_cobertura.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[politica_sancion.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[promesa_pago.gestion_id → gestion_cobranza]] | [[gestion_cobranza]] | — | no |
| [[promesa_pago.registrada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[reemplazo_participante.acuerdo_grupo_id → acuerdo]] | [[acuerdo]] | ↗ | sí |
| [[reemplazo_participante.cupo_id → cupo]] | [[cupo]] | ↗ | no |
| [[reemplazo_participante.grupo_id → grupo]] | [[grupo]] | ↗ | no |
| [[reemplazo_participante.participante_entrante_id → participante]] | [[participante]] | ↗ | sí |
| [[reemplazo_participante.participante_saliente_id → participante]] | [[participante]] | ↗ | no |
| [[reemplazo_participante.registro_id → registro_incumplimiento]] | [[registro_incumplimiento]] | — | sí |
| [[registro_incumplimiento.cupo_id → cupo]] | [[cupo]] | ↗ | sí |
| [[registro_incumplimiento.entrega_afectada_id → entrega_fondo]] | [[entrega_fondo]] | ↗ | sí |
| [[registro_incumplimiento.grupo_id → grupo]] | [[grupo]] | ↗ | no |
| [[registro_incumplimiento.obligacion_id → obligacion_aporte]] | [[obligacion_aporte]] | ↗ | sí |
| [[registro_incumplimiento.participante_id → participante]] | [[participante]] | ↗ | no |
| [[registro_incumplimiento.periodo_id → periodo]] | [[periodo]] | ↗ | sí |
| [[registro_incumplimiento.reportado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[registro_incumplimiento.responsable_gestion → usuario]] | [[usuario]] | ↗ | sí |
| [[registro_incumplimiento.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[sancion.acuerdo_grupo_id → acuerdo]] | [[acuerdo]] | ↗ | sí |
| [[sancion.aplicada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[sancion.matriz_id → matriz_sancion]] | [[matriz_sancion]] | — | sí |
| [[sancion.participante_id → participante]] | [[participante]] | ↗ | sí |
| [[sancion.registro_id → registro_incumplimiento]] | [[registro_incumplimiento]] | — | no |
| [[sancion.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[score_riesgo_incumplimiento.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[score_riesgo_incumplimiento.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[subrogacion.cobertura_id → cobertura_incumplimiento]] | [[cobertura_incumplimiento]] | — | no |
| [[subrogacion.deuda_id → deuda_participante]] | [[deuda_participante]] | — | no |

## 09 — Auditoría, Reportes y Cumplimiento

> [[_Relaciones 09|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[alerta_cumplimiento.analista_id → usuario]] | [[usuario]] | ↗ | sí |
| [[alerta_cumplimiento.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[alerta_cumplimiento.regla_id → regla_cumplimiento]] | [[regla_cumplimiento]] | — | no |
| [[alerta_cumplimiento.reporte_sospechoso_id → reporte_operacion_sospechosa]] | [[reporte_operacion_sospechosa]] | — | sí |
| [[alerta_cumplimiento.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[bitacora_evento.actor_usuario_id → usuario]] | [[usuario]] | ↗ | sí |
| [[bitacora_evento.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[bitacora_evento.suplantando_a_usuario_id → usuario]] | [[usuario]] | ↗ | sí |
| [[coincidencia_lista.lista_id → lista_restrictiva_externa]] | [[lista_restrictiva_externa]] | — | no |
| [[coincidencia_lista.revisada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[coincidencia_lista.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[ejecucion_reporte.definicion_id → definicion_reporte]] | [[definicion_reporte]] | — | no |
| [[ejecucion_reporte.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[ejecucion_reporte.solicitado_por → usuario]] | [[usuario]] | ↗ | no |
| [[exportacion_reporte.ejecucion_id → ejecucion_reporte]] | [[ejecucion_reporte]] | — | no |
| [[indicador_kpi.definicion_indicador_id → definicion_indicador]] | [[definicion_indicador]] | — | no |
| [[proceso_anonimizacion.solicitud_id → solicitud_datos_personales]] | [[solicitud_datos_personales]] | — | sí |
| [[proceso_anonimizacion.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[programacion_reporte.definicion_id → definicion_reporte]] | [[definicion_reporte]] | — | no |
| [[registro_acceso_datos.usuario_afectado_id → usuario]] | [[usuario]] | ↗ | no |
| [[registro_acceso_datos.usuario_consultor_id → usuario]] | [[usuario]] | ↗ | no |
| [[reporte_operacion_sospechosa.aprobado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[reporte_operacion_sospechosa.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[solicitud_datos_personales.atendida_por → usuario]] | [[usuario]] | ↗ | sí |
| [[solicitud_datos_personales.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[ticket_soporte.asignado_a → usuario]] | [[usuario]] | ↗ | sí |
| [[ticket_soporte.usuario_id → usuario]] | [[usuario]] | ↗ | no |

## 10 — Billetera, Custodia y Dinero Electrónico

> [[_Relaciones 10|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[bloqueo_saldo.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[bloqueo_saldo.levantada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[bloqueo_saldo.retencion_id → retencion_saldo]] | [[retencion_saldo]] | — | sí |
| [[certificado_saldo.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[certificado_saldo.solicitado_por → usuario]] | [[usuario]] | ↗ | no |
| [[conciliacion_custodia.cierre_diario_id → cierre_diario]] | [[cierre_diario]] | ↗ | sí |
| [[conciliacion_custodia.cuenta_custodia_id → cuenta_custodia]] | [[cuenta_custodia]] | — | no |
| [[conciliacion_custodia.ejecutada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[consumo_limite.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[consumo_limite.limite_id → limite_operativo_billetera]] | [[limite_operativo_billetera]] | — | no |
| [[cuenta_billetera.cuenta_contable_id → cuenta_contable]] | [[cuenta_contable]] | ↗ | sí |
| [[cuenta_billetera.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[cuenta_billetera.politica_billetera_id → politica_billetera]] | [[politica_billetera]] | — | sí |
| [[cuenta_billetera.usuario_id → usuario]] | [[usuario]] | ↗ | sí |
| [[descuadre_custodia.conciliacion_custodia_id → conciliacion_custodia]] | [[conciliacion_custodia]] | — | no |
| [[descuadre_custodia.incidente_operativo_id → incidente_operativo]] | [[incidente_operativo]] | ↗ | sí |
| [[descuadre_custodia.resuelto_por → usuario]] | [[usuario]] | ↗ | sí |
| [[estado_cuenta_billetera.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[evaluacion_antifraude.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[evaluacion_antifraude.revisada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[evaluacion_antifraude.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | — | sí |
| [[instrumento_fondeo.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[movimiento_billetera.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[movimiento_billetera.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | — | no |
| [[movimiento_custodia.cuenta_custodia_id → cuenta_custodia]] | [[cuenta_custodia]] | — | no |
| [[movimiento_custodia.movimiento_bancario_id → movimiento_bancario]] | [[movimiento_bancario]] | ↗ | sí |
| [[orden_recarga.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[orden_recarga.instrumento_fondeo_id → instrumento_fondeo]] | [[instrumento_fondeo]] | — | sí |
| [[orden_recarga.pago_id → pago]] | [[pago]] | ↗ | sí |
| [[orden_recarga.proveedor_id → proveedor_pago]] | [[proveedor_pago]] | ↗ | sí |
| [[orden_recarga.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | — | sí |
| [[orden_retiro.aprobada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[orden_retiro.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[orden_retiro.instrumento_destino_id → instrumento_fondeo]] | [[instrumento_fondeo]] | — | no |
| [[orden_retiro.proveedor_id → proveedor_pago]] | [[proveedor_pago]] | ↗ | sí |
| [[orden_retiro.retencion_id → retencion_saldo]] | [[retencion_saldo]] | — | sí |
| [[orden_retiro.solicitada_por → usuario]] | [[usuario]] | ↗ | no |
| [[orden_retiro.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | — | sí |
| [[politica_billetera.aprobada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[regla_antifraude.aprobada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[respuesta_idempotente.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[retencion_saldo.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[retencion_saldo.liberada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[retencion_saldo.transaccion_origen_id → transaccion_billetera]] | [[transaccion_billetera]] | — | sí |
| [[reverso_transaccion.autorizada_por → usuario]] | [[usuario]] | ↗ | no |
| [[reverso_transaccion.transaccion_original_id → transaccion_billetera]] | [[transaccion_billetera]] | — | no |
| [[reverso_transaccion.transaccion_reverso_id → transaccion_billetera]] | [[transaccion_billetera]] | — | sí |
| [[saldo_diario_billetera.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[solicitud_cierre_billetera.aprobada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[solicitud_cierre_billetera.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[solicitud_cierre_billetera.orden_retiro_id → orden_retiro]] | [[orden_retiro]] | — | sí |
| [[transaccion_billetera.asiento_contable_id → asiento_contable]] | [[asiento_contable]] | ↗ | sí |
| [[transaccion_billetera.dispositivo_id → dispositivo]] | [[dispositivo]] | ↗ | sí |
| [[transaccion_billetera.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[transaccion_billetera.iniciada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[transaccion_billetera.sesion_id → sesion]] | [[sesion]] | ↗ | sí |
| [[transferencia_p2p.cuenta_billetera_destino_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[transferencia_p2p.cuenta_billetera_origen_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[transferencia_p2p.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[transferencia_p2p.obligacion_id → obligacion_aporte]] | [[obligacion_aporte]] | ↗ | sí |
| [[transferencia_p2p.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | — | no |

## 11 — Tarifas, Comisiones, Impuestos y Facturación

> [[_Relaciones 11|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[aplicacion_promocion.campana_id → campana_promocional]] | [[campana_promocional]] | — | no |
| [[aplicacion_promocion.devengo_id → devengo_comision]] | [[devengo_comision]] | — | no |
| [[asignacion_tarifario.autorizado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[asignacion_tarifario.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[asignacion_tarifario.segmento_id → segmento_comercial]] | [[segmento_comercial]] | — | sí |
| [[asignacion_tarifario.tarifario_id → tarifario]] | [[tarifario]] | — | no |
| [[asignacion_tarifario.usuario_id → usuario]] | [[usuario]] | ↗ | sí |
| [[calculo_impuesto.devengo_id → devengo_comision]] | [[devengo_comision]] | — | no |
| [[calculo_impuesto.impuesto_id → impuesto]] | [[impuesto]] | — | no |
| [[cambio_tarifario.aprobado_por → usuario]] | [[usuario]] | ↗ | no |
| [[cambio_tarifario.tarifario_anterior_id → tarifario]] | [[tarifario]] | — | no |
| [[cambio_tarifario.tarifario_nuevo_id → tarifario]] | [[tarifario]] | — | no |
| [[campana_promocional.aprobada_por → usuario]] | [[usuario]] | ↗ | no |
| [[cargo_comision.deduccion_entrega_id → deduccion_entrega]] | [[deduccion_entrega]] | ↗ | sí |
| [[cargo_comision.devengo_id → devengo_comision]] | [[devengo_comision]] | — | no |
| [[cargo_comision.obligacion_id → obligacion_aporte]] | [[obligacion_aporte]] | ↗ | sí |
| [[cargo_comision.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | ↗ | sí |
| [[concepto_tarifa.cuenta_ingreso_id → cuenta_contable]] | [[cuenta_contable]] | ↗ | sí |
| [[concepto_tarifa.hecho_generador_id → catalogo_hecho_generador]] | [[catalogo_hecho_generador]] | — | no |
| [[concepto_tarifa.politica_redondeo_id → politica_redondeo]] | [[politica_redondeo]] | — | sí |
| [[concepto_tarifa.tarifario_id → tarifario]] | [[tarifario]] | — | no |
| [[costo_proveedor_operacion.liquidacion_ingresos_id → liquidacion_ingresos]] | [[liquidacion_ingresos]] | — | sí |
| [[costo_proveedor_operacion.proveedor_id → proveedor_pago]] | [[proveedor_pago]] | ↗ | no |
| [[costo_proveedor_operacion.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | ↗ | sí |
| [[cotizacion_comision.concepto_tarifa_id → concepto_tarifa]] | [[concepto_tarifa]] | — | no |
| [[cotizacion_comision.tarifario_id → tarifario]] | [[tarifario]] | — | no |
| [[cuenta_por_cobrar_comision.devengo_id → devengo_comision]] | [[devengo_comision]] | — | no |
| [[cuenta_por_cobrar_comision.gestion_cobranza_id → gestion_cobranza]] | [[gestion_cobranza]] | ↗ | sí |
| [[cuenta_por_cobrar_comision.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[datos_facturacion.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[devengo_comision.asiento_contable_id → asiento_contable]] | [[asiento_contable]] | ↗ | sí |
| [[devengo_comision.concepto_tarifa_id → concepto_tarifa]] | [[concepto_tarifa]] | — | no |
| [[devengo_comision.cotizacion_id → cotizacion_comision]] | [[cotizacion_comision]] | — | sí |
| [[devengo_comision.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[devengo_comision.participante_id → participante]] | [[participante]] | ↗ | sí |
| [[devengo_comision.tarifario_id → tarifario]] | [[tarifario]] | — | no |
| [[devengo_comision.usuario_obligado_id → usuario]] | [[usuario]] | ↗ | no |
| [[devolucion_comision.autorizada_por → usuario]] | [[usuario]] | ↗ | no |
| [[devolucion_comision.devengo_id → devengo_comision]] | [[devengo_comision]] | — | no |
| [[devolucion_comision.reclamo_id → reclamo_cliente]] | [[reclamo_cliente]] | ↗ | sí |
| [[devolucion_comision.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | ↗ | sí |
| [[evento_significativo_sin.registrado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[exencion_comision.autorizada_por → usuario]] | [[usuario]] | ↗ | no |
| [[exencion_comision.concepto_tarifa_id → concepto_tarifa]] | [[concepto_tarifa]] | — | sí |
| [[exencion_comision.grupo_id → grupo]] | [[grupo]] | ↗ | sí |
| [[exencion_comision.segmento_id → segmento_comercial]] | [[segmento_comercial]] | — | sí |
| [[exencion_comision.usuario_id → usuario]] | [[usuario]] | ↗ | sí |
| [[factura_electronica.datos_facturacion_id → datos_facturacion]] | [[datos_facturacion]] | — | no |
| [[factura_electronica.devengo_id → devengo_comision]] | [[devengo_comision]] | — | sí |
| [[factura_electronica.evento_significativo_id → evento_significativo_sin]] | [[evento_significativo_sin]] | — | sí |
| [[factura_electronica.lote_envio_sin_id → lote_envio_sin]] | [[lote_envio_sin]] | — | sí |
| [[factura_electronica.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[impuesto.cuenta_contable_id → cuenta_contable]] | [[cuenta_contable]] | ↗ | sí |
| [[liquidacion_ingresos.asiento_contable_id → asiento_contable]] | [[asiento_contable]] | ↗ | sí |
| [[liquidacion_ingresos.cerrada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[nota_credito_debito.devolucion_comision_id → devolucion_comision]] | [[devolucion_comision]] | — | sí |
| [[nota_credito_debito.factura_id → factura_electronica]] | [[factura_electronica]] | — | no |
| [[regla_tarifa.concepto_tarifa_id → concepto_tarifa]] | [[concepto_tarifa]] | — | no |
| [[simulacion_tarifa.ejecutada_por → usuario]] | [[usuario]] | ↗ | no |
| [[simulacion_tarifa.tarifario_id → tarifario]] | [[tarifario]] | — | no |
| [[tarifa_congelada_grupo.acuerdo_id → acuerdo]] | [[acuerdo]] | ↗ | sí |
| [[tarifa_congelada_grupo.grupo_id → grupo]] | [[grupo]] | ↗ | no |
| [[tarifa_congelada_grupo.tarifario_id → tarifario]] | [[tarifario]] | — | no |
| [[tarifario.aprobado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[tarifario.tarifario_anterior_id → tarifario]] | [[tarifario]] | — | sí |

## 12 — Cumplimiento Regulatorio y Consumidor Financiero

> [[_Relaciones 12|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[aceptacion_contrato.contrato_adhesion_id → contrato_adhesion]] | [[contrato_adhesion]] | — | no |
| [[aceptacion_contrato.dispositivo_id → dispositivo]] | [[dispositivo]] | ↗ | sí |
| [[aceptacion_contrato.token_firma_id → token_verificacion]] | [[token_verificacion]] | ↗ | sí |
| [[aceptacion_contrato.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[acta_comite.comite_gobierno_id → comite_gobierno]] | [[comite_gobierno]] | — | no |
| [[acta_comite.elaborada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[activo_informacion.contrato_tercero_id → contrato_tercero]] | [[contrato_tercero]] | — | sí |
| [[activo_informacion.custodio_id → usuario]] | [[usuario]] | ↗ | sí |
| [[activo_informacion.propietario_id → usuario]] | [[usuario]] | ↗ | sí |
| [[alerta_monitoreo_lft.asignada_a → usuario]] | [[usuario]] | ↗ | sí |
| [[alerta_monitoreo_lft.caso_id → caso_investigacion_lft]] | [[caso_investigacion_lft]] | — | sí |
| [[alerta_monitoreo_lft.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | ↗ | sí |
| [[alerta_monitoreo_lft.regla_monitoreo_id → regla_monitoreo_lft]] | [[regla_monitoreo_lft]] | — | no |
| [[alerta_monitoreo_lft.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | ↗ | sí |
| [[alerta_monitoreo_lft.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[beneficiario_final.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[calificacion_riesgo_cliente.calificado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[calificacion_riesgo_cliente.matriz_riesgo_id → matriz_riesgo_lft]] | [[matriz_riesgo_lft]] | — | sí |
| [[calificacion_riesgo_cliente.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[capacitacion_cumplimiento.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[caso_investigacion_lft.analista_id → usuario]] | [[usuario]] | ↗ | no |
| [[caso_investigacion_lft.reporte_operacion_sospechosa_id → reporte_operacion_sospechosa]] | [[reporte_operacion_sospechosa]] | ↗ | sí |
| [[caso_investigacion_lft.revisado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[caso_investigacion_lft.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[contrato_adhesion.aprobado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[contrato_tercero.responsable_id → usuario]] | [[usuario]] | ↗ | sí |
| [[control_interno.responsable_id → usuario]] | [[usuario]] | ↗ | sí |
| [[debida_diligencia.aprobada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[debida_diligencia.calificacion_riesgo_id → calificacion_riesgo_cliente]] | [[calificacion_riesgo_cliente]] | — | sí |
| [[debida_diligencia.segunda_revision_por → usuario]] | [[usuario]] | ↗ | sí |
| [[debida_diligencia.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[debida_diligencia.verificacion_kyc_id → verificacion_kyc]] | [[verificacion_kyc]] | ↗ | sí |
| [[declaracion_origen_fondos.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | ↗ | sí |
| [[declaracion_origen_fondos.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[declaracion_origen_fondos.verificada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[declaracion_pep.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[declaracion_pep.verificada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[designacion_regulatoria.acta_comite_id → acta_comite]] | [[acta_comite]] | — | sí |
| [[designacion_regulatoria.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[desvio_perfil.alerta_monitoreo_id → alerta_monitoreo_lft]] | [[alerta_monitoreo_lft]] | — | sí |
| [[desvio_perfil.perfil_transaccional_id → perfil_transaccional]] | [[perfil_transaccional]] | — | no |
| [[desvio_perfil.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[documento_publicado.publicado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[entorno_prueba_regulado.licencia_regulatoria_id → licencia_regulatoria]] | [[licencia_regulatoria]] | — | no |
| [[envio_regulatorio.enviado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[envio_regulatorio.reporte_regulatorio_id → reporte_regulatorio]] | [[reporte_regulatorio]] | — | no |
| [[evaluacion_riesgo_producto.aprobada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[evaluacion_tercero.contrato_tercero_id → contrato_tercero]] | [[contrato_tercero]] | — | no |
| [[evaluacion_tercero.evaluado_por → usuario]] | [[usuario]] | ↗ | no |
| [[evento_riesgo_operativo.incidente_operativo_id → incidente_operativo]] | [[incidente_operativo]] | ↗ | sí |
| [[evento_riesgo_operativo.registrado_por → usuario]] | [[usuario]] | ↗ | no |
| [[expediente_cliente.responsable_id → usuario]] | [[usuario]] | ↗ | sí |
| [[expediente_cliente.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[factor_riesgo_evaluado.matriz_riesgo_id → matriz_riesgo_lft]] | [[matriz_riesgo_lft]] | — | no |
| [[factor_riesgo_evaluado.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[hallazgo_auditoria.responsable_id → usuario]] | [[usuario]] | ↗ | sí |
| [[incidente_seguridad.activo_informacion_id → activo_informacion]] | [[activo_informacion]] | — | sí |
| [[incidente_seguridad.evento_riesgo_id → evento_riesgo_operativo]] | [[evento_riesgo_operativo]] | — | sí |
| [[incidente_seguridad.incidente_operativo_id → incidente_operativo]] | [[incidente_operativo]] | ↗ | sí |
| [[incidente_seguridad.responsable_id → usuario]] | [[usuario]] | ↗ | sí |
| [[instancia_reclamo.reclamo_id → reclamo_cliente]] | [[reclamo_cliente]] | — | no |
| [[licencia_regulatoria.responsable_id → usuario]] | [[usuario]] | ↗ | sí |
| [[matriz_riesgo_lft.aprobada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[observacion_regulatoria.envio_regulatorio_id → envio_regulatorio]] | [[envio_regulatorio]] | — | sí |
| [[observacion_regulatoria.responsable_id → usuario]] | [[usuario]] | ↗ | sí |
| [[oficial_cumplimiento.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[perfil_transaccional.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[plan_accion_riesgo.evento_riesgo_id → evento_riesgo_operativo]] | [[evento_riesgo_operativo]] | — | sí |
| [[plan_accion_riesgo.hallazgo_id → hallazgo_auditoria]] | [[hallazgo_auditoria]] | — | sí |
| [[plan_accion_riesgo.responsable_id → usuario]] | [[usuario]] | ↗ | no |
| [[plan_continuidad.politica_interna_id → politica_interna]] | [[politica_interna]] | — | sí |
| [[plan_continuidad.responsable_id → usuario]] | [[usuario]] | ↗ | sí |
| [[politica_interna.acta_comite_id → acta_comite]] | [[acta_comite]] | — | sí |
| [[politica_interna.responsable_id → usuario]] | [[usuario]] | ↗ | sí |
| [[prueba_continuidad.acta_comite_id → acta_comite]] | [[acta_comite]] | — | sí |
| [[prueba_continuidad.ejecutada_por → usuario]] | [[usuario]] | ↗ | no |
| [[prueba_continuidad.plan_continuidad_id → plan_continuidad]] | [[plan_continuidad]] | — | no |
| [[prueba_control.control_id → control_interno]] | [[control_interno]] | — | no |
| [[prueba_control.ejecutada_por → usuario]] | [[usuario]] | ↗ | no |
| [[punto_reclamo.responsable_id → usuario]] | [[usuario]] | ↗ | sí |
| [[reclamo_cliente.devolucion_comision_id → devolucion_comision]] | [[devolucion_comision]] | ↗ | sí |
| [[reclamo_cliente.punto_reclamo_id → punto_reclamo]] | [[punto_reclamo]] | — | no |
| [[reclamo_cliente.responsable_id → usuario]] | [[usuario]] | ↗ | sí |
| [[reclamo_cliente.ticket_soporte_id → ticket_soporte]] | [[ticket_soporte]] | ↗ | sí |
| [[reclamo_cliente.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[registro_operacion_relevante.declaracion_origen_fondos_id → declaracion_origen_fondos]] | [[declaracion_origen_fondos]] | — | sí |
| [[registro_operacion_relevante.operacion_inicio_ventana_id → registro_operacion_relevante]] | [[registro_operacion_relevante]] | — | sí |
| [[registro_operacion_relevante.reporte_regulatorio_id → reporte_regulatorio]] | [[reporte_regulatorio]] | — | sí |
| [[registro_operacion_relevante.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | ↗ | no |
| [[registro_operacion_relevante.umbral_reporte_id → umbral_reporte_uif]] | [[umbral_reporte_uif]] | — | no |
| [[registro_operacion_relevante.usuario_id → usuario]] | [[usuario]] | ↗ | no |
| [[regla_monitoreo_lft.aprobada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[reporte_regulatorio.aprobado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[reporte_regulatorio.catalogo_reporte_id → catalogo_reporte_regulatorio]] | [[catalogo_reporte_regulatorio]] | — | no |
| [[reporte_regulatorio.generado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[reporte_regulatorio.revisado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[requerimiento_autoridad.bloqueo_saldo_id → bloqueo_saldo]] | [[bloqueo_saldo]] | ↗ | sí |
| [[requerimiento_autoridad.respondido_por → usuario]] | [[usuario]] | ↗ | sí |
| [[requerimiento_autoridad.usuario_afectado_id → usuario]] | [[usuario]] | ↗ | sí |
| [[revision_periodica_kyc.calificacion_riesgo_id → calificacion_riesgo_cliente]] | [[calificacion_riesgo_cliente]] | — | sí |
| [[revision_periodica_kyc.ejecutada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[revision_periodica_kyc.usuario_id → usuario]] | [[usuario]] | ↗ | no |

## 13 — Contabilidad Financiera y ERP

> [[_Relaciones 13|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[activo_fijo.categoria_activo_fijo_id → categoria_activo_fijo]] | [[categoria_activo_fijo]] | — | no |
| [[activo_fijo.centro_costo_id → centro_costo]] | [[centro_costo]] | — | sí |
| [[activo_fijo.factura_proveedor_id → factura_proveedor]] | [[factura_proveedor]] | — | sí |
| [[asiento_plantilla.creada_por → usuario]] | [[usuario]] | ↗ | no |
| [[categoria_activo_fijo.cuenta_activo_id → cuenta_contable]] | [[cuenta_contable]] | ↗ | no |
| [[categoria_activo_fijo.cuenta_depreciacion_id → cuenta_contable]] | [[cuenta_contable]] | ↗ | no |
| [[categoria_activo_fijo.cuenta_gasto_depreciacion_id → cuenta_contable]] | [[cuenta_contable]] | ↗ | no |
| [[cierre_periodo_contable.cerrado_por → usuario]] | [[usuario]] | ↗ | no |
| [[cierre_periodo_contable.periodo_contable_id → periodo_contable]] | [[periodo_contable]] | — | no |
| [[cobro_cuenta_por_cobrar.asiento_contable_id → asiento_contable]] | [[asiento_contable]] | ↗ | sí |
| [[cobro_cuenta_por_cobrar.cuenta_por_cobrar_id → cuenta_por_cobrar]] | [[cuenta_por_cobrar]] | — | no |
| [[cuenta_por_cobrar.tercero_comercial_id → tercero_comercial]] | [[tercero_comercial]] | — | sí |
| [[depreciacion_activo.activo_fijo_id → activo_fijo]] | [[activo_fijo]] | — | no |
| [[depreciacion_activo.asiento_contable_id → asiento_contable]] | [[asiento_contable]] | ↗ | sí |
| [[depreciacion_activo.periodo_contable_id → periodo_contable]] | [[periodo_contable]] | — | no |
| [[ejercicio_fiscal.cerrado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[estado_financiero_generado.generado_por → usuario]] | [[usuario]] | ↗ | no |
| [[estado_financiero_generado.periodo_contable_id → periodo_contable]] | [[periodo_contable]] | — | no |
| [[factura_proveedor.aprobada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[factura_proveedor.asiento_contable_id → asiento_contable]] | [[asiento_contable]] | ↗ | sí |
| [[factura_proveedor.centro_costo_id → centro_costo]] | [[centro_costo]] | — | sí |
| [[factura_proveedor.orden_compra_id → orden_compra]] | [[orden_compra]] | — | sí |
| [[factura_proveedor.tercero_comercial_id → tercero_comercial]] | [[tercero_comercial]] | — | no |
| [[linea_plantilla_asiento.cuenta_contable_id → cuenta_contable]] | [[cuenta_contable]] | ↗ | no |
| [[linea_plantilla_asiento.plantilla_id → asiento_plantilla]] | [[asiento_plantilla]] | — | no |
| [[orden_compra.aprobada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[orden_compra.centro_costo_id → centro_costo]] | [[centro_costo]] | — | sí |
| [[orden_compra.tercero_comercial_id → tercero_comercial]] | [[tercero_comercial]] | — | no |
| [[pago_a_proveedor.asiento_contable_id → asiento_contable]] | [[asiento_contable]] | ↗ | sí |
| [[pago_a_proveedor.autorizado_por → usuario]] | [[usuario]] | ↗ | no |
| [[pago_a_proveedor.factura_proveedor_id → factura_proveedor]] | [[factura_proveedor]] | — | no |
| [[partida_presupuestaria.cuenta_contable_id → cuenta_contable]] | [[cuenta_contable]] | ↗ | no |
| [[partida_presupuestaria.periodo_contable_id → periodo_contable]] | [[periodo_contable]] | — | no |
| [[partida_presupuestaria.presupuesto_id → presupuesto]] | [[presupuesto]] | — | no |
| [[periodo_contable.ejercicio_fiscal_id → ejercicio_fiscal]] | [[ejercicio_fiscal]] | — | no |
| [[presupuesto.aprobado_por → usuario]] | [[usuario]] | ↗ | sí |
| [[presupuesto.centro_costo_id → centro_costo]] | [[centro_costo]] | — | no |
| [[presupuesto.ejercicio_fiscal_id → ejercicio_fiscal]] | [[ejercicio_fiscal]] | — | no |
| [[tercero_comercial.cuenta_contable_id → cuenta_contable]] | [[cuenta_contable]] | ↗ | sí |

## 14 — Publicidad y Campañas

> [[_Relaciones 14|índice del módulo]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[anunciante.organizador_id → organizador]] | [[organizador]] | ↗ | sí |
| [[anunciante.socio_comercial_id → socio_comercial]] | [[socio_comercial]] | — | sí |
| [[anuncio.conjunto_anuncios_id → conjunto_anuncios]] | [[conjunto_anuncios]] | — | no |
| [[anuncio.pieza_creativa_id → pieza_creativa]] | [[pieza_creativa]] | — | no |
| [[campana_publicitaria.aprobada_por → usuario]] | [[usuario]] | ↗ | sí |
| [[campana_publicitaria.cuenta_publicitaria_id → cuenta_publicitaria]] | [[cuenta_publicitaria]] | — | no |
| [[clic_anuncio.impresion_id → impresion_anuncio]] | [[impresion_anuncio]] | — | no |
| [[clic_anuncio.usuario_id → usuario]] | [[usuario]] | ↗ | sí |
| [[conjunto_anuncios.campana_publicitaria_id → campana_publicitaria]] | [[campana_publicitaria]] | — | no |
| [[conjunto_anuncios.espacio_publicitario_id → espacio_publicitario]] | [[espacio_publicitario]] | — | no |
| [[conjunto_anuncios.segmento_audiencia_id → segmento_audiencia]] | [[segmento_audiencia]] | — | no |
| [[conversion_anuncio.clic_id → clic_anuncio]] | [[clic_anuncio]] | — | sí |
| [[conversion_anuncio.impresion_id → impresion_anuncio]] | [[impresion_anuncio]] | — | sí |
| [[cuenta_publicitaria.anunciante_id → anunciante]] | [[anunciante]] | — | no |
| [[factura_publicidad.cuenta_por_cobrar_id → cuenta_por_cobrar]] | [[cuenta_por_cobrar]] | ↗ | sí |
| [[factura_publicidad.cuenta_publicitaria_id → cuenta_publicitaria]] | [[cuenta_publicitaria]] | — | no |
| [[factura_publicidad.factura_electronica_id → factura_electronica]] | [[factura_electronica]] | ↗ | sí |
| [[impresion_anuncio.anuncio_id → anuncio]] | [[anuncio]] | — | no |
| [[impresion_anuncio.usuario_id → usuario]] | [[usuario]] | ↗ | sí |
| [[pieza_creativa.anunciante_id → anunciante]] | [[anunciante]] | — | no |
| [[revision_creativa.pieza_creativa_id → pieza_creativa]] | [[pieza_creativa]] | — | no |
| [[revision_creativa.revisada_por → usuario]] | [[usuario]] | ↗ | no |
| [[segmento_audiencia.creado_por → usuario]] | [[usuario]] | ↗ | no |
| [[socio_comercial.verificado_por → usuario]] | [[usuario]] | ↗ | sí |

