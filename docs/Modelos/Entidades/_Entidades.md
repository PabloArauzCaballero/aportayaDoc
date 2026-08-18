---
tags:
  - moc
  - indice
entidades: 306
---

# Índice de entidades

Las **306 tablas** del modelo, agrupadas por módulo. «Sal.» y «Ent.» son claves foráneas salientes y entrantes.

[[Index|← Índice general]] · [[_Relaciones|Relaciones →]]

## 01 — Identidad, Usuarios y Seguridad

> Saber con certeza a quién le estás confiando plata ajena · [[01_identidad_usuarios|ficha de negocio]] · [[_Entidades 01|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[usuario]] | 18 | 0 | 211 | muy conectada |
| [[direccion_usuario]] | 8 | 1 | 0 | — |
| [[perfil_financiero]] | 8 | 1 | 0 | — |
| [[credencial_acceso]] | 8 | 1 | 0 | — |
| [[historial_credencial]] | 4 | 1 | 0 | — |
| [[politica_token]] | 12 | 0 | 0 | — |
| [[token_verificacion]] | 31 | 4 | 10 | muy conectada |
| [[intento_validacion_token]] | 7 | 1 | 0 | — |
| [[factor_mfa]] | 9 | 1 | 0 | — |
| [[dispositivo]] | 11 | 1 | 4 | — |
| [[sesion]] | 11 | 2 | 1 | — |
| [[intento_autenticacion]] | 10 | 1 | 0 | — |
| [[bloqueo_cuenta]] | 7 | 2 | 0 | — |
| [[restriccion_usuario]] | 10 | 2 | 0 | — |
| [[documento_identidad]] | 14 | 1 | 1 | — |
| [[verificacion_kyc]] | 14 | 3 | 2 | — |
| [[referencia_personal]] | 8 | 1 | 0 | — |
| [[rol]] | 5 | 0 | 2 | — |
| [[permiso]] | 6 | 0 | 1 | — |
| [[rol_permiso]] | 2 | 2 | 0 | — |
| [[asignacion_rol]] | 10 | 3 | 0 | — |
| [[consentimiento]] | 10 | 1 | 0 | — |
| [[preferencia_notificacion]] | 12 | 1 | 0 | — |
| [[reputacion_usuario]] | 11 | 1 | 0 | — |
| [[solicitud_baja]] | 6 | 1 | 0 | — |

## 02 — Grupos, Cupos, Turnos y Gobernanza

> Reglas del juego, orden de cobro y decisiones colectivas · [[02_grupos_turnos|ficha de negocio]] · [[_Entidades 02|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[grupo]] | 27 | 1 | 45 | muy conectada |
| [[configuracion_grupo]] | 10 | 3 | 0 | — |
| [[reglamento_grupo]] | 10 | 2 | 1 | — |
| [[aceptacion_reglamento]] | 7 | 3 | 0 | — |
| [[historial_estado_grupo]] | 7 | 2 | 0 | — |
| [[participante]] | 13 | 3 | 25 | muy conectada |
| [[cupo]] | 8 | 2 | 6 | muy conectada |
| [[traspaso_cupo]] | 10 | 4 | 0 | — |
| [[solicitud_retiro]] | 9 | 2 | 0 | — |
| [[solicitud_ingreso]] | 10 | 3 | 0 | — |
| [[invitacion]] | 12 | 3 | 0 | — |
| [[periodo]] | 11 | 1 | 6 | — |
| [[turno]] | 11 | 4 | 4 | muy conectada |
| [[sorteo_turnos]] | 14 | 2 | 0 | — |
| [[solicitud_permuta]] | 11 | 4 | 0 | — |
| [[dia_no_habil]] | 5 | 1 | 0 | — |
| [[postulacion_emparejamiento]] | 11 | 1 | 1 | — |
| [[criterio_emparejamiento]] | 8 | 0 | 1 | — |
| [[propuesta_grupo]] | 10 | 2 | 1 | — |
| [[propuesta_postulacion]] | 4 | 2 | 0 | — |
| [[acuerdo]] | 15 | 2 | 8 | muy conectada |
| [[voto_participante]] | 7 | 2 | 0 | — |

## 03 — Aportes, Pagos QR y Conciliación

> Que "pagué" signifique "el banco lo confirmó" · [[03_aportes_pagos_qr|ficha de negocio]] · [[_Entidades 03|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[politica_mora]] | 10 | 1 | 2 | — |
| [[obligacion_aporte]] | 22 | 7 | 7 | muy conectada |
| [[plan_regularizacion]] | 7 | 2 | 2 | — |
| [[proveedor_pago]] | 12 | 0 | 8 | muy conectada |
| [[orden_cobro]] | 11 | 2 | 4 | — |
| [[qr_cobro]] | 9 | 1 | 0 | — |
| [[enlace_pago_rapido]] | 6 | 2 | 0 | — |
| [[intento_pago]] | 10 | 1 | 1 | — |
| [[pago]] | 19 | 4 | 9 | muy conectada |
| [[comprobante_manual]] | 9 | 3 | 0 | — |
| [[constancia_pago]] | 7 | 1 | 0 | — |
| [[reembolso]] | 10 | 3 | 0 | — |
| [[disputa_pago]] | 10 | 1 | 0 | — |
| [[extracto_bancario]] | 10 | 2 | 1 | — |
| [[movimiento_bancario]] | 9 | 1 | 2 | — |
| [[conciliacion]] | 8 | 3 | 1 | — |
| [[excepcion_conciliacion]] | 10 | 2 | 0 | — |
| [[webhook_pasarela]] | 13 | 2 | 0 | — |
| [[tipo_cambio]] | 7 | 0 | 0 | — |
| [[cuenta_contable]] | 11 | 3 | 12 | muy conectada |
| [[asiento_contable]] | 11 | 4 | 12 | append-only, muy conectada |
| [[movimiento_contable]] | 6 | 2 | 0 | append-only |
| [[cierre_diario]] | 10 | 1 | 1 | — |

## 04 — Entregas de Fondo

> Que la bolsa llegue completa, a la persona correcta, una sola vez · [[04_entregas_fondo|ficha de negocio]] · [[_Entidades 04|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[entrega_fondo]] | 23 | 8 | 8 | muy conectada |
| [[deduccion_entrega]] | 9 | 1 | 1 | — |
| [[regla_entrega]] | 8 | 0 | 1 | — |
| [[validacion_pre_entrega]] | 10 | 3 | 0 | — |
| [[cuenta_bancaria_beneficiario]] | 16 | 1 | 2 | — |
| [[orden_desembolso]] | 12 | 3 | 1 | — |
| [[intento_desembolso]] | 9 | 1 | 0 | — |
| [[confirmacion_recepcion]] | 10 | 2 | 0 | — |
| [[incidencia_entrega]] | 14 | 3 | 0 | — |
| [[historial_estado_entrega]] | 7 | 2 | 0 | — |

## 05 — Notificaciones y Comunicaciones

> WhatsApp como canal real de cobro, sin spam ni doble aviso · [[05_notificaciones|ficha de negocio]] · [[_Entidades 05|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[evento_notificable]] | 12 | 0 | 3 | — |
| [[plantilla_mensaje]] | 9 | 1 | 1 | — |
| [[version_plantilla]] | 11 | 1 | 1 | — |
| [[proveedor_mensajeria]] | 11 | 0 | 1 | — |
| [[canal_vinculado]] | 12 | 1 | 2 | — |
| [[lista_supresion]] | 8 | 0 | 0 | — |
| [[notificacion]] | 11 | 2 | 5 | — |
| [[envio_notificacion]] | 22 | 4 | 3 | — |
| [[evento_entrega_mensaje]] | 8 | 1 | 0 | — |
| [[cola_envio]] | 6 | 1 | 0 | — |
| [[cola_muerta]] | 6 | 1 | 0 | — |
| [[enlace_pago_notificado]] | 9 | 3 | 0 | — |
| [[respuesta_entrante]] | 8 | 2 | 0 | — |
| [[programacion_recordatorio]] | 9 | 2 | 0 | — |
| [[bandeja_entrada]] | 9 | 2 | 0 | — |

## 06 — Transparencia y Reputación

> Que nadie tenga que "creerle" al organizador · [[06_transparencia_reputacion|ficha de negocio]] · [[_Entidades 06|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[modelo_scoring]] | 12 | 0 | 3 | — |
| [[peso_factor]] | 7 | 1 | 0 | — |
| [[regla_impacto_evento]] | 8 | 1 | 0 | — |
| [[evento_reputacion]] | 15 | 4 | 1 | append-only |
| [[puntaje_reputacion]] | 18 | 2 | 1 | — |
| [[componente_score]] | 7 | 1 | 0 | — |
| [[snapshot_reputacion]] | 7 | 1 | 1 | — |
| [[certificado_reputacion]] | 10 | 2 | 0 | — |
| [[insignia_logro]] | 6 | 0 | 1 | — |
| [[insignia_otorgada]] | 6 | 2 | 0 | — |
| [[metrica_grupo]] | 9 | 2 | 0 | — |
| [[bloque_transparencia]] | 11 | 1 | 1 | — |
| [[registro_sellado]] | 7 | 1 | 0 | append-only |
| [[verificacion_publica]] | 9 | 0 | 0 | — |
| [[resena_participante]] | 10 | 4 | 0 | — |
| [[alerta_riesgo]] | 10 | 0 | 0 | — |

## 07 — Organizador y Automatización

> Administrar es un rol, no un negocio: el organizador no cobra ni custodia · [[07_organizador_automatizacion|ficha de negocio]] · [[_Entidades 07|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[organizador]] | 15 | 1 | 6 | — |
| [[solicitud_organizador]] | 11 | 3 | 0 | — |
| [[requisito_habilitacion]] | 8 | 0 | 0 | — |
| [[capacitacion_organizador]] | 7 | 1 | 0 | — |
| [[contrato_organizador]] | 12 | 2 | 0 | — |
| [[evaluacion_desempeno]] | 13 | 1 | 2 | — |
| [[metrica_organizador]] | 7 | 1 | 0 | — |
| [[sancion_organizador]] | 9 | 3 | 1 | — |
| [[apelacion_sancion_org]] | 9 | 2 | 0 | — |
| [[regla_automatizacion]] | 10 | 0 | 1 | — |
| [[tarea_automatizada]] | 8 | 2 | 1 | — |
| [[ejecucion_tarea]] | 8 | 1 | 0 | — |

## 08 — Garantía, Incumplimiento, Cobranza y Sanciones

> El grupo no se detiene, pero la deuda no se perdona sola · [[08_garantia_incumplimiento|ficha de negocio]] · [[_Entidades 08|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[politica_cobertura]] | 13 | 1 | 1 | — |
| [[fondo_garantia]] | 14 | 3 | 3 | — |
| [[movimiento_fondo]] | 11 | 3 | 2 | append-only |
| [[devolucion_fondo]] | 9 | 2 | 0 | — |
| [[registro_incumplimiento]] | 30 | 9 | 11 | append-only, muy conectada |
| [[evidencia_incumplimiento]] | 10 | 2 | 0 | — |
| [[historial_estado_incumplimiento]] | 9 | 2 | 0 | append-only |
| [[descargo_participante]] | 10 | 3 | 0 | — |
| [[historial_incumplimiento_usuario]] | 14 | 1 | 0 | — |
| [[lista_restriccion_interna]] | 11 | 3 | 0 | — |
| [[score_riesgo_incumplimiento]] | 8 | 2 | 0 | — |
| [[alerta_temprana]] | 8 | 2 | 0 | — |
| [[estrategia_cobranza]] | 12 | 0 | 1 | — |
| [[gestion_cobranza]] | 12 | 3 | 3 | — |
| [[accion_cobranza]] | 11 | 3 | 0 | — |
| [[promesa_pago]] | 10 | 2 | 0 | — |
| [[acuerdo_quita]] | 10 | 3 | 0 | — |
| [[cobertura_incumplimiento]] | 16 | 7 | 2 | muy conectada |
| [[deuda_participante]] | 18 | 5 | 4 | muy conectada |
| [[subrogacion]] | 8 | 2 | 0 | — |
| [[abono_recuperacion]] | 13 | 5 | 0 | append-only |
| [[castigo_deuda]] | 9 | 3 | 0 | — |
| [[aval_participante]] | 12 | 4 | 1 | — |
| [[ejecucion_aval]] | 10 | 4 | 0 | — |
| [[politica_sancion]] | 8 | 1 | 3 | — |
| [[matriz_sancion]] | 10 | 1 | 1 | — |
| [[sancion]] | 16 | 6 | 1 | — |
| [[apelacion_sancion]] | 12 | 3 | 0 | — |
| [[reemplazo_participante]] | 12 | 6 | 1 | — |
| [[candidato_reemplazo]] | 7 | 2 | 0 | — |
| [[plan_contingencia]] | 11 | 2 | 0 | — |
| [[disolucion_anticipada]] | 11 | 2 | 1 | — |
| [[liquidacion_participante]] | 8 | 2 | 0 | — |

## 09 — Auditoría, Reportes y Cumplimiento

> Poder demostrar todo lo anterior ante un reclamo o un regulador · [[09_auditoria_reportes|ficha de negocio]] · [[_Entidades 09|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[bitacora_evento]] | 21 | 3 | 0 | append-only |
| [[registro_acceso_datos]] | 10 | 2 | 0 | append-only |
| [[politica_retencion]] | 7 | 0 | 0 | — |
| [[definicion_reporte]] | 11 | 0 | 2 | — |
| [[ejecucion_reporte]] | 12 | 3 | 1 | — |
| [[exportacion_reporte]] | 11 | 1 | 0 | — |
| [[programacion_reporte]] | 10 | 1 | 0 | — |
| [[indicador_kpi]] | 11 | 0 | 0 | — |
| [[regla_cumplimiento]] | 10 | 0 | 1 | — |
| [[alerta_cumplimiento]] | 15 | 5 | 0 | — |
| [[reporte_operacion_sospechosa]] | 10 | 2 | 2 | — |
| [[lista_restrictiva_externa]] | 5 | 0 | 1 | — |
| [[coincidencia_lista]] | 8 | 3 | 0 | — |
| [[umbral_operativo]] | 6 | 0 | 0 | — |
| [[solicitud_datos_personales]] | 10 | 2 | 1 | — |
| [[proceso_anonimizacion]] | 8 | 2 | 0 | — |
| [[ticket_soporte]] | 13 | 2 | 1 | — |
| [[incidente_operativo]] | 13 | 0 | 3 | — |

## 10 — Billetera, Custodia y Dinero Electrónico

> El saldo no se guarda: se deriva, y todos los días cuadra contra el banco · [[10_billetera_custodia|ficha de negocio]] · [[_Entidades 10|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[politica_billetera]] | 11 | 1 | 1 | — |
| [[cuenta_billetera]] | 17 | 4 | 14 | muy conectada |
| [[saldo_diario_billetera]] | 9 | 1 | 0 | append-only |
| [[transaccion_billetera]] | 20 | 5 | 14 | append-only, muy conectada |
| [[movimiento_billetera]] | 10 | 2 | 0 | append-only |
| [[retencion_saldo]] | 12 | 3 | 2 | — |
| [[reverso_transaccion]] | 10 | 3 | 0 | — |
| [[instrumento_fondeo]] | 16 | 1 | 2 | — |
| [[punto_atencion]] | 12 | 1 | 2 | — |
| [[arqueo_punto_atencion]] | 13 | 2 | 0 | — |
| [[orden_recarga]] | 17 | 6 | 0 | — |
| [[orden_retiro]] | 20 | 7 | 1 | muy conectada |
| [[transferencia_p2p]] | 11 | 5 | 0 | — |
| [[cuenta_custodia]] | 14 | 0 | 2 | — |
| [[movimiento_custodia]] | 11 | 2 | 0 | append-only |
| [[conciliacion_custodia]] | 13 | 3 | 1 | — |
| [[descuadre_custodia]] | 12 | 3 | 0 | — |
| [[limite_operativo_billetera]] | 11 | 0 | 1 | — |
| [[consumo_limite]] | 8 | 2 | 0 | — |
| [[respuesta_idempotente]] | 9 | 1 | 0 | — |
| [[regla_antifraude]] | 10 | 1 | 0 | — |
| [[evaluacion_antifraude]] | 11 | 3 | 0 | — |
| [[bloqueo_saldo]] | 15 | 3 | 1 | — |
| [[estado_cuenta_billetera]] | 13 | 1 | 0 | — |
| [[certificado_saldo]] | 10 | 2 | 0 | — |
| [[solicitud_cierre_billetera]] | 10 | 3 | 0 | — |

## 11 — Tarifas, Comisiones, Impuestos y Facturación

> La política de cobro es dato, no código: se cambia con un seeder · [[11_tarifas_comisiones|ficha de negocio]] · [[_Entidades 11|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[catalogo_hecho_generador]] | 8 | 0 | 1 | — |
| [[tarifario]] | 15 | 2 | 9 | muy conectada |
| [[politica_redondeo]] | 6 | 0 | 1 | — |
| [[concepto_tarifa]] | 22 | 4 | 4 | muy conectada |
| [[regla_tarifa]] | 12 | 1 | 0 | — |
| [[segmento_comercial]] | 6 | 0 | 2 | — |
| [[asignacion_tarifario]] | 11 | 5 | 0 | — |
| [[tarifa_congelada_grupo]] | 8 | 3 | 0 | — |
| [[simulacion_tarifa]] | 8 | 2 | 0 | — |
| [[cambio_tarifario]] | 12 | 3 | 0 | — |
| [[cotizacion_comision]] | 15 | 2 | 1 | — |
| [[devengo_comision]] | 20 | 7 | 6 | append-only, muy conectada |
| [[cargo_comision]] | 12 | 4 | 0 | — |
| [[exencion_comision]] | 14 | 5 | 0 | — |
| [[campana_promocional]] | 11 | 1 | 1 | — |
| [[aplicacion_promocion]] | 5 | 2 | 0 | — |
| [[devolucion_comision]] | 12 | 4 | 2 | — |
| [[cuenta_por_cobrar_comision]] | 10 | 3 | 0 | — |
| [[impuesto]] | 9 | 1 | 1 | — |
| [[calculo_impuesto]] | 8 | 2 | 0 | — |
| [[datos_facturacion]] | 9 | 1 | 1 | — |
| [[lote_envio_sin]] | 8 | 0 | 1 | — |
| [[evento_significativo_sin]] | 14 | 1 | 1 | — |
| [[factura_electronica]] | 26 | 5 | 2 | — |
| [[nota_credito_debito]] | 9 | 2 | 0 | — |
| [[liquidacion_ingresos]] | 17 | 2 | 1 | — |
| [[costo_proveedor_operacion]] | 12 | 3 | 0 | — |

## 12 — Cumplimiento Regulatorio y Consumidor Financiero

> Que una inspección se responda con consultas, no armando carpetas · [[12_cumplimiento_asfi|ficha de negocio]] · [[_Entidades 12|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[matriz_riesgo_lft]] | 10 | 1 | 2 | — |
| [[factor_riesgo_evaluado]] | 8 | 2 | 0 | — |
| [[calificacion_riesgo_cliente]] | 13 | 3 | 2 | — |
| [[debida_diligencia]] | 14 | 5 | 0 | — |
| [[perfil_transaccional]] | 12 | 1 | 1 | — |
| [[desvio_perfil]] | 12 | 3 | 0 | — |
| [[declaracion_pep]] | 12 | 2 | 0 | — |
| [[beneficiario_final]] | 8 | 1 | 0 | — |
| [[declaracion_origen_fondos]] | 12 | 3 | 1 | — |
| [[revision_periodica_kyc]] | 8 | 3 | 0 | — |
| [[expediente_cliente]] | 9 | 2 | 0 | — |
| [[regla_monitoreo_lft]] | 14 | 1 | 1 | — |
| [[alerta_monitoreo_lft]] | 14 | 6 | 1 | — |
| [[caso_investigacion_lft]] | 15 | 4 | 1 | — |
| [[umbral_reporte_uif]] | 13 | 0 | 1 | — |
| [[registro_operacion_relevante]] | 25 | 6 | 1 | append-only |
| [[catalogo_reporte_regulatorio]] | 10 | 0 | 1 | — |
| [[reporte_regulatorio]] | 15 | 4 | 2 | — |
| [[envio_regulatorio]] | 10 | 2 | 1 | — |
| [[observacion_regulatoria]] | 13 | 2 | 0 | — |
| [[requerimiento_autoridad]] | 14 | 3 | 0 | — |
| [[contrato_adhesion]] | 13 | 1 | 1 | — |
| [[aceptacion_contrato]] | 9 | 4 | 0 | — |
| [[documento_publicado]] | 9 | 1 | 0 | — |
| [[punto_reclamo]] | 7 | 1 | 1 | — |
| [[reclamo_cliente]] | 25 | 5 | 2 | — |
| [[instancia_reclamo]] | 9 | 1 | 0 | — |
| [[evento_riesgo_operativo]] | 18 | 2 | 2 | append-only |
| [[control_interno]] | 10 | 1 | 1 | — |
| [[prueba_control]] | 9 | 2 | 0 | — |
| [[hallazgo_auditoria]] | 10 | 1 | 1 | — |
| [[plan_accion_riesgo]] | 10 | 3 | 0 | — |
| [[evaluacion_riesgo_producto]] | 10 | 1 | 0 | — |
| [[oficial_cumplimiento]] | 8 | 1 | 0 | — |
| [[capacitacion_cumplimiento]] | 10 | 1 | 0 | — |
| [[licencia_regulatoria]] | 14 | 1 | 1 | — |
| [[entorno_prueba_regulado]] | 11 | 1 | 0 | — |
| [[comite_gobierno]] | 6 | 0 | 1 | — |
| [[acta_comite]] | 11 | 2 | 3 | append-only |
| [[politica_interna]] | 13 | 2 | 1 | — |
| [[designacion_regulatoria]] | 10 | 2 | 0 | — |
| [[activo_informacion]] | 14 | 3 | 1 | — |
| [[incidente_seguridad]] | 18 | 4 | 0 | — |
| [[plan_continuidad]] | 10 | 2 | 1 | — |
| [[prueba_continuidad]] | 11 | 3 | 0 | — |
| [[contrato_tercero]] | 18 | 1 | 2 | — |
| [[evaluacion_tercero]] | 9 | 2 | 0 | — |

## 13 — Contabilidad Financiera y ERP

> Que cerrar un mes no dependa de un Excel armado a mano · [[13_contabilidad_erp|ficha de negocio]] · [[_Entidades 13|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[ejercicio_fiscal]] | 7 | 1 | 2 | — |
| [[periodo_contable]] | 6 | 1 | 5 | — |
| [[cierre_periodo_contable]] | 8 | 2 | 0 | append-only |
| [[centro_costo]] | 5 | 0 | 4 | — |
| [[presupuesto]] | 7 | 3 | 1 | — |
| [[partida_presupuestaria]] | 7 | 3 | 0 | — |
| [[tercero_comercial]] | 9 | 1 | 3 | — |
| [[orden_compra]] | 10 | 3 | 1 | — |
| [[factura_proveedor]] | 14 | 5 | 2 | append-only |
| [[pago_a_proveedor]] | 8 | 3 | 0 | append-only |
| [[cuenta_por_cobrar]] | 10 | 1 | 2 | append-only |
| [[cobro_cuenta_por_cobrar]] | 7 | 2 | 0 | append-only |
| [[categoria_activo_fijo]] | 8 | 3 | 1 | — |
| [[activo_fijo]] | 13 | 3 | 1 | — |
| [[depreciacion_activo]] | 7 | 3 | 0 | append-only |
| [[asiento_plantilla]] | 7 | 1 | 1 | — |
| [[linea_plantilla_asiento]] | 6 | 2 | 0 | — |
| [[estado_financiero_generado]] | 7 | 2 | 0 | append-only |

## 14 — Publicidad y Campañas

> Que un partner se anuncie dentro de la app sin inventar un segundo cobro · [[14_publicidad_campanas|ficha de negocio]] · [[_Entidades 14|índice del módulo]]

| Tabla | Columnas | Sal. | Ent. | Notas |
| --- | --: | --: | --: | --- |
| [[socio_comercial]] | 9 | 1 | 1 | — |
| [[anunciante]] | 7 | 2 | 2 | — |
| [[cuenta_publicitaria]] | 7 | 1 | 2 | — |
| [[campana_publicitaria]] | 11 | 2 | 1 | — |
| [[segmento_audiencia]] | 6 | 1 | 1 | — |
| [[espacio_publicitario]] | 6 | 0 | 1 | — |
| [[conjunto_anuncios]] | 10 | 3 | 1 | — |
| [[pieza_creativa]] | 8 | 1 | 2 | — |
| [[revision_creativa]] | 6 | 2 | 0 | — |
| [[anuncio]] | 6 | 2 | 1 | — |
| [[impresion_anuncio]] | 6 | 2 | 2 | append-only |
| [[clic_anuncio]] | 6 | 2 | 1 | append-only |
| [[conversion_anuncio]] | 6 | 2 | 0 | append-only |
| [[factura_publicidad]] | 9 | 3 | 0 | append-only |

## Tablas append-only

No admiten `UPDATE` ni `DELETE`; se corrigen con el movimiento inverso:

[[abono_recuperacion]] · [[acta_comite]] · [[asiento_contable]] · [[bitacora_evento]] · [[cierre_periodo_contable]] · [[clic_anuncio]] · [[cobro_cuenta_por_cobrar]] · [[conversion_anuncio]] · [[cuenta_por_cobrar]] · [[depreciacion_activo]] · [[devengo_comision]] · [[estado_financiero_generado]] · [[evento_reputacion]] · [[evento_riesgo_operativo]] · [[factura_proveedor]] · [[factura_publicidad]] · [[historial_estado_incumplimiento]] · [[impresion_anuncio]] · [[movimiento_billetera]] · [[movimiento_contable]] · [[movimiento_custodia]] · [[movimiento_fondo]] · [[pago_a_proveedor]] · [[registro_acceso_datos]] · [[registro_incumplimiento]] · [[registro_operacion_relevante]] · [[registro_sellado]] · [[saldo_diario_billetera]] · [[transaccion_billetera]]

