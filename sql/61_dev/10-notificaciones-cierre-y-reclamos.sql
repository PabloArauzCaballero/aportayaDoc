-- Lo que rodea al dinero y hace auditable la operación: canales y preferencias de contacto, avisos enviados con prueba de entrega, cierre diario cuadrado, conciliación de custodia con encaje, arqueo de efectivo, un reclamo con plazo corriendo y un evento de riesgo operativo.
-- GENERADO desde seeders/dev/10-notificaciones-cierre-y-reclamos.json — no editar a mano.

-- USR000004 se dio de baja de WhatsApp comercial: los avisos de cobranza obligatorios igual le llegan, los comerciales no.
INSERT INTO canal_vinculado (usuario_id, tipo, identificador, verificado, verificado_en, opt_in_en, opt_out_en, motivo_opt_out, ventana_conversacion_hasta, rebotes_consecutivos, estado) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'WHATSAPP', '+59171000001', TRUE, now() - interval '120 days', now() - interval '120 days', NULL, NULL, now() + interval '20 hours', 0, 'ACTIVO'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'WHATSAPP', '+59171000002', TRUE, now() - interval '110 days', now() - interval '110 days', NULL, NULL, NULL, 0, 'ACTIVO'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 'PUSH', 'demo-push-03', TRUE, now() - interval '100 days', now() - interval '100 days', NULL, NULL, NULL, 0, 'ACTIVO'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'WHATSAPP', '+59171000004', TRUE, now() - interval '95 days', now() - interval '95 days', now() - interval '20 days', 'El titular pidió no recibir mensajes comerciales', now() + interval '12 hours', 0, 'ACTIVO'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'WHATSAPP', '+59171000005', TRUE, now() - interval '90 days', now() - interval '90 days', NULL, NULL, NULL, 0, 'ACTIVO'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'CORREO', 'demo6@pasanaku.test', TRUE, now() - interval '85 days', now() - interval '85 days', NULL, NULL, NULL, 0, 'ACTIVO')
ON CONFLICT DO NOTHING;

-- El tope diario y la franja de no molestar valen para lo comercial y lo de cobranza; nunca para un aviso transaccional o de seguridad.
INSERT INTO preferencia_notificacion (usuario_id, canal_primario, canal_respaldo, acepta_whatsapp, acepta_correo, acepta_sms, acepta_push, tope_diario_mensajes, hora_no_molestar_desde, hora_no_molestar_hasta, frecuencia_resumen) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'WHATSAPP', 'PUSH_APP', TRUE, TRUE, TRUE, TRUE, 5, '22:00:00', '07:00:00', 'SEMANAL'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'WHATSAPP', 'SMS', TRUE, TRUE, TRUE, TRUE, 5, '22:00:00', '07:00:00', 'SEMANAL'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 'PUSH_APP', 'WHATSAPP', TRUE, TRUE, FALSE, TRUE, 3, '21:00:00', '08:00:00', 'MENSUAL'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'WHATSAPP', 'SMS', FALSE, TRUE, TRUE, TRUE, 2, '21:00:00', '08:00:00', 'MENSUAL'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'WHATSAPP', 'CORREO', TRUE, TRUE, TRUE, TRUE, 8, NULL, NULL, 'SEMANAL'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'CORREO', 'PUSH_APP', FALSE, TRUE, FALSE, TRUE, 3, '23:00:00', '06:00:00', 'MENSUAL')
ON CONFLICT DO NOTHING;

