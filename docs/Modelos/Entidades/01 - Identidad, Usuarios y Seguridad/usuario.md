---
tags:
  - entidad
  - modulo/01-identidad-usuarios-y-seguridad
tabla: usuario
clase: Usuario
modulo: "01 — Identidad, Usuarios y Seguridad"
estereotipo: Raíz de agregado
clave_primaria: [id]
columnas: 18
fk_salientes: 0
fk_entrantes: 209
append_only: false
---

# `usuario`

> Módulo [[01_identidad_usuarios|01 — Identidad, Usuarios y Seguridad]] · clase `Usuario` · Raíz de agregado

## Columnas

| Columna | Tipo | Clave | Nulo | Anotaciones |
| --- | --- | --- | :-: | --- |
| `id` | UUID | PK | no | PK |
| `codigo_publico` | VARCHAR(12) | UQ | no | UQ |
| `nombres` | VARCHAR(80) | — | no | — |
| `apellidos` | VARCHAR(80) | — | no | — |
| `telefono_e164` | VARCHAR(20) | UQ IDX | no | UQ, IDX |
| `correo` | VARCHAR(150) | UQ | sí | UQ, NULL |
| `fecha_nacimiento` | DATE | — | no | — |
| `estado` | VARCHAR(25) | — | no | CK |
| `nivel_kyc` | VARCHAR(15) | — | no | CK |
| `idioma` | VARCHAR(10) | — | no | — |
| `zona_horaria` | VARCHAR(40) | — | no | — |
| `url_avatar` | VARCHAR(255) | — | sí | NULL |
| `telefono_verificado_en` | TIMESTAMPTZ | — | sí | NULL |
| `correo_verificado_en` | TIMESTAMPTZ | — | sí | NULL |
| `ultimo_acceso_en` | TIMESTAMPTZ | — | sí | NULL |
| `fecha_registro` | TIMESTAMPTZ | — | no | — |
| `eliminado_en` | TIMESTAMPTZ | — | sí | NULL |
| `version` | INTEGER | — | no | — |

## Referenciada por

