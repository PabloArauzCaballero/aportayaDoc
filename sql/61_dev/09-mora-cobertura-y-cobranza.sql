-- Qué pasa cuando alguien no paga: expediente de incumplimiento con notificación probada y descargo abierto, cobertura del fondo de garantía, deuda subrogada a nombre del moroso y gestión de cobranza en etapa temprana.
-- GENERADO desde seeders/dev/09-mora-cobertura-y-cobranza.json — no editar a mano.

-- Estado NOTIFICADO: se detectó, se notificó y corre el plazo de descargo. Todavía no hay sanción.
INSERT INTO registro_incumplimiento (codigo_expediente, usuario_id, participante_id, grupo_id, periodo_id, cupo_id, obligacion_id, entrega_afectada_id, responsable_gestion, tipo, severidad, estado, origen_deteccion, monto_involucrado, monto_recuperado, monto_castigado, dias_mora_al_detectar, dias_mora_actuales, es_reincidencia, numero_reincidencia, afecto_a_la_entrega, detectado_en, notificado_en, fecha_limite_subsanacion, cerrado_en, motivo_cierre, resumen_resolucion, reportado_por, version) VALUES
  ('INC-DEMO-0001', (SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), (SELECT id FROM participante WHERE grupo_id = (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01') AND usuario_id = (SELECT id FROM usuario WHERE codigo_publico = 'USR000004')), (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), (SELECT id FROM periodo WHERE grupo_id = (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01') AND numero = 2), (SELECT id FROM cupo WHERE grupo_id = (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01') AND numero = 4), (SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000004'), NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), 'APORTE_IMPAGO', 'MODERADA', 'NOTIFICADO', 'AUTOMATICO_VENCIMIENTO', 510.0, 0.0, 0.0, 4, 8, FALSE, 1, FALSE, now() - interval '4 days', now() - interval '4 days', now() + interval '3 days', NULL, NULL, NULL, NULL, 2)
ON CONFLICT DO NOTHING;