-- Cuatro avisos que dejaron rastro: la entrega cobrada, el vencimiento del aporte, la cobertura del fondo y la imputación con plazo de descargo. Los dos últimos son obligatorios y por eso salieron aunque el titular apagó WhatsApp comercial.
INSERT INTO notificacion (usuario_id, evento_id, prioridad, contexto, clave_deduplicacion, estado, programada_para, creada_en, finalizada_en, correlation_id) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), (SELECT id FROM evento_notificable WHERE tipo = 'ENTREGA_ACREDITADA'), 'CRITICA', '{"grupo": "GRP-DEMO-01", "periodo": 1, "bruto": 3000.0, "deducciones": 10.5, "neto": 2989.5}'::jsonb, 'entrega:GRP-DEMO-01:1:USR000001', 'LEIDA', now() - interval '30 days', now() - interval '30 days', now() - interval '30 days', gen_random_uuid()),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), (SELECT id FROM evento_notificable WHERE tipo = 'APORTE_POR_VENCER'), 'NORMAL', '{"grupo": "GRP-DEMO-01", "periodo": 2, "monto": 500.0, "vence_en_dias": 3}'::jsonb, 'por-vencer:GRP-DEMO-01:2:USR000004', 'ENTREGADA', now() - interval '11 days', now() - interval '11 days', now() - interval '11 days', gen_random_uuid()),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), (SELECT id FROM evento_notificable WHERE tipo = 'APORTE_VENCIDO'), 'ALTA', '{"grupo": "GRP-DEMO-01", "periodo": 2, "monto": 500.0, "mora": 10.0, "total": 510.0}'::jsonb, 'vencido:GRP-DEMO-01:2:USR000004', 'ENTREGADA', now() - interval '4 days', now() - interval '4 days', now() - interval '4 days', gen_random_uuid()),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), (SELECT id FROM evento_notificable WHERE tipo = 'COBERTURA_APLICADA'), 'ALTA', '{"grupo": "GRP-DEMO-01", "periodo": 2, "cubierto": 500.0, "deuda": 510.0}'::jsonb, 'cobertura:GRP-DEMO-01:2:USR000004', 'ENTREGADA', now() - interval '3 days', now() - interval '3 days', now() - interval '3 days', gen_random_uuid())
ON CONFLICT DO NOTHING;