| Entidad | Columna | Módulo | Relación |
| --- | --- | :-: | --- |
| [[abono_recuperacion]] | `registrado_por` | ↗ 08 | [[abono_recuperacion.registrado_por → usuario]] |
| [[accion_cobranza]] | `ejecutada_por` | ↗ 08 | [[accion_cobranza.ejecutada_por → usuario]] |
| [[aceptacion_contrato]] | `usuario_id` | ↗ 12 | [[aceptacion_contrato.usuario_id → usuario]] |
| [[acta_comite]] | `elaborada_por` | ↗ 12 | [[acta_comite.elaborada_por → usuario]] |
| [[activo_informacion]] | `custodio_id` | ↗ 12 | [[activo_informacion.custodio_id → usuario]] |
| [[activo_informacion]] | `propietario_id` | ↗ 12 | [[activo_informacion.propietario_id → usuario]] |
| [[acuerdo]] | `propuesto_por` | ↗ 02 | [[acuerdo.propuesto_por → usuario]] |
| [[acuerdo_quita]] | `aprobado_por` | ↗ 08 | [[acuerdo_quita.aprobado_por → usuario]] |
| [[alerta_cumplimiento]] | `analista_id` | ↗ 09 | [[alerta_cumplimiento.analista_id → usuario]] |
| [[alerta_cumplimiento]] | `usuario_id` | ↗ 09 | [[alerta_cumplimiento.usuario_id → usuario]] |
| [[alerta_monitoreo_lft]] | `asignada_a` | ↗ 12 | [[alerta_monitoreo_lft.asignada_a → usuario]] |
| [[alerta_monitoreo_lft]] | `usuario_id` | ↗ 12 | [[alerta_monitoreo_lft.usuario_id → usuario]] |
| [[alerta_temprana]] | `usuario_id` | ↗ 08 | [[alerta_temprana.usuario_id → usuario]] |
| [[apelacion_sancion]] | `apelante_id` | ↗ 08 | [[apelacion_sancion.apelante_id → usuario]] |
| [[apelacion_sancion]] | `resuelta_por` | ↗ 08 | [[apelacion_sancion.resuelta_por → usuario]] |
| [[apelacion_sancion_org]] | `resuelta_por` | ↗ 07 | [[apelacion_sancion_org.resuelta_por → usuario]] |
| [[asiento_contable]] | `registrado_por` | ↗ 03 | [[asiento_contable.registrado_por → usuario]] |
| [[asiento_plantilla]] | `creada_por` | ↗ 13 | [[asiento_plantilla.creada_por → usuario]] |
| [[asignacion_rol]] | `otorgada_por` | 01 | [[asignacion_rol.otorgada_por → usuario]] |
| [[asignacion_rol]] | `usuario_id` | 01 | [[asignacion_rol.usuario_id → usuario]] |
| [[asignacion_tarifario]] | `autorizado_por` | ↗ 11 | [[asignacion_tarifario.autorizado_por → usuario]] |
| [[asignacion_tarifario]] | `usuario_id` | ↗ 11 | [[asignacion_tarifario.usuario_id → usuario]] |
| [[aval_participante]] | `avalista_usuario_id` | ↗ 08 | [[aval_participante.avalista_usuario_id → usuario]] |
| [[bandeja_entrada]] | `usuario_id` | ↗ 05 | [[bandeja_entrada.usuario_id → usuario]] |
| [[beneficiario_final]] | `usuario_id` | ↗ 12 | [[beneficiario_final.usuario_id → usuario]] |
| [[bitacora_evento]] | `actor_usuario_id` | ↗ 09 | [[bitacora_evento.actor_usuario_id → usuario]] |
| [[bitacora_evento]] | `suplantando_a_usuario_id` | ↗ 09 | [[bitacora_evento.suplantando_a_usuario_id → usuario]] |
| [[bloqueo_cuenta]] | `liberada_por` | 01 | [[bloqueo_cuenta.liberada_por → usuario]] |
| [[bloqueo_cuenta]] | `usuario_id` | 01 | [[bloqueo_cuenta.usuario_id → usuario]] |
| [[bloqueo_saldo]] | `levantada_por` | ↗ 10 | [[bloqueo_saldo.levantada_por → usuario]] |
| [[calificacion_riesgo_cliente]] | `calificado_por` | ↗ 12 | [[calificacion_riesgo_cliente.calificado_por → usuario]] |
| [[calificacion_riesgo_cliente]] | `usuario_id` | ↗ 12 | [[calificacion_riesgo_cliente.usuario_id → usuario]] |
| [[cambio_tarifario]] | `aprobado_por` | ↗ 11 | [[cambio_tarifario.aprobado_por → usuario]] |
| [[campana_promocional]] | `aprobada_por` | ↗ 11 | [[campana_promocional.aprobada_por → usuario]] |
| [[campana_publicitaria]] | `aprobada_por` | ↗ 14 | [[campana_publicitaria.aprobada_por → usuario]] |
| [[canal_vinculado]] | `usuario_id` | ↗ 05 | [[canal_vinculado.usuario_id → usuario]] |
| [[candidato_reemplazo]] | `usuario_id` | ↗ 08 | [[candidato_reemplazo.usuario_id → usuario]] |
| [[capacitacion_cumplimiento]] | `usuario_id` | ↗ 12 | [[capacitacion_cumplimiento.usuario_id → usuario]] |
| [[caso_investigacion_lft]] | `analista_id` | ↗ 12 | [[caso_investigacion_lft.analista_id → usuario]] |
| [[caso_investigacion_lft]] | `revisado_por` | ↗ 12 | [[caso_investigacion_lft.revisado_por → usuario]] |
| [[caso_investigacion_lft]] | `usuario_id` | ↗ 12 | [[caso_investigacion_lft.usuario_id → usuario]] |
| [[castigo_deuda]] | `aprobado_por` | ↗ 08 | [[castigo_deuda.aprobado_por → usuario]] |
| [[certificado_reputacion]] | `usuario_id` | ↗ 06 | [[certificado_reputacion.usuario_id → usuario]] |
| [[certificado_saldo]] | `solicitado_por` | ↗ 10 | [[certificado_saldo.solicitado_por → usuario]] |
| [[cierre_diario]] | `cerrado_por` | ↗ 03 | [[cierre_diario.cerrado_por → usuario]] |
| [[cierre_periodo_contable]] | `cerrado_por` | ↗ 13 | [[cierre_periodo_contable.cerrado_por → usuario]] |
| [[clic_anuncio]] | `usuario_id` | ↗ 14 | [[clic_anuncio.usuario_id → usuario]] |
| [[cobertura_incumplimiento]] | `aprobada_por` | ↗ 08 | [[cobertura_incumplimiento.aprobada_por → usuario]] |
| [[coincidencia_lista]] | `revisada_por` | ↗ 09 | [[coincidencia_lista.revisada_por → usuario]] |
| [[coincidencia_lista]] | `usuario_id` | ↗ 09 | [[coincidencia_lista.usuario_id → usuario]] |
| [[comprobante_manual]] | `revisado_por` | ↗ 03 | [[comprobante_manual.revisado_por → usuario]] |
| [[comprobante_manual]] | `segunda_revision_por` | ↗ 03 | [[comprobante_manual.segunda_revision_por → usuario]] |
| [[conciliacion]] | `conciliado_por` | ↗ 03 | [[conciliacion.conciliado_por → usuario]] |
| [[conciliacion_custodia]] | `ejecutada_por` | ↗ 10 | [[conciliacion_custodia.ejecutada_por → usuario]] |
| [[consentimiento]] | `usuario_id` | 01 | [[consentimiento.usuario_id → usuario]] |
| [[contrato_adhesion]] | `aprobado_por` | ↗ 12 | [[contrato_adhesion.aprobado_por → usuario]] |
| [[contrato_tercero]] | `responsable_id` | ↗ 12 | [[contrato_tercero.responsable_id → usuario]] |
| [[control_interno]] | `responsable_id` | ↗ 12 | [[control_interno.responsable_id → usuario]] |
| [[credencial_acceso]] | `usuario_id` | 01 | [[credencial_acceso.usuario_id → usuario]] |
| [[cuenta_bancaria_beneficiario]] | `usuario_id` | ↗ 04 | [[cuenta_bancaria_beneficiario.usuario_id → usuario]] |
| [[cuenta_billetera]] | `usuario_id` | ↗ 10 | [[cuenta_billetera.usuario_id → usuario]] |
| [[cuenta_por_cobrar_comision]] | `usuario_id` | ↗ 11 | [[cuenta_por_cobrar_comision.usuario_id → usuario]] |
| [[datos_facturacion]] | `usuario_id` | ↗ 11 | [[datos_facturacion.usuario_id → usuario]] |
| [[debida_diligencia]] | `aprobada_por` | ↗ 12 | [[debida_diligencia.aprobada_por → usuario]] |
| [[debida_diligencia]] | `segunda_revision_por` | ↗ 12 | [[debida_diligencia.segunda_revision_por → usuario]] |
| [[debida_diligencia]] | `usuario_id` | ↗ 12 | [[debida_diligencia.usuario_id → usuario]] |
| [[declaracion_origen_fondos]] | `usuario_id` | ↗ 12 | [[declaracion_origen_fondos.usuario_id → usuario]] |
| [[declaracion_origen_fondos]] | `verificada_por` | ↗ 12 | [[declaracion_origen_fondos.verificada_por → usuario]] |
| [[declaracion_pep]] | `usuario_id` | ↗ 12 | [[declaracion_pep.usuario_id → usuario]] |
| [[declaracion_pep]] | `verificada_por` | ↗ 12 | [[declaracion_pep.verificada_por → usuario]] |
| [[descargo_participante]] | `resuelto_por` | ↗ 08 | [[descargo_participante.resuelto_por → usuario]] |
| [[descuadre_custodia]] | `resuelto_por` | ↗ 10 | [[descuadre_custodia.resuelto_por → usuario]] |
| [[designacion_regulatoria]] | `usuario_id` | ↗ 12 | [[designacion_regulatoria.usuario_id → usuario]] |
| [[desvio_perfil]] | `usuario_id` | ↗ 12 | [[desvio_perfil.usuario_id → usuario]] |
| [[deuda_participante]] | `usuario_id` | ↗ 08 | [[deuda_participante.usuario_id → usuario]] |
| [[devengo_comision]] | `usuario_obligado_id` | ↗ 11 | [[devengo_comision.usuario_obligado_id → usuario]] |
| [[devolucion_comision]] | `autorizada_por` | ↗ 11 | [[devolucion_comision.autorizada_por → usuario]] |
| [[direccion_usuario]] | `usuario_id` | 01 | [[direccion_usuario.usuario_id → usuario]] |
| [[dispositivo]] | `usuario_id` | 01 | [[dispositivo.usuario_id → usuario]] |
| [[documento_identidad]] | `usuario_id` | 01 | [[documento_identidad.usuario_id → usuario]] |
| [[documento_publicado]] | `publicado_por` | ↗ 12 | [[documento_publicado.publicado_por → usuario]] |
| [[ejecucion_reporte]] | `solicitado_por` | ↗ 09 | [[ejecucion_reporte.solicitado_por → usuario]] |
| [[ejercicio_fiscal]] | `cerrado_por` | ↗ 13 | [[ejercicio_fiscal.cerrado_por → usuario]] |
| [[entrega_fondo]] | `autorizada_por` | ↗ 04 | [[entrega_fondo.autorizada_por → usuario]] |
| [[entrega_fondo]] | `ejecutada_por` | ↗ 04 | [[entrega_fondo.ejecutada_por → usuario]] |
| [[envio_regulatorio]] | `enviado_por` | ↗ 12 | [[envio_regulatorio.enviado_por → usuario]] |
| [[estado_financiero_generado]] | `generado_por` | ↗ 13 | [[estado_financiero_generado.generado_por → usuario]] |
| [[evaluacion_antifraude]] | `revisada_por` | ↗ 10 | [[evaluacion_antifraude.revisada_por → usuario]] |
| [[evaluacion_riesgo_producto]] | `aprobada_por` | ↗ 12 | [[evaluacion_riesgo_producto.aprobada_por → usuario]] |
| [[evaluacion_tercero]] | `evaluado_por` | ↗ 12 | [[evaluacion_tercero.evaluado_por → usuario]] |
| [[evento_reputacion]] | `usuario_id` | ↗ 06 | [[evento_reputacion.usuario_id → usuario]] |
| [[evento_riesgo_operativo]] | `registrado_por` | ↗ 12 | [[evento_riesgo_operativo.registrado_por → usuario]] |
| [[evento_significativo_sin]] | `registrado_por` | ↗ 11 | [[evento_significativo_sin.registrado_por → usuario]] |
| [[evidencia_incumplimiento]] | `aportada_por` | ↗ 08 | [[evidencia_incumplimiento.aportada_por → usuario]] |
| [[excepcion_conciliacion]] | `asignada_a` | ↗ 03 | [[excepcion_conciliacion.asignada_a → usuario]] |
| [[exencion_comision]] | `autorizada_por` | ↗ 11 | [[exencion_comision.autorizada_por → usuario]] |
| [[exencion_comision]] | `usuario_id` | ↗ 11 | [[exencion_comision.usuario_id → usuario]] |
| [[expediente_cliente]] | `responsable_id` | ↗ 12 | [[expediente_cliente.responsable_id → usuario]] |
| [[expediente_cliente]] | `usuario_id` | ↗ 12 | [[expediente_cliente.usuario_id → usuario]] |
| [[extracto_bancario]] | `importado_por` | ↗ 03 | [[extracto_bancario.importado_por → usuario]] |
| [[factor_mfa]] | `usuario_id` | 01 | [[factor_mfa.usuario_id → usuario]] |
| [[factor_riesgo_evaluado]] | `usuario_id` | ↗ 12 | [[factor_riesgo_evaluado.usuario_id → usuario]] |
| [[factura_electronica]] | `usuario_id` | ↗ 11 | [[factura_electronica.usuario_id → usuario]] |
| [[factura_proveedor]] | `aprobada_por` | ↗ 13 | [[factura_proveedor.aprobada_por → usuario]] |
| [[gestion_cobranza]] | `gestor_asignado_id` | ↗ 08 | [[gestion_cobranza.gestor_asignado_id → usuario]] |
| [[hallazgo_auditoria]] | `responsable_id` | ↗ 12 | [[hallazgo_auditoria.responsable_id → usuario]] |
| [[historial_credencial]] | `usuario_id` | 01 | [[historial_credencial.usuario_id → usuario]] |
| [[historial_estado_entrega]] | `ejecutado_por` | ↗ 04 | [[historial_estado_entrega.ejecutado_por → usuario]] |
| [[historial_estado_grupo]] | `ejecutado_por` | ↗ 02 | [[historial_estado_grupo.ejecutado_por → usuario]] |
| [[historial_estado_incumplimiento]] | `ejecutado_por` | ↗ 08 | [[historial_estado_incumplimiento.ejecutado_por → usuario]] |
| [[historial_incumplimiento_usuario]] | `usuario_id` | ↗ 08 | [[historial_incumplimiento_usuario.usuario_id → usuario]] |
| [[impresion_anuncio]] | `usuario_id` | ↗ 14 | [[impresion_anuncio.usuario_id → usuario]] |
| [[incidencia_entrega]] | `asignada_a` | ↗ 04 | [[incidencia_entrega.asignada_a → usuario]] |
| [[incidencia_entrega]] | `reportada_por` | ↗ 04 | [[incidencia_entrega.reportada_por → usuario]] |
| [[incidente_seguridad]] | `responsable_id` | ↗ 12 | [[incidente_seguridad.responsable_id → usuario]] |
| [[insignia_otorgada]] | `usuario_id` | ↗ 06 | [[insignia_otorgada.usuario_id → usuario]] |
| [[instrumento_fondeo]] | `usuario_id` | ↗ 10 | [[instrumento_fondeo.usuario_id → usuario]] |
| [[intento_autenticacion]] | `usuario_id` | 01 | [[intento_autenticacion.usuario_id → usuario]] |
| [[invitacion]] | `emisor_id` | ↗ 02 | [[invitacion.emisor_id → usuario]] |
| [[licencia_regulatoria]] | `responsable_id` | ↗ 12 | [[licencia_regulatoria.responsable_id → usuario]] |
| [[liquidacion_ingresos]] | `cerrada_por` | ↗ 11 | [[liquidacion_ingresos.cerrada_por → usuario]] |
| [[lista_restriccion_interna]] | `retirado_por` | ↗ 08 | [[lista_restriccion_interna.retirado_por → usuario]] |
| [[lista_restriccion_interna]] | `usuario_id` | ↗ 08 | [[lista_restriccion_interna.usuario_id → usuario]] |
| [[matriz_riesgo_lft]] | `aprobada_por` | ↗ 12 | [[matriz_riesgo_lft.aprobada_por → usuario]] |
| [[movimiento_fondo]] | `registrado_por` | ↗ 08 | [[movimiento_fondo.registrado_por → usuario]] |
| [[notificacion]] | `usuario_id` | ↗ 05 | [[notificacion.usuario_id → usuario]] |
| [[observacion_regulatoria]] | `responsable_id` | ↗ 12 | [[observacion_regulatoria.responsable_id → usuario]] |
| [[oficial_cumplimiento]] | `usuario_id` | ↗ 12 | [[oficial_cumplimiento.usuario_id → usuario]] |
| [[orden_compra]] | `aprobada_por` | ↗ 13 | [[orden_compra.aprobada_por → usuario]] |
| [[orden_retiro]] | `aprobada_por` | ↗ 10 | [[orden_retiro.aprobada_por → usuario]] |
| [[orden_retiro]] | `solicitada_por` | ↗ 10 | [[orden_retiro.solicitada_por → usuario]] |
| [[organizador]] | `usuario_id` | ↗ 07 | [[organizador.usuario_id → usuario]] |
| [[pago]] | `registrado_por` | ↗ 03 | [[pago.registrado_por → usuario]] |
| [[pago_a_proveedor]] | `autorizado_por` | ↗ 13 | [[pago_a_proveedor.autorizado_por → usuario]] |
| [[participante]] | `usuario_id` | ↗ 02 | [[participante.usuario_id → usuario]] |
| [[perfil_financiero]] | `usuario_id` | 01 | [[perfil_financiero.usuario_id → usuario]] |
| [[perfil_transaccional]] | `usuario_id` | ↗ 12 | [[perfil_transaccional.usuario_id → usuario]] |
| [[plan_accion_riesgo]] | `responsable_id` | ↗ 12 | [[plan_accion_riesgo.responsable_id → usuario]] |
| [[plan_continuidad]] | `responsable_id` | ↗ 12 | [[plan_continuidad.responsable_id → usuario]] |
| [[plan_regularizacion]] | `aprobado_por` | ↗ 03 | [[plan_regularizacion.aprobado_por → usuario]] |
| [[politica_billetera]] | `aprobada_por` | ↗ 10 | [[politica_billetera.aprobada_por → usuario]] |
| [[politica_interna]] | `responsable_id` | ↗ 12 | [[politica_interna.responsable_id → usuario]] |
| [[postulacion_emparejamiento]] | `usuario_id` | ↗ 02 | [[postulacion_emparejamiento.usuario_id → usuario]] |
| [[preferencia_notificacion]] | `usuario_id` | 01 | [[preferencia_notificacion.usuario_id → usuario]] |
| [[presupuesto]] | `aprobado_por` | ↗ 13 | [[presupuesto.aprobado_por → usuario]] |
| [[proceso_anonimizacion]] | `usuario_id` | ↗ 09 | [[proceso_anonimizacion.usuario_id → usuario]] |
| [[promesa_pago]] | `registrada_por` | ↗ 08 | [[promesa_pago.registrada_por → usuario]] |
| [[prueba_continuidad]] | `ejecutada_por` | ↗ 12 | [[prueba_continuidad.ejecutada_por → usuario]] |
| [[prueba_control]] | `ejecutada_por` | ↗ 12 | [[prueba_control.ejecutada_por → usuario]] |
| [[puntaje_reputacion]] | `usuario_id` | ↗ 06 | [[puntaje_reputacion.usuario_id → usuario]] |
| [[punto_reclamo]] | `responsable_id` | ↗ 12 | [[punto_reclamo.responsable_id → usuario]] |
| [[reclamo_cliente]] | `responsable_id` | ↗ 12 | [[reclamo_cliente.responsable_id → usuario]] |
| [[reclamo_cliente]] | `usuario_id` | ↗ 12 | [[reclamo_cliente.usuario_id → usuario]] |
| [[reembolso]] | `aprobado_por` | ↗ 03 | [[reembolso.aprobado_por → usuario]] |
| [[reembolso]] | `solicitado_por` | ↗ 03 | [[reembolso.solicitado_por → usuario]] |
| [[referencia_personal]] | `usuario_id` | 01 | [[referencia_personal.usuario_id → usuario]] |
| [[registro_acceso_datos]] | `usuario_afectado_id` | ↗ 09 | [[registro_acceso_datos.usuario_afectado_id → usuario]] |
| [[registro_acceso_datos]] | `usuario_consultor_id` | ↗ 09 | [[registro_acceso_datos.usuario_consultor_id → usuario]] |
| [[registro_incumplimiento]] | `reportado_por` | ↗ 08 | [[registro_incumplimiento.reportado_por → usuario]] |
| [[registro_incumplimiento]] | `responsable_gestion` | ↗ 08 | [[registro_incumplimiento.responsable_gestion → usuario]] |
| [[registro_incumplimiento]] | `usuario_id` | ↗ 08 | [[registro_incumplimiento.usuario_id → usuario]] |
| [[registro_operacion_relevante]] | `usuario_id` | ↗ 12 | [[registro_operacion_relevante.usuario_id → usuario]] |
| [[regla_antifraude]] | `aprobada_por` | ↗ 10 | [[regla_antifraude.aprobada_por → usuario]] |
| [[regla_monitoreo_lft]] | `aprobada_por` | ↗ 12 | [[regla_monitoreo_lft.aprobada_por → usuario]] |
| [[reglamento_grupo]] | `redactado_por` | ↗ 02 | [[reglamento_grupo.redactado_por → usuario]] |
| [[reporte_operacion_sospechosa]] | `aprobado_por` | ↗ 09 | [[reporte_operacion_sospechosa.aprobado_por → usuario]] |
| [[reporte_operacion_sospechosa]] | `usuario_id` | ↗ 09 | [[reporte_operacion_sospechosa.usuario_id → usuario]] |
| [[reporte_regulatorio]] | `aprobado_por` | ↗ 12 | [[reporte_regulatorio.aprobado_por → usuario]] |
| [[reporte_regulatorio]] | `generado_por` | ↗ 12 | [[reporte_regulatorio.generado_por → usuario]] |
| [[reporte_regulatorio]] | `revisado_por` | ↗ 12 | [[reporte_regulatorio.revisado_por → usuario]] |
| [[reputacion_usuario]] | `usuario_id` | 01 | [[reputacion_usuario.usuario_id → usuario]] |
| [[requerimiento_autoridad]] | `respondido_por` | ↗ 12 | [[requerimiento_autoridad.respondido_por → usuario]] |
| [[requerimiento_autoridad]] | `usuario_afectado_id` | ↗ 12 | [[requerimiento_autoridad.usuario_afectado_id → usuario]] |
| [[resena_participante]] | `evaluado_usuario_id` | ↗ 06 | [[resena_participante.evaluado_usuario_id → usuario]] |
| [[resena_participante]] | `moderada_por` | ↗ 06 | [[resena_participante.moderada_por → usuario]] |
| [[respuesta_idempotente]] | `usuario_id` | ↗ 10 | [[respuesta_idempotente.usuario_id → usuario]] |
| [[restriccion_usuario]] | `levantada_por` | 01 | [[restriccion_usuario.levantada_por → usuario]] |
| [[restriccion_usuario]] | `usuario_id` | 01 | [[restriccion_usuario.usuario_id → usuario]] |
| [[retencion_saldo]] | `liberada_por` | ↗ 10 | [[retencion_saldo.liberada_por → usuario]] |
| [[reverso_transaccion]] | `autorizada_por` | ↗ 10 | [[reverso_transaccion.autorizada_por → usuario]] |
| [[revision_creativa]] | `revisada_por` | ↗ 14 | [[revision_creativa.revisada_por → usuario]] |
| [[revision_periodica_kyc]] | `ejecutada_por` | ↗ 12 | [[revision_periodica_kyc.ejecutada_por → usuario]] |
| [[revision_periodica_kyc]] | `usuario_id` | ↗ 12 | [[revision_periodica_kyc.usuario_id → usuario]] |
| [[sancion]] | `aplicada_por` | ↗ 08 | [[sancion.aplicada_por → usuario]] |
| [[sancion]] | `usuario_id` | ↗ 08 | [[sancion.usuario_id → usuario]] |
| [[sancion_organizador]] | `aplicada_por` | ↗ 07 | [[sancion_organizador.aplicada_por → usuario]] |
| [[score_riesgo_incumplimiento]] | `usuario_id` | ↗ 08 | [[score_riesgo_incumplimiento.usuario_id → usuario]] |
| [[segmento_audiencia]] | `creado_por` | ↗ 14 | [[segmento_audiencia.creado_por → usuario]] |
| [[sesion]] | `usuario_id` | 01 | [[sesion.usuario_id → usuario]] |
| [[simulacion_tarifa]] | `ejecutada_por` | ↗ 11 | [[simulacion_tarifa.ejecutada_por → usuario]] |
| [[snapshot_reputacion]] | `usuario_id` | ↗ 06 | [[snapshot_reputacion.usuario_id → usuario]] |
| [[socio_comercial]] | `verificado_por` | ↗ 14 | [[socio_comercial.verificado_por → usuario]] |
| [[solicitud_baja]] | `usuario_id` | 01 | [[solicitud_baja.usuario_id → usuario]] |
| [[solicitud_cierre_billetera]] | `aprobada_por` | ↗ 10 | [[solicitud_cierre_billetera.aprobada_por → usuario]] |
| [[solicitud_datos_personales]] | `atendida_por` | ↗ 09 | [[solicitud_datos_personales.atendida_por → usuario]] |
| [[solicitud_datos_personales]] | `usuario_id` | ↗ 09 | [[solicitud_datos_personales.usuario_id → usuario]] |
| [[solicitud_ingreso]] | `revisada_por` | ↗ 02 | [[solicitud_ingreso.revisada_por → usuario]] |
| [[solicitud_ingreso]] | `usuario_id` | ↗ 02 | [[solicitud_ingreso.usuario_id → usuario]] |
| [[solicitud_organizador]] | `revisada_por` | ↗ 07 | [[solicitud_organizador.revisada_por → usuario]] |
| [[solicitud_organizador]] | `usuario_id` | ↗ 07 | [[solicitud_organizador.usuario_id → usuario]] |
| [[sorteo_turnos]] | `ejecutado_por` | ↗ 02 | [[sorteo_turnos.ejecutado_por → usuario]] |
| [[tarifario]] | `aprobado_por` | ↗ 11 | [[tarifario.aprobado_por → usuario]] |
| [[ticket_soporte]] | `asignado_a` | ↗ 09 | [[ticket_soporte.asignado_a → usuario]] |
| [[ticket_soporte]] | `usuario_id` | ↗ 09 | [[ticket_soporte.usuario_id → usuario]] |
| [[token_verificacion]] | `usuario_id` | 01 | [[token_verificacion.usuario_id → usuario]] |
| [[transaccion_billetera]] | `iniciada_por` | ↗ 10 | [[transaccion_billetera.iniciada_por → usuario]] |
| [[validacion_pre_entrega]] | `omitida_por` | ↗ 04 | [[validacion_pre_entrega.omitida_por → usuario]] |
| [[verificacion_kyc]] | `revisada_por` | 01 | [[verificacion_kyc.revisada_por → usuario]] |
| [[verificacion_kyc]] | `usuario_id` | 01 | [[verificacion_kyc.usuario_id → usuario]] |

