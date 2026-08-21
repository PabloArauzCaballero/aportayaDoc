-- Aplica el esquema completo en orden.
--   psql -v ON_ERROR_STOP=1 -f sql/aplicar.sql
-- Generado por scripts/generar_ddl.py — no editar a mano.

\set ON_ERROR_STOP on
BEGIN;

-- El DDL califica cada tabla con su esquema. El search_path existe para
-- el SQL escrito a mano que viene despues (restricciones, semillas,
-- prueba de humo), que referencia las tablas por nombre simple.
-- Los 307 nombres de tabla son unicos, asi que resuelve sin ambiguedad.
SET search_path TO aportes, auditoria, cumplimiento, entregas, erp, garantia, grupos, identidad, notificaciones, nucleo_financiero, organizador, publicidad, tarifas, transparencia, catalogo, comun, public;

-- 1) Base
\ir 00_base/00_extensiones.sql
\ir 00_base/01_roles.sql
-- Un esquema y un rol por servicio (ADR-017): la frontera entre
-- servicios es el GRANT, no una convención de nombres.
\ir 00_base/02_esquemas.sql

-- 2) Tablas (una por archivo, agrupadas por módulo)
--    módulo 01 — Identidad, Usuarios y Seguridad
\ir 10_tablas/01_identidad_usuarios/usuario.sql
\ir 10_tablas/01_identidad_usuarios/direccion_usuario.sql
\ir 10_tablas/01_identidad_usuarios/perfil_financiero.sql
\ir 10_tablas/01_identidad_usuarios/credencial_acceso.sql
\ir 10_tablas/01_identidad_usuarios/historial_credencial.sql
\ir 10_tablas/01_identidad_usuarios/politica_token.sql
\ir 10_tablas/01_identidad_usuarios/token_verificacion.sql
\ir 10_tablas/01_identidad_usuarios/intento_validacion_token.sql
\ir 10_tablas/01_identidad_usuarios/factor_mfa.sql
\ir 10_tablas/01_identidad_usuarios/dispositivo.sql
\ir 10_tablas/01_identidad_usuarios/sesion.sql
\ir 10_tablas/01_identidad_usuarios/intento_autenticacion.sql
\ir 10_tablas/01_identidad_usuarios/bloqueo_cuenta.sql
\ir 10_tablas/01_identidad_usuarios/restriccion_usuario.sql
\ir 10_tablas/01_identidad_usuarios/documento_identidad.sql
\ir 10_tablas/01_identidad_usuarios/verificacion_kyc.sql
\ir 10_tablas/01_identidad_usuarios/referencia_personal.sql
\ir 10_tablas/01_identidad_usuarios/rol.sql
\ir 10_tablas/01_identidad_usuarios/permiso.sql
\ir 10_tablas/01_identidad_usuarios/rol_permiso.sql
\ir 10_tablas/01_identidad_usuarios/asignacion_rol.sql
\ir 10_tablas/01_identidad_usuarios/consentimiento.sql
\ir 10_tablas/01_identidad_usuarios/preferencia_notificacion.sql
\ir 10_tablas/01_identidad_usuarios/reputacion_usuario.sql
\ir 10_tablas/01_identidad_usuarios/solicitud_baja.sql
--    módulo 02 — Grupos, Cupos, Turnos y Gobernanza
\ir 10_tablas/02_grupos_turnos/grupo.sql
\ir 10_tablas/02_grupos_turnos/configuracion_grupo.sql
\ir 10_tablas/02_grupos_turnos/reglamento_grupo.sql
\ir 10_tablas/02_grupos_turnos/aceptacion_reglamento.sql
\ir 10_tablas/02_grupos_turnos/historial_estado_grupo.sql
\ir 10_tablas/02_grupos_turnos/participante.sql
\ir 10_tablas/02_grupos_turnos/cupo.sql
\ir 10_tablas/02_grupos_turnos/traspaso_cupo.sql
\ir 10_tablas/02_grupos_turnos/solicitud_retiro.sql
\ir 10_tablas/02_grupos_turnos/solicitud_ingreso.sql
\ir 10_tablas/02_grupos_turnos/invitacion.sql
\ir 10_tablas/02_grupos_turnos/periodo.sql
\ir 10_tablas/02_grupos_turnos/turno.sql
\ir 10_tablas/02_grupos_turnos/sorteo_turnos.sql
\ir 10_tablas/02_grupos_turnos/solicitud_permuta.sql
\ir 10_tablas/02_grupos_turnos/dia_no_habil.sql
\ir 10_tablas/02_grupos_turnos/postulacion_emparejamiento.sql
\ir 10_tablas/02_grupos_turnos/criterio_emparejamiento.sql
\ir 10_tablas/02_grupos_turnos/propuesta_grupo.sql
\ir 10_tablas/02_grupos_turnos/propuesta_postulacion.sql
\ir 10_tablas/02_grupos_turnos/acuerdo.sql
\ir 10_tablas/02_grupos_turnos/voto_participante.sql
--    módulo 03 — Aportes, Pagos QR y Conciliación
\ir 10_tablas/03_aportes_pagos_qr/politica_mora.sql
\ir 10_tablas/03_aportes_pagos_qr/obligacion_aporte.sql
\ir 10_tablas/03_aportes_pagos_qr/plan_regularizacion.sql
\ir 10_tablas/03_aportes_pagos_qr/proveedor_pago.sql
\ir 10_tablas/03_aportes_pagos_qr/orden_cobro.sql
\ir 10_tablas/03_aportes_pagos_qr/qr_cobro.sql
\ir 10_tablas/03_aportes_pagos_qr/enlace_pago_rapido.sql
\ir 10_tablas/03_aportes_pagos_qr/intento_pago.sql
\ir 10_tablas/03_aportes_pagos_qr/pago.sql
\ir 10_tablas/03_aportes_pagos_qr/comprobante_manual.sql
\ir 10_tablas/03_aportes_pagos_qr/constancia_pago.sql
\ir 10_tablas/03_aportes_pagos_qr/reembolso.sql
\ir 10_tablas/03_aportes_pagos_qr/disputa_pago.sql
\ir 10_tablas/03_aportes_pagos_qr/extracto_bancario.sql
\ir 10_tablas/03_aportes_pagos_qr/movimiento_bancario.sql
\ir 10_tablas/03_aportes_pagos_qr/conciliacion.sql
\ir 10_tablas/03_aportes_pagos_qr/excepcion_conciliacion.sql
\ir 10_tablas/03_aportes_pagos_qr/webhook_pasarela.sql
\ir 10_tablas/03_aportes_pagos_qr/tipo_cambio.sql
\ir 10_tablas/03_aportes_pagos_qr/cuenta_contable.sql
\ir 10_tablas/03_aportes_pagos_qr/asiento_contable.sql
\ir 10_tablas/03_aportes_pagos_qr/movimiento_contable.sql
\ir 10_tablas/03_aportes_pagos_qr/cierre_diario.sql
--    módulo 04 — Entregas de Fondo
\ir 10_tablas/04_entregas_fondo/entrega_fondo.sql
\ir 10_tablas/04_entregas_fondo/deduccion_entrega.sql
\ir 10_tablas/04_entregas_fondo/regla_entrega.sql
\ir 10_tablas/04_entregas_fondo/validacion_pre_entrega.sql
\ir 10_tablas/04_entregas_fondo/cuenta_bancaria_beneficiario.sql
\ir 10_tablas/04_entregas_fondo/orden_desembolso.sql
\ir 10_tablas/04_entregas_fondo/intento_desembolso.sql
\ir 10_tablas/04_entregas_fondo/confirmacion_recepcion.sql
\ir 10_tablas/04_entregas_fondo/incidencia_entrega.sql
\ir 10_tablas/04_entregas_fondo/historial_estado_entrega.sql
--    módulo 05 — Notificaciones y Comunicaciones
\ir 10_tablas/05_notificaciones/evento_notificable.sql
\ir 10_tablas/05_notificaciones/plantilla_mensaje.sql
\ir 10_tablas/05_notificaciones/version_plantilla.sql
\ir 10_tablas/05_notificaciones/proveedor_mensajeria.sql
\ir 10_tablas/05_notificaciones/canal_vinculado.sql
\ir 10_tablas/05_notificaciones/lista_supresion.sql
\ir 10_tablas/05_notificaciones/notificacion.sql
\ir 10_tablas/05_notificaciones/envio_notificacion.sql
\ir 10_tablas/05_notificaciones/evento_entrega_mensaje.sql
\ir 10_tablas/05_notificaciones/cola_envio.sql
\ir 10_tablas/05_notificaciones/cola_muerta.sql
\ir 10_tablas/05_notificaciones/enlace_pago_notificado.sql
\ir 10_tablas/05_notificaciones/respuesta_entrante.sql
\ir 10_tablas/05_notificaciones/programacion_recordatorio.sql
\ir 10_tablas/05_notificaciones/bandeja_entrada.sql
--    módulo 06 — Transparencia y Reputación
\ir 10_tablas/06_transparencia_reputacion/modelo_scoring.sql
\ir 10_tablas/06_transparencia_reputacion/peso_factor.sql
\ir 10_tablas/06_transparencia_reputacion/regla_impacto_evento.sql
\ir 10_tablas/06_transparencia_reputacion/evento_reputacion.sql
\ir 10_tablas/06_transparencia_reputacion/puntaje_reputacion.sql
\ir 10_tablas/06_transparencia_reputacion/componente_score.sql
\ir 10_tablas/06_transparencia_reputacion/snapshot_reputacion.sql
\ir 10_tablas/06_transparencia_reputacion/certificado_reputacion.sql
\ir 10_tablas/06_transparencia_reputacion/insignia_logro.sql
\ir 10_tablas/06_transparencia_reputacion/insignia_otorgada.sql
\ir 10_tablas/06_transparencia_reputacion/metrica_grupo.sql
\ir 10_tablas/06_transparencia_reputacion/bloque_transparencia.sql
\ir 10_tablas/06_transparencia_reputacion/registro_sellado.sql
\ir 10_tablas/06_transparencia_reputacion/verificacion_publica.sql
\ir 10_tablas/06_transparencia_reputacion/resena_participante.sql
\ir 10_tablas/06_transparencia_reputacion/alerta_riesgo.sql
--    módulo 07 — Organizador y Automatización
\ir 10_tablas/07_organizador_automatizacion/organizador.sql
\ir 10_tablas/07_organizador_automatizacion/solicitud_organizador.sql
\ir 10_tablas/07_organizador_automatizacion/requisito_habilitacion.sql
\ir 10_tablas/07_organizador_automatizacion/capacitacion_organizador.sql
\ir 10_tablas/07_organizador_automatizacion/contrato_organizador.sql
\ir 10_tablas/07_organizador_automatizacion/evaluacion_desempeno.sql
\ir 10_tablas/07_organizador_automatizacion/metrica_organizador.sql
\ir 10_tablas/07_organizador_automatizacion/sancion_organizador.sql
\ir 10_tablas/07_organizador_automatizacion/apelacion_sancion_org.sql
\ir 10_tablas/07_organizador_automatizacion/regla_automatizacion.sql
\ir 10_tablas/07_organizador_automatizacion/tarea_automatizada.sql
\ir 10_tablas/07_organizador_automatizacion/ejecucion_tarea.sql
--    módulo 08 — Garantía, Incumplimiento, Cobranza y Sanciones
\ir 10_tablas/08_garantia_incumplimiento/politica_cobertura.sql
\ir 10_tablas/08_garantia_incumplimiento/fondo_garantia.sql
\ir 10_tablas/08_garantia_incumplimiento/movimiento_fondo.sql
\ir 10_tablas/08_garantia_incumplimiento/devolucion_fondo.sql
\ir 10_tablas/08_garantia_incumplimiento/registro_incumplimiento.sql
\ir 10_tablas/08_garantia_incumplimiento/evidencia_incumplimiento.sql
\ir 10_tablas/08_garantia_incumplimiento/historial_estado_incumplimiento.sql
\ir 10_tablas/08_garantia_incumplimiento/descargo_participante.sql
\ir 10_tablas/08_garantia_incumplimiento/historial_incumplimiento_usuario.sql
\ir 10_tablas/08_garantia_incumplimiento/lista_restriccion_interna.sql
\ir 10_tablas/08_garantia_incumplimiento/score_riesgo_incumplimiento.sql
\ir 10_tablas/08_garantia_incumplimiento/alerta_temprana.sql
\ir 10_tablas/08_garantia_incumplimiento/estrategia_cobranza.sql
\ir 10_tablas/08_garantia_incumplimiento/gestion_cobranza.sql
\ir 10_tablas/08_garantia_incumplimiento/accion_cobranza.sql
\ir 10_tablas/08_garantia_incumplimiento/promesa_pago.sql
\ir 10_tablas/08_garantia_incumplimiento/acuerdo_quita.sql
\ir 10_tablas/08_garantia_incumplimiento/cobertura_incumplimiento.sql
\ir 10_tablas/08_garantia_incumplimiento/deuda_participante.sql
\ir 10_tablas/08_garantia_incumplimiento/subrogacion.sql
\ir 10_tablas/08_garantia_incumplimiento/abono_recuperacion.sql
\ir 10_tablas/08_garantia_incumplimiento/castigo_deuda.sql
\ir 10_tablas/08_garantia_incumplimiento/aval_participante.sql
\ir 10_tablas/08_garantia_incumplimiento/ejecucion_aval.sql
\ir 10_tablas/08_garantia_incumplimiento/politica_sancion.sql
\ir 10_tablas/08_garantia_incumplimiento/matriz_sancion.sql
\ir 10_tablas/08_garantia_incumplimiento/sancion.sql
\ir 10_tablas/08_garantia_incumplimiento/apelacion_sancion.sql
\ir 10_tablas/08_garantia_incumplimiento/reemplazo_participante.sql
\ir 10_tablas/08_garantia_incumplimiento/candidato_reemplazo.sql
\ir 10_tablas/08_garantia_incumplimiento/plan_contingencia.sql
\ir 10_tablas/08_garantia_incumplimiento/disolucion_anticipada.sql
\ir 10_tablas/08_garantia_incumplimiento/liquidacion_participante.sql
--    módulo 09 — Auditoría, Reportes y Cumplimiento
\ir 10_tablas/09_auditoria_reportes/bitacora_evento.sql
\ir 10_tablas/09_auditoria_reportes/registro_acceso_datos.sql
\ir 10_tablas/09_auditoria_reportes/politica_retencion.sql
\ir 10_tablas/09_auditoria_reportes/definicion_reporte.sql
\ir 10_tablas/09_auditoria_reportes/ejecucion_reporte.sql
\ir 10_tablas/09_auditoria_reportes/exportacion_reporte.sql
\ir 10_tablas/09_auditoria_reportes/programacion_reporte.sql
\ir 10_tablas/09_auditoria_reportes/indicador_kpi.sql
\ir 10_tablas/09_auditoria_reportes/regla_cumplimiento.sql
\ir 10_tablas/09_auditoria_reportes/alerta_cumplimiento.sql
\ir 10_tablas/09_auditoria_reportes/reporte_operacion_sospechosa.sql
\ir 10_tablas/09_auditoria_reportes/lista_restrictiva_externa.sql
\ir 10_tablas/09_auditoria_reportes/coincidencia_lista.sql
\ir 10_tablas/09_auditoria_reportes/umbral_operativo.sql
\ir 10_tablas/09_auditoria_reportes/solicitud_datos_personales.sql
\ir 10_tablas/09_auditoria_reportes/proceso_anonimizacion.sql
\ir 10_tablas/09_auditoria_reportes/ticket_soporte.sql
\ir 10_tablas/09_auditoria_reportes/incidente_operativo.sql
--    módulo 10 — Billetera, Custodia y Dinero Electrónico
\ir 10_tablas/10_billetera_custodia/politica_billetera.sql
\ir 10_tablas/10_billetera_custodia/cuenta_billetera.sql
\ir 10_tablas/10_billetera_custodia/saldo_diario_billetera.sql
\ir 10_tablas/10_billetera_custodia/transaccion_billetera.sql
\ir 10_tablas/10_billetera_custodia/movimiento_billetera.sql
\ir 10_tablas/10_billetera_custodia/retencion_saldo.sql
\ir 10_tablas/10_billetera_custodia/reverso_transaccion.sql
\ir 10_tablas/10_billetera_custodia/instrumento_fondeo.sql
\ir 10_tablas/10_billetera_custodia/orden_recarga.sql
\ir 10_tablas/10_billetera_custodia/orden_retiro.sql
\ir 10_tablas/10_billetera_custodia/transferencia_p2p.sql
\ir 10_tablas/10_billetera_custodia/cuenta_custodia.sql
\ir 10_tablas/10_billetera_custodia/movimiento_custodia.sql
\ir 10_tablas/10_billetera_custodia/conciliacion_custodia.sql
\ir 10_tablas/10_billetera_custodia/descuadre_custodia.sql
\ir 10_tablas/10_billetera_custodia/limite_operativo_billetera.sql
\ir 10_tablas/10_billetera_custodia/consumo_limite.sql
\ir 10_tablas/10_billetera_custodia/respuesta_idempotente.sql
\ir 10_tablas/10_billetera_custodia/regla_antifraude.sql
\ir 10_tablas/10_billetera_custodia/evaluacion_antifraude.sql
\ir 10_tablas/10_billetera_custodia/bloqueo_saldo.sql
\ir 10_tablas/10_billetera_custodia/estado_cuenta_billetera.sql
\ir 10_tablas/10_billetera_custodia/certificado_saldo.sql
\ir 10_tablas/10_billetera_custodia/solicitud_cierre_billetera.sql
--    módulo 11 — Tarifas, Comisiones, Impuestos y Facturación
\ir 10_tablas/11_tarifas_comisiones/catalogo_hecho_generador.sql
\ir 10_tablas/11_tarifas_comisiones/tarifario.sql
\ir 10_tablas/11_tarifas_comisiones/politica_redondeo.sql
\ir 10_tablas/11_tarifas_comisiones/concepto_tarifa.sql
\ir 10_tablas/11_tarifas_comisiones/regla_tarifa.sql
\ir 10_tablas/11_tarifas_comisiones/segmento_comercial.sql
\ir 10_tablas/11_tarifas_comisiones/asignacion_tarifario.sql
\ir 10_tablas/11_tarifas_comisiones/tarifa_congelada_grupo.sql
\ir 10_tablas/11_tarifas_comisiones/simulacion_tarifa.sql
\ir 10_tablas/11_tarifas_comisiones/cambio_tarifario.sql
\ir 10_tablas/11_tarifas_comisiones/cotizacion_comision.sql
\ir 10_tablas/11_tarifas_comisiones/devengo_comision.sql
\ir 10_tablas/11_tarifas_comisiones/cargo_comision.sql
\ir 10_tablas/11_tarifas_comisiones/exencion_comision.sql
\ir 10_tablas/11_tarifas_comisiones/campana_promocional.sql
\ir 10_tablas/11_tarifas_comisiones/aplicacion_promocion.sql
\ir 10_tablas/11_tarifas_comisiones/devolucion_comision.sql
\ir 10_tablas/11_tarifas_comisiones/cuenta_por_cobrar_comision.sql
\ir 10_tablas/11_tarifas_comisiones/impuesto.sql
\ir 10_tablas/11_tarifas_comisiones/calculo_impuesto.sql
\ir 10_tablas/11_tarifas_comisiones/datos_facturacion.sql
\ir 10_tablas/11_tarifas_comisiones/lote_envio_sin.sql
\ir 10_tablas/11_tarifas_comisiones/evento_significativo_sin.sql
\ir 10_tablas/11_tarifas_comisiones/factura_electronica.sql
\ir 10_tablas/11_tarifas_comisiones/nota_credito_debito.sql
\ir 10_tablas/11_tarifas_comisiones/liquidacion_ingresos.sql
\ir 10_tablas/11_tarifas_comisiones/costo_proveedor_operacion.sql
--    módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
\ir 10_tablas/12_cumplimiento_asfi/matriz_riesgo_lft.sql
\ir 10_tablas/12_cumplimiento_asfi/factor_riesgo_evaluado.sql
\ir 10_tablas/12_cumplimiento_asfi/calificacion_riesgo_cliente.sql
\ir 10_tablas/12_cumplimiento_asfi/debida_diligencia.sql
\ir 10_tablas/12_cumplimiento_asfi/perfil_transaccional.sql
\ir 10_tablas/12_cumplimiento_asfi/desvio_perfil.sql
\ir 10_tablas/12_cumplimiento_asfi/declaracion_pep.sql
\ir 10_tablas/12_cumplimiento_asfi/beneficiario_final.sql
\ir 10_tablas/12_cumplimiento_asfi/declaracion_origen_fondos.sql
\ir 10_tablas/12_cumplimiento_asfi/revision_periodica_kyc.sql
\ir 10_tablas/12_cumplimiento_asfi/expediente_cliente.sql
\ir 10_tablas/12_cumplimiento_asfi/regla_monitoreo_lft.sql
\ir 10_tablas/12_cumplimiento_asfi/alerta_monitoreo_lft.sql
\ir 10_tablas/12_cumplimiento_asfi/caso_investigacion_lft.sql
\ir 10_tablas/12_cumplimiento_asfi/umbral_reporte_uif.sql
\ir 10_tablas/12_cumplimiento_asfi/registro_operacion_relevante.sql
\ir 10_tablas/12_cumplimiento_asfi/catalogo_reporte_regulatorio.sql
\ir 10_tablas/12_cumplimiento_asfi/reporte_regulatorio.sql
\ir 10_tablas/12_cumplimiento_asfi/envio_regulatorio.sql
\ir 10_tablas/12_cumplimiento_asfi/observacion_regulatoria.sql
\ir 10_tablas/12_cumplimiento_asfi/requerimiento_autoridad.sql
\ir 10_tablas/12_cumplimiento_asfi/contrato_adhesion.sql
\ir 10_tablas/12_cumplimiento_asfi/aceptacion_contrato.sql
\ir 10_tablas/12_cumplimiento_asfi/documento_publicado.sql
\ir 10_tablas/12_cumplimiento_asfi/punto_reclamo.sql
\ir 10_tablas/12_cumplimiento_asfi/reclamo_cliente.sql
\ir 10_tablas/12_cumplimiento_asfi/instancia_reclamo.sql
\ir 10_tablas/12_cumplimiento_asfi/evento_riesgo_operativo.sql
\ir 10_tablas/12_cumplimiento_asfi/control_interno.sql
\ir 10_tablas/12_cumplimiento_asfi/prueba_control.sql
\ir 10_tablas/12_cumplimiento_asfi/hallazgo_auditoria.sql
\ir 10_tablas/12_cumplimiento_asfi/plan_accion_riesgo.sql
\ir 10_tablas/12_cumplimiento_asfi/evaluacion_riesgo_producto.sql
\ir 10_tablas/12_cumplimiento_asfi/oficial_cumplimiento.sql
\ir 10_tablas/12_cumplimiento_asfi/capacitacion_cumplimiento.sql
\ir 10_tablas/12_cumplimiento_asfi/licencia_regulatoria.sql
\ir 10_tablas/12_cumplimiento_asfi/entorno_prueba_regulado.sql
\ir 10_tablas/12_cumplimiento_asfi/comite_gobierno.sql
\ir 10_tablas/12_cumplimiento_asfi/acta_comite.sql
\ir 10_tablas/12_cumplimiento_asfi/politica_interna.sql
\ir 10_tablas/12_cumplimiento_asfi/designacion_regulatoria.sql
\ir 10_tablas/12_cumplimiento_asfi/activo_informacion.sql
\ir 10_tablas/12_cumplimiento_asfi/incidente_seguridad.sql
\ir 10_tablas/12_cumplimiento_asfi/plan_continuidad.sql
\ir 10_tablas/12_cumplimiento_asfi/prueba_continuidad.sql
\ir 10_tablas/12_cumplimiento_asfi/contrato_tercero.sql
\ir 10_tablas/12_cumplimiento_asfi/evaluacion_tercero.sql
--    módulo 13 — Contabilidad Financiera y ERP
\ir 10_tablas/13_contabilidad_erp/ejercicio_fiscal.sql
\ir 10_tablas/13_contabilidad_erp/periodo_contable.sql
\ir 10_tablas/13_contabilidad_erp/cierre_periodo_contable.sql
\ir 10_tablas/13_contabilidad_erp/centro_costo.sql
\ir 10_tablas/13_contabilidad_erp/presupuesto.sql
\ir 10_tablas/13_contabilidad_erp/partida_presupuestaria.sql
\ir 10_tablas/13_contabilidad_erp/tercero_comercial.sql
\ir 10_tablas/13_contabilidad_erp/orden_compra.sql
\ir 10_tablas/13_contabilidad_erp/factura_proveedor.sql
\ir 10_tablas/13_contabilidad_erp/pago_a_proveedor.sql
\ir 10_tablas/13_contabilidad_erp/cuenta_por_cobrar.sql
\ir 10_tablas/13_contabilidad_erp/cobro_cuenta_por_cobrar.sql
\ir 10_tablas/13_contabilidad_erp/categoria_activo_fijo.sql
\ir 10_tablas/13_contabilidad_erp/activo_fijo.sql
\ir 10_tablas/13_contabilidad_erp/depreciacion_activo.sql
\ir 10_tablas/13_contabilidad_erp/asiento_plantilla.sql
\ir 10_tablas/13_contabilidad_erp/linea_plantilla_asiento.sql
\ir 10_tablas/13_contabilidad_erp/estado_financiero_generado.sql
--    módulo 14 — Publicidad y Campañas
\ir 10_tablas/14_publicidad_campanas/socio_comercial.sql
\ir 10_tablas/14_publicidad_campanas/anunciante.sql
\ir 10_tablas/14_publicidad_campanas/cuenta_publicitaria.sql
\ir 10_tablas/14_publicidad_campanas/campana_publicitaria.sql
\ir 10_tablas/14_publicidad_campanas/segmento_audiencia.sql
\ir 10_tablas/14_publicidad_campanas/espacio_publicitario.sql
\ir 10_tablas/14_publicidad_campanas/conjunto_anuncios.sql
\ir 10_tablas/14_publicidad_campanas/pieza_creativa.sql
\ir 10_tablas/14_publicidad_campanas/revision_creativa.sql
\ir 10_tablas/14_publicidad_campanas/anuncio.sql
\ir 10_tablas/14_publicidad_campanas/impresion_anuncio.sql
\ir 10_tablas/14_publicidad_campanas/clic_anuncio.sql
\ir 10_tablas/14_publicidad_campanas/conversion_anuncio.sql
\ir 10_tablas/14_publicidad_campanas/factura_publicidad.sql