-- La prueba de entrega: sin esto no se puede sostener que el plazo de descargo empezó a correr (R-PLZ).
INSERT INTO envio_notificacion (notificacion_id, proveedor_id, version_plantilla_id, canal_vinculado_id, canal, destinatario, clave_idempotencia, encolado_en, contenido_enviado, estado, id_mensaje_proveedor, orden, intentos, max_intentos, costo, moneda, codigo_error, enviado_en, entregado_en, leido_en, proximo_reintento_en) VALUES
  ((SELECT id FROM notificacion WHERE clave_deduplicacion = 'entrega:GRP-DEMO-01:1:USR000001'), (SELECT id FROM proveedor_mensajeria WHERE codigo = 'WHATSAPP_BSP'), (SELECT v.id FROM version_plantilla v JOIN plantilla_mensaje p ON p.id = v.plantilla_id WHERE p.codigo = 'TX_ENTREGA_ACREDITADA_WA' AND v.version = 1), (SELECT id FROM canal_vinculado WHERE identificador = '+59171000001' AND tipo = 'WHATSAPP'), 'WHATSAPP', '+59171000001', 'demo-envio-entrega-p1', now() - interval '30 days', 'María Elena, cobraste tu turno del grupo Pasanaku demo del barrio. Bolsa: Bs 3000.00. Deducciones: Bs 10.50. Acreditado: Bs 2989.50. Detalle en la app.', 'LEIDO', 'wamid.DEMO0001', 1, 1, 3, 0.28, 'BOB', NULL, now() - interval '30 days', now() - interval '30 days', now() - interval '30 days', NULL),
  ((SELECT id FROM notificacion WHERE clave_deduplicacion = 'por-vencer:GRP-DEMO-01:2:USR000004'), (SELECT id FROM proveedor_mensajeria WHERE codigo = 'WHATSAPP_BSP'), (SELECT v.id FROM version_plantilla v JOIN plantilla_mensaje p ON p.id = v.plantilla_id WHERE p.codigo = 'COB_APORTE_POR_VENCER_WA' AND v.version = 1), (SELECT id FROM canal_vinculado WHERE identificador = '+59171000004' AND tipo = 'WHATSAPP'), 'WHATSAPP', '+59171000004', 'demo-envio-por-vencer-p2-04', now() - interval '11 days', 'Pedro, tu aporte de Bs 500.00 al grupo Pasanaku demo del barrio vence en 3 días. Podés pagarlo desde la app en un toque.', 'ENTREGADO', 'wamid.DEMO0002', 1, 1, 3, 0.28, 'BOB', NULL, now() - interval '11 days', now() - interval '11 days', NULL, NULL),
  ((SELECT id FROM notificacion WHERE clave_deduplicacion = 'vencido:GRP-DEMO-01:2:USR000004'), (SELECT id FROM proveedor_mensajeria WHERE codigo = 'WHATSAPP_BSP'), (SELECT v.id FROM version_plantilla v JOIN plantilla_mensaje p ON p.id = v.plantilla_id WHERE p.codigo = 'COB_APORTE_VENCIDO_WA' AND v.version = 1), (SELECT id FROM canal_vinculado WHERE identificador = '+59171000004' AND tipo = 'WHATSAPP'), 'WHATSAPP', '+59171000004', 'demo-envio-vencido-p2-04', now() - interval '4 days', 'Pedro: tu aporte al grupo Pasanaku demo del barrio venció. Total a pagar hoy: Bs 510.00 (aporte Bs 500.00 + recargo Bs 10.00). Si necesitás otro plazo, escribinos.', 'ENTREGADO', 'wamid.DEMO0003', 1, 1, 3, 0.28, 'BOB', NULL, now() - interval '4 days', now() - interval '4 days', NULL, NULL),
  ((SELECT id FROM notificacion WHERE clave_deduplicacion = 'cobertura:GRP-DEMO-01:2:USR000004'), (SELECT id FROM proveedor_mensajeria WHERE codigo = 'WHATSAPP_BSP'), (SELECT v.id FROM version_plantilla v JOIN plantilla_mensaje p ON p.id = v.plantilla_id WHERE p.codigo = 'COB_COBERTURA_APLICADA_WA' AND v.version = 1), (SELECT id FROM canal_vinculado WHERE identificador = '+59171000004' AND tipo = 'WHATSAPP'), 'WHATSAPP', '+59171000004', 'demo-envio-cobertura-p2-04', now() - interval '3 days', 'Pedro: el fondo de garantía del grupo Pasanaku demo del barrio cubrió tu aporte de Bs 500.00 para que el grupo no se frene. Queda una deuda a tu nombre por Bs 510.00. Podés regularizarla o pedir un plan de pagos.', 'ENTREGADO', 'wamid.DEMO0004', 1, 1, 3, 0.28, 'BOB', NULL, now() - interval '3 days', now() - interval '3 days', NULL, NULL)
ON CONFLICT DO NOTHING;

-- Un cierre cuadrado: todo lo recaudado quedó conciliado y no hay excepciones abiertas.
INSERT INTO cierre_diario (fecha, total_recaudado, total_conciliado, total_excepciones, cantidad_pagos, cuadrado, cerrado_por, cerrado_en, reabierto_en) VALUES
  ((current_date + interval '-1 days'), 2500.0, 2500.0, 0.0, 5, TRUE, (SELECT id FROM usuario WHERE codigo_publico = 'USR000008'), now() - interval '18 hours', NULL)
ON CONFLICT DO NOTHING;

-- El encaje del día: Bs 9.900 en el banco custodio contra Bs 9.900 de dinero electrónico emitido. `diferencia` y `ratio_cobertura` son columnas derivadas: no se escriben, se calculan.
INSERT INTO conciliacion_custodia (cuenta_custodia_id, cierre_diario_id, ejecutada_por, fecha, saldo_dinero_electronico, saldo_custodia, saldo_en_transito, cumple_encaje, estado, ejecutada_en) VALUES
  ((SELECT id FROM cuenta_custodia WHERE contrato_referencia = 'FID-DEMO-001'), (SELECT id FROM cierre_diario ORDER BY fecha DESC LIMIT 1), (SELECT id FROM usuario WHERE codigo_publico = 'USR000008'), (current_date + interval '-1 days'), 9900.0, 9900.0, 0.0, TRUE, 'CUADRADA', now() - interval '17 hours')
ON CONFLICT DO NOTHING;