-- La evidencia se arma sola con lo que ya existe: la orden vencida y el saldo insuficiente
INSERT INTO evidencia_incumplimiento (registro_id, tipo, descripcion, url_archivo, hash_archivo, contenido_estructurado, aportada_por, fecha_hora, es_inmutable) VALUES
  ((SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001'), 'LOG_SISTEMA', 'La orden de cobro AP-P2-04 venció sin pago y el intento quedó en estado EXPIRADA', NULL, NULL, '{"orden": "AP-P2-04", "estado": "EXPIRADA", "intentos": 1}'::jsonb, NULL, now() - interval '5 days', TRUE),
  ((SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001'), 'CAPTURA_ESTADO', 'Saldo disponible de la billetera del titular al vencimiento: Bs 0,00', NULL, NULL, '{"cuenta": "BOB-0000004", "saldo_disponible": 0.0, "fecha": "vencimiento"}'::jsonb, NULL, now() - interval '8 days', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO historial_estado_incumplimiento (registro_id, estado_anterior, estado_nuevo, motivo, monto_asociado, ejecutado_por, es_automatico, fecha_hora) VALUES
  ((SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001'), NULL, 'DETECTADO', 'Vencimiento de la obligación sin pago, superada la gracia de 3 días', 500.0, NULL, TRUE, now() - interval '4 days'),
  ((SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001'), 'DETECTADO', 'NOTIFICADO', 'Notificación entregada por WhatsApp y correo con plazo de descargo de 5 días hábiles', 510.0, NULL, TRUE, now() - interval '4 days'),
  ((SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001'), 'NOTIFICADO', 'CUBIERTO_POR_GARANTIA', 'El fondo de garantía cubrió el aporte para no frenar la entrega del período', 500.0, (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), FALSE, now() - interval '3 days')
ON CONFLICT DO NOTHING;

INSERT INTO cobertura_incumplimiento (fondo_id, registro_id, obligacion_id, periodo_id, movimiento_fondo_id, asiento_contable_id, aprobada_por, monto_solicitado, monto_cubierto, porcentaje_cobertura, estado, requirio_aprobacion_manual, motivo_rechazo, solicitada_en, aplicada_en) VALUES
  ((SELECT f.id FROM fondo_garantia f JOIN grupo g ON g.id = f.grupo_id WHERE g.codigo_publico = 'GRP-DEMO-01'), (SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001'), (SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000004'), (SELECT id FROM periodo WHERE grupo_id = (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01') AND numero = 2), NULL, NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), 500.0, 500.0, 100.0, 'EN_RECUPERACION', FALSE, NULL, now() - interval '3 days', now() - interval '3 days')
ON CONFLICT DO NOTHING;

INSERT INTO transaccion_billetera (tipo, estado, moneda, monto_total, grupo_id, origen_tipo, origen_id, canal, clave_idempotencia, hash_registro, ocurrida_en, registrada_en) VALUES
  ('COBERTURA_GARANTIA', 'APLICADA', 'BOB', 500.0, (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), 'COBERTURA_INCUMPLIMIENTO', (SELECT id FROM cobertura_incumplimiento LIMIT 1), 'BATCH', 'demo-cobertura-p2-04', '', now() - interval '3 days', now() - interval '3 days')
ON CONFLICT (COALESCE(iniciada_por, '00000000-0000-0000-0000-000000000000'::uuid), origen_tipo, clave_idempotencia) DO NOTHING;

INSERT INTO movimiento_billetera (transaccion_id, cuenta_billetera_id, orden, sentido, monto, saldo_disponible_posterior, saldo_retenido_posterior, glosa, registrado_en) VALUES
  ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-cobertura-p2-04'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'FDG-0000001'), 1, 'DEBITO', 500.0, 100.0, 0.0, 'Cobertura del aporte impago del período 2 — USR000004', now() - interval '3 days'),
  ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-cobertura-p2-04'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'GRP-0000001'), 2, 'CREDITO', 500.0, 3000.0, 0.0, 'Aporte del período 2 cubierto por el fondo de garantía', now() - interval '3 days')
ON CONFLICT DO NOTHING;

INSERT INTO movimiento_fondo (fondo_id, asiento_contable_id, tipo, monto, saldo_resultante, referencia_tipo, referencia_id, descripcion, fecha, registrado_por) VALUES
  ((SELECT f.id FROM fondo_garantia f JOIN grupo g ON g.id = f.grupo_id WHERE g.codigo_publico = 'GRP-DEMO-01'), NULL, 'COBERTURA_APLICADA', 500.0, 100.0, 'cobertura_incumplimiento', (SELECT id FROM cobertura_incumplimiento WHERE registro_id = (SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001')), 'Cobertura del aporte impago del período 2 — USR000004', now() - interval '3 days', (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'))
ON CONFLICT DO NOTHING;

INSERT INTO deuda_participante (usuario_id, participante_id, grupo_id, registro_id, cobertura_id, acreedor, capital_original, recargos_acumulados, total_abonado, saldo_actual, moneda, estado, es_subrogada, fecha_exigibilidad, fecha_prescripcion, dias_vencida, version) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), (SELECT id FROM participante WHERE grupo_id = (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01') AND usuario_id = (SELECT id FROM usuario WHERE codigo_publico = 'USR000004')), (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), (SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001'), (SELECT id FROM cobertura_incumplimiento WHERE registro_id = (SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001')), 'FONDO_GARANTIA', 500.0, 10.0, 0.0, 510.0, 'BOB', 'EN_MORA', TRUE, (current_date + interval '-3 days'), (current_date + interval '1822 days'), 3, 1)
ON CONFLICT DO NOTHING;

INSERT INTO subrogacion (cobertura_id, deuda_id, acreedor_original, acreedor_subrogado, monto_subrogado, fecha, documento_respaldo_url) VALUES
  ((SELECT id FROM cobertura_incumplimiento WHERE registro_id = (SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001')), (SELECT id FROM deuda_participante WHERE registro_id = (SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001')), 'GRUPO', 'FONDO_GARANTIA', 500.0, now() - interval '3 days', 'https://almacen.pasanaku.test/subrogaciones/INC-DEMO-0001.pdf')
ON CONFLICT DO NOTHING;

INSERT INTO gestion_cobranza (registro_id, estrategia_id, gestor_asignado_id, etapa_actual, monto_en_gestion, intentos_contacto, ultimo_contacto_en, proxima_accion_en, estado, abierta_en, cerrada_en) VALUES
  ((SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001'), (SELECT id FROM estrategia_cobranza WHERE etapa = 'TEMPRANA'), NULL, 'TEMPRANA', 510.0, 2, now() - interval '2 days', now() + interval '1 day', 'ACTIVA', now() - interval '4 days', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO accion_cobranza (gestion_id, notificacion_id, etapa, tipo, canal, resultado, nota_gestor, costo, ejecutada_por, ejecutada_en) VALUES
  ((SELECT id FROM gestion_cobranza WHERE registro_id = (SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001')), NULL, 'TEMPRANA', 'RECORDATORIO_AUTOMATICO', 'WHATSAPP', 'SIN_RESPUESTA', 'Primer recordatorio automático tras el vencimiento', 0.28, NULL, now() - interval '4 days'),
  ((SELECT id FROM gestion_cobranza WHERE registro_id = (SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001')), NULL, 'TEMPRANA', 'MENSAJE_DIRECTO', 'WHATSAPP', 'SOLICITA_PLAN', 'El titular responde que cobra el día 15 y pide fraccionar en dos cuotas', 0.28, (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), now() - interval '2 days')
ON CONFLICT DO NOTHING;

-- El descargo está presentado y sin resolver: mientras no se resuelva, no puede haber sanción firme
INSERT INTO descargo_participante (registro_id, participante_id, argumento, evidencias, estado, resolucion, resuelto_por, presentado_en, resuelto_en) VALUES
  ((SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001'), (SELECT id FROM participante WHERE grupo_id = (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01') AND usuario_id = (SELECT id FROM usuario WHERE codigo_publico = 'USR000004')), 'Reconozco el atraso. Me retrasaron el pago del servicio que presto y cobro el día 15. Pido plan de pagos en dos cuotas y que no se me expulse del grupo.', '{"adjuntos": [], "declara": "pago comprometido para el día 15"}'::jsonb, 'EN_ANALISIS', NULL, NULL, now() - interval '2 days', NULL)
ON CONFLICT DO NOTHING;

-- La alerta acompaña, no castiga: dispara una gestión, nunca una sanción
INSERT INTO alerta_temprana (usuario_id, grupo_id, codigo, descripcion, severidad, estado, generada_en) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), 'PAGA_CADA_VEZ_MAS_TARDE', 'El período 1 lo pagó a los 2 días del corte y el período 2 no lo pagó: la tendencia empeora', 'MEDIA', 'ATENDIDA', now() - interval '6 days')
ON CONFLICT DO NOTHING;