-- 2b) Infraestructura de mensajería por esquema (ADR-027)
\ir 15_infra/mensajeria.sql

-- 3) Claves foráneas (después de todas las tablas)
\ir 20_claves/01_identidad_usuarios.sql
\ir 20_claves/02_grupos_turnos.sql
\ir 20_claves/03_aportes_pagos_qr.sql
\ir 20_claves/04_entregas_fondo.sql
\ir 20_claves/05_notificaciones.sql
\ir 20_claves/06_transparencia_reputacion.sql
\ir 20_claves/07_organizador_automatizacion.sql
\ir 20_claves/08_garantia_incumplimiento.sql
\ir 20_claves/09_auditoria_reportes.sql
\ir 20_claves/10_billetera_custodia.sql
\ir 20_claves/11_tarifas_comisiones.sql
\ir 20_claves/12_cumplimiento_asfi.sql
\ir 20_claves/13_contabilidad_erp.sql
\ir 20_claves/14_publicidad_campanas.sql

-- 4) Índices y unicidad
\ir 30_indices/01_identidad_usuarios.sql
\ir 30_indices/02_grupos_turnos.sql
\ir 30_indices/03_aportes_pagos_qr.sql
\ir 30_indices/04_entregas_fondo.sql
\ir 30_indices/05_notificaciones.sql
\ir 30_indices/06_transparencia_reputacion.sql
\ir 30_indices/07_organizador_automatizacion.sql
\ir 30_indices/08_garantia_incumplimiento.sql
\ir 30_indices/09_auditoria_reportes.sql
\ir 30_indices/10_billetera_custodia.sql
\ir 30_indices/11_tarifas_comisiones.sql
\ir 30_indices/12_cumplimiento_asfi.sql
\ir 30_indices/13_contabilidad_erp.sql
\ir 30_indices/14_publicidad_campanas.sql

-- 5) Sellado de las tablas append-only
\ir 35_append_only/append_only.sql

-- 6) Reglas de negocio y cumplimiento (catálogo de restricciones)
\ir 40_reglas/restricciones.sql

-- 7) Permisos sobre las tablas ya creadas (invariante 11).
--    Va al final: ALTER DEFAULT PRIVILEGES solo cubre lo que se cree
--    despues, y acá las tablas ya existen.
\ir 00_base/03_permisos.sql

COMMIT;

-- Verificación posterior (no forma parte de la aplicación):
--   psql -f sql/50_verificacion/verificaciones.sql
--   psql -f sql/50_verificacion/prueba_humo.sql