-- Un arqueo con faltante de Bs 20: `diferencia` es derivada y negativa, y por eso abre el evento de riesgo operativo de más abajo.
INSERT INTO arqueo_punto_atencion (punto_atencion_id, arqueado_por, fecha, saldo_inicial, total_recargas, total_retiros, saldo_teorico, saldo_contado, estado, observaciones, cerrado_en) VALUES
  ((SELECT id FROM punto_atencion WHERE codigo = 'PA-SCZ-001'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000008'), (current_date + interval '-44 days'), 0.0, 600.0, 0.0, 600.0, 580.0, 'DESCUADRADO', 'Faltante de Bs 20,00 al cierre. Se abre evento de riesgo operativo y se descuenta del corresponsal.', now() - interval '44 days'),
  ((SELECT id FROM punto_atencion WHERE codigo = 'PA-LPZ-001'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000008'), (current_date + interval '-44 days'), 0.0, 0.0, 0.0, 0.0, 0.0, 'CUADRADO', NULL, now() - interval '44 days')
ON CONFLICT DO NOTHING;

-- El faltante del arqueo, cuantificado. `perdida_neta` es derivada: bruta menos recuperación.
INSERT INTO evento_riesgo_operativo (codigo, incidente_operativo_id, registrado_por, categoria_evento, factor_riesgo, reportado_central_riesgo_operativo, linea_negocio, descripcion, fecha_ocurrencia, fecha_deteccion, fecha_contabilizacion, perdida_bruta, recuperacion, moneda, causa_raiz, estado) VALUES
  ('ERO-DEMO-0001', NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000009'), 'FRAUDE_EXTERNO', 'PERSONAS', FALSE, 'Recaudación en puntos de atención', 'Faltante de Bs 20,00 en el arqueo del corresponsal PA-SCZ-001. Se recuperó la totalidad con el descuento de la liquidación del punto.', now() - interval '44 days', now() - interval '44 days', now() - interval '43 days', 20.0, 20.0, 'BOB', 'Conteo incorrecto al cierre de caja del corresponsal.', 'CERRADO')
ON CONFLICT DO NOTHING;

-- Reclamo con plazo GUARDADO al ingresar, no recalculado: 5 días hábiles desde el ingreso. `ck_reclamo_conservacion` exige además conservar el expediente 10 años. Es el caso que ejercita la segunda instancia ante el regulador.
INSERT INTO reclamo_cliente (codigo, usuario_id, punto_reclamo_id, responsable_id, ticket_soporte_id, devolucion_comision_id, categoria, producto, monto_reclamado, descripcion, canal_ingreso, estado, fecha_ingreso, dias_habiles_plazo, plazo_respuesta, plazo_prorrogado_hasta, prorroga_comunicada_al_cliente_en, prorroga_comunicada_al_organismo_en, justificacion_prorroga, fecha_respuesta, resultado, respuesta, incluido_en_reporte_mensual, conservar_hasta) VALUES
  ('REC-DEMO-0001', (SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), (SELECT id FROM punto_reclamo WHERE codigo = 'PR-APP'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000008'), NULL, NULL, 'GRUPO', 'PASANAKU', 10.0, 'No estoy de acuerdo con el recargo por mora de Bs 10. El día del vencimiento intenté pagar con el QR y la app me dio error; recién al día siguiente pude entrar. Pido que se me deje sin efecto el recargo.', 'APP', 'EN_ANALISIS', now() - interval '2 days', 5, now() + interval '5 days', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, (current_date + interval '3660 days'))
ON CONFLICT DO NOTHING;

-- Primera instancia abierta en la entidad. Si la respuesta no conforma, se agrega otra fila con instancia REGULADOR: el historial no se pisa.
INSERT INTO instancia_reclamo (reclamo_id, instancia, fecha_elevacion, numero_expediente, estado, resolucion, fecha_resolucion, monto_resarcido) VALUES
  ((SELECT id FROM reclamo_cliente WHERE codigo = 'REC-DEMO-0001'), 'ENTIDAD', now() - interval '2 days', 'EXP-ENT-0001', 'EN_TRAMITE', NULL, NULL, NULL)
ON CONFLICT DO NOTHING;