## Entidades vecinas

[[abono_recuperacion]] · [[accion_cobranza]] · [[aceptacion_contrato]] · [[acta_comite]] · [[activo_informacion]] · [[acuerdo]] · [[acuerdo_quita]] · [[alerta_cumplimiento]] · [[alerta_monitoreo_lft]] · [[alerta_temprana]] · [[apelacion_sancion]] · [[apelacion_sancion_org]] · [[asiento_contable]] · [[asiento_plantilla]] · [[asignacion_rol]] · [[asignacion_tarifario]] · [[aval_participante]] · [[bandeja_entrada]] · [[beneficiario_final]] · [[bitacora_evento]] · [[bloqueo_cuenta]] · [[bloqueo_saldo]] · [[calificacion_riesgo_cliente]] · [[cambio_tarifario]] · [[campana_promocional]] · [[campana_publicitaria]] · [[canal_vinculado]] · [[candidato_reemplazo]] · [[capacitacion_cumplimiento]] · [[caso_investigacion_lft]] · [[castigo_deuda]] · [[certificado_reputacion]] · [[certificado_saldo]] · [[cierre_diario]] · [[cierre_periodo_contable]] · [[clic_anuncio]] · [[cobertura_incumplimiento]] · [[coincidencia_lista]] · [[comprobante_manual]] · [[conciliacion]] · [[conciliacion_custodia]] · [[consentimiento]] · [[contrato_adhesion]] · [[contrato_tercero]] · [[control_interno]] · [[credencial_acceso]] · [[cuenta_bancaria_beneficiario]] · [[cuenta_billetera]] · [[cuenta_por_cobrar_comision]] · [[datos_facturacion]] · [[debida_diligencia]] · [[declaracion_origen_fondos]] · [[declaracion_pep]] · [[descargo_participante]] · [[descuadre_custodia]] · [[designacion_regulatoria]] · [[desvio_perfil]] · [[deuda_participante]] · [[devengo_comision]] · [[devolucion_comision]] · [[direccion_usuario]] · [[dispositivo]] · [[documento_identidad]] · [[documento_publicado]] · [[ejecucion_reporte]] · [[ejercicio_fiscal]] · [[entrega_fondo]] · [[envio_regulatorio]] · [[estado_financiero_generado]] · [[evaluacion_antifraude]] · [[evaluacion_riesgo_producto]] · [[evaluacion_tercero]] · [[evento_reputacion]] · [[evento_riesgo_operativo]] · [[evento_significativo_sin]] · [[evidencia_incumplimiento]] · [[excepcion_conciliacion]] · [[exencion_comision]] · [[expediente_cliente]] · [[extracto_bancario]] · [[factor_mfa]] · [[factor_riesgo_evaluado]] · [[factura_electronica]] · [[factura_proveedor]] · [[gestion_cobranza]] · [[hallazgo_auditoria]] · [[historial_credencial]] · [[historial_estado_entrega]] · [[historial_estado_grupo]] · [[historial_estado_incumplimiento]] · [[historial_incumplimiento_usuario]] · [[impresion_anuncio]] · [[incidencia_entrega]] · [[incidente_seguridad]] · [[insignia_otorgada]] · [[instrumento_fondeo]] · [[intento_autenticacion]] · [[invitacion]] · [[licencia_regulatoria]] · [[liquidacion_ingresos]] · [[lista_restriccion_interna]] · [[matriz_riesgo_lft]] · [[movimiento_fondo]] · [[notificacion]] · [[observacion_regulatoria]] · [[oficial_cumplimiento]] · [[orden_compra]] · [[orden_retiro]] · [[organizador]] · [[pago]] · [[pago_a_proveedor]] · [[participante]] · [[perfil_financiero]] · [[perfil_transaccional]] · [[plan_accion_riesgo]] · [[plan_continuidad]] · [[plan_regularizacion]] · [[politica_billetera]] · [[politica_interna]] · [[postulacion_emparejamiento]] · [[preferencia_notificacion]] · [[presupuesto]] · [[proceso_anonimizacion]] · [[promesa_pago]] · [[prueba_continuidad]] · [[prueba_control]] · [[puntaje_reputacion]] · [[punto_reclamo]] · [[reclamo_cliente]] · [[reembolso]] · [[referencia_personal]] · [[registro_acceso_datos]] · [[registro_incumplimiento]] · [[registro_operacion_relevante]] · [[regla_antifraude]] · [[regla_monitoreo_lft]] · [[reglamento_grupo]] · [[reporte_operacion_sospechosa]] · [[reporte_regulatorio]] · [[reputacion_usuario]] · [[requerimiento_autoridad]] · [[resena_participante]] · [[respuesta_idempotente]] · [[restriccion_usuario]] · [[retencion_saldo]] · [[reverso_transaccion]] · [[revision_creativa]] · [[revision_periodica_kyc]] · [[sancion]] · [[sancion_organizador]] · [[score_riesgo_incumplimiento]] · [[segmento_audiencia]] · [[sesion]] · [[simulacion_tarifa]] · [[snapshot_reputacion]] · [[socio_comercial]] · [[solicitud_baja]] · [[solicitud_cierre_billetera]] · [[solicitud_datos_personales]] · [[solicitud_ingreso]] · [[solicitud_organizador]] · [[sorteo_turnos]] · [[tarifario]] · [[ticket_soporte]] · [[token_verificacion]] · [[transaccion_billetera]] · [[validacion_pre_entrega]] · [[verificacion_kyc]]

## Ver también

- Justificación de negocio: [[01_identidad_usuarios]]
- Diagramas: `docs/entidades/01_identidad_usuarios.puml`
- Índice: [[_Entidades]] · [[Index]]
