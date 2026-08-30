-- Cobro del período 2 por QR interoperable: orden con vencimiento, intento, webhook firmado, acreditación, conciliación contra el extracto y constancia verificable. La orden de USR000004 venció sin pago.
-- GENERADO desde seeders/dev/08-cobros-qr-y-conciliacion.json — no editar a mano.

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO orden_cobro (obligacion_id, proveedor_id, monto_exacto, moneda, permite_monto_abierto, referencia_unica, estado, emitida_en, expira_en, clave_idempotencia) VALUES
      ((SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000001'), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 500.0, 'BOB', FALSE, 'AP-P2-01', 'PAGADA', now() - interval '15 days', now() - interval '5 days', 'demo-orden-cobro-p2-01'),
      ((SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000002'), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 500.0, 'BOB', FALSE, 'AP-P2-02', 'PAGADA', now() - interval '15 days', now() - interval '5 days', 'demo-orden-cobro-p2-02'),
      ((SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000003'), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 500.0, 'BOB', FALSE, 'AP-P2-03', 'PAGADA', now() - interval '15 days', now() - interval '5 days', 'demo-orden-cobro-p2-03'),
      ((SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000004'), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 500.0, 'BOB', FALSE, 'AP-P2-04', 'EXPIRADA', now() - interval '15 days', now() - interval '5 days', 'demo-orden-cobro-p2-04'),
      ((SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000005'), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 500.0, 'BOB', FALSE, 'AP-P2-05', 'PAGADA', now() - interval '15 days', now() - interval '5 days', 'demo-orden-cobro-p2-05'),
      ((SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000006'), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 500.0, 'BOB', FALSE, 'AP-P2-06', 'PAGADA', now() - interval '15 days', now() - interval '5 days', 'demo-orden-cobro-p2-06')
    ON CONFLICT (obligacion_id, clave_idempotencia) DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO qr_cobro (orden_cobro_id, payload_emv, url_imagen, crc, banco_emisor, cuenta_abono, es_reutilizable, escaneos) VALUES
      ((SELECT id FROM orden_cobro WHERE clave_idempotencia = 'demo-orden-cobro-p2-01'), '00020101021226580014BO.QR.DEMO0114AP-P2-01520400005303068540550052.005802BO5910APORTAYA6304', 'https://almacen.pasanaku.test/qr/AP-P2-01.png', 'A1B2', 'BANCO DEMO S.A.', '****4321', FALSE, 2),
      ((SELECT id FROM orden_cobro WHERE clave_idempotencia = 'demo-orden-cobro-p2-02'), '00020101021226580014BO.QR.DEMO0114AP-P2-02520400005303068540550052.005802BO5910APORTAYA6304', 'https://almacen.pasanaku.test/qr/AP-P2-02.png', 'A1B2', 'BANCO DEMO S.A.', '****4321', FALSE, 2),
      ((SELECT id FROM orden_cobro WHERE clave_idempotencia = 'demo-orden-cobro-p2-03'), '00020101021226580014BO.QR.DEMO0114AP-P2-03520400005303068540550052.005802BO5910APORTAYA6304', 'https://almacen.pasanaku.test/qr/AP-P2-03.png', 'A1B2', 'BANCO DEMO S.A.', '****4321', FALSE, 2),
      ((SELECT id FROM orden_cobro WHERE clave_idempotencia = 'demo-orden-cobro-p2-04'), '00020101021226580014BO.QR.DEMO0114AP-P2-04520400005303068540550052.005802BO5910APORTAYA6304', 'https://almacen.pasanaku.test/qr/AP-P2-04.png', 'A1B2', 'BANCO DEMO S.A.', '****4321', FALSE, 0),
      ((SELECT id FROM orden_cobro WHERE clave_idempotencia = 'demo-orden-cobro-p2-05'), '00020101021226580014BO.QR.DEMO0114AP-P2-05520400005303068540550052.005802BO5910APORTAYA6304', 'https://almacen.pasanaku.test/qr/AP-P2-05.png', 'A1B2', 'BANCO DEMO S.A.', '****4321', FALSE, 2),
      ((SELECT id FROM orden_cobro WHERE clave_idempotencia = 'demo-orden-cobro-p2-06'), '00020101021226580014BO.QR.DEMO0114AP-P2-06520400005303068540550052.005802BO5910APORTAYA6304', 'https://almacen.pasanaku.test/qr/AP-P2-06.png', 'A1B2', 'BANCO DEMO S.A.', '****4321', FALSE, 2)
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO intento_pago (orden_cobro_id, numero_intento, canal, iniciado_en, finalizado_en, estado, codigo_error, mensaje_proveedor, clave_idempotencia) VALUES
      ((SELECT id FROM orden_cobro WHERE clave_idempotencia = 'demo-orden-cobro-p2-01'), 1, 'QR_INTEROPERABLE', now() - interval '12 days', now() - interval '12 days', 'APROBADA', NULL, 'Pago aprobado', 'demo-intento-p2-01'),
      ((SELECT id FROM orden_cobro WHERE clave_idempotencia = 'demo-orden-cobro-p2-02'), 1, 'QR_INTEROPERABLE', now() - interval '12 days', now() - interval '12 days', 'APROBADA', NULL, 'Pago aprobado', 'demo-intento-p2-02'),
      ((SELECT id FROM orden_cobro WHERE clave_idempotencia = 'demo-orden-cobro-p2-03'), 1, 'QR_INTEROPERABLE', now() - interval '11 days', now() - interval '11 days', 'APROBADA', NULL, 'Pago aprobado', 'demo-intento-p2-03'),
      ((SELECT id FROM orden_cobro WHERE clave_idempotencia = 'demo-orden-cobro-p2-04'), 1, 'QR_INTEROPERABLE', now() - interval '6 days', now() - interval '5 days', 'EXPIRADA', 'ORDEN_EXPIRADA', 'La orden venció sin pago', 'demo-intento-p2-04'),
      ((SELECT id FROM orden_cobro WHERE clave_idempotencia = 'demo-orden-cobro-p2-05'), 1, 'QR_INTEROPERABLE', now() - interval '13 days', now() - interval '13 days', 'APROBADA', NULL, 'Pago aprobado', 'demo-intento-p2-05'),
      ((SELECT id FROM orden_cobro WHERE clave_idempotencia = 'demo-orden-cobro-p2-06'), 1, 'QR_INTEROPERABLE', now() - interval '10 days', now() - interval '10 days', 'APROBADA', NULL, 'Pago aprobado', 'demo-intento-p2-06')
    ON CONFLICT (orden_cobro_id, clave_idempotencia) DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO pago (obligacion_id, intento_pago_id, proveedor_id, monto, moneda, monto_comision_proveedor, monto_neto_acreditado, canal, estado, fecha_hora_pago, fecha_hora_acreditacion, referencia_proveedor, pagador_nombre, pagador_documento, cuenta_origen_enmascarada, registrado_por, es_manual, clave_idempotencia) VALUES
      ((SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000001'), (SELECT id FROM intento_pago WHERE clave_idempotencia = 'demo-intento-p2-01'), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 500.0, 'BOB', 1.75, 498.25, 'QR_INTEROPERABLE', 'ACREDITADO', now() - interval '12 days', now() - interval '12 days', 'BCO-P2-01', 'María Elena Quispe Mamani', '9000001', '****4001', NULL, FALSE, 'demo-pago-p2-01'),
      ((SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000002'), (SELECT id FROM intento_pago WHERE clave_idempotencia = 'demo-intento-p2-02'), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 500.0, 'BOB', 1.75, 498.25, 'QR_INTEROPERABLE', 'ACREDITADO', now() - interval '12 days', now() - interval '12 days', 'BCO-P2-02', 'Juan Carlos Rojas Vargas', '9000002', '****4002', NULL, FALSE, 'demo-pago-p2-02'),
      ((SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000003'), (SELECT id FROM intento_pago WHERE clave_idempotencia = 'demo-intento-p2-03'), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 500.0, 'BOB', 1.75, 498.25, 'QR_INTEROPERABLE', 'ACREDITADO', now() - interval '11 days', now() - interval '11 days', 'BCO-P2-03', 'Ana Lucía Choque Flores', '9000003', '****4003', NULL, FALSE, 'demo-pago-p2-03'),
      ((SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000005'), (SELECT id FROM intento_pago WHERE clave_idempotencia = 'demo-intento-p2-05'), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 500.0, 'BOB', 1.75, 498.25, 'QR_INTEROPERABLE', 'ACREDITADO', now() - interval '13 days', now() - interval '13 days', 'BCO-P2-05', 'Rosa Condori Apaza', '9000005', '****4005', NULL, FALSE, 'demo-pago-p2-05'),
      ((SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000006'), (SELECT id FROM intento_pago WHERE clave_idempotencia = 'demo-intento-p2-06'), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 500.0, 'BOB', 1.75, 498.25, 'QR_INTEROPERABLE', 'ACREDITADO', now() - interval '10 days', now() - interval '10 days', 'BCO-P2-06', 'Luis Fernando Mendoza Paz', '9000006', '****4006', NULL, FALSE, 'demo-pago-p2-06')
    ON CONFLICT (obligacion_id, clave_idempotencia) DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO webhook_pasarela (proveedor_id, evento, payload_crudo, firma, firma_valida, recibido_en, procesado_en, intentos_procesamiento, estado, clave_idempotencia, error_procesamiento, pago_id) VALUES
      ((SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 'pago.acreditado', '{"referencia": "BCO-P2-01", "monto": 500.0, "moneda": "BOB", "orden": "AP-P2-01", "estado": "APROBADO"}'::jsonb, 'hmac-demo-01', TRUE, now() - interval '12 days', now() - interval '12 days', 1, 'PROCESADO', 'demo-webhook-p2-01', NULL, (SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-01')),
      ((SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 'pago.acreditado', '{"referencia": "BCO-P2-02", "monto": 500.0, "moneda": "BOB", "orden": "AP-P2-02", "estado": "APROBADO"}'::jsonb, 'hmac-demo-02', TRUE, now() - interval '12 days', now() - interval '12 days', 1, 'PROCESADO', 'demo-webhook-p2-02', NULL, (SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-02')),
      ((SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 'pago.acreditado', '{"referencia": "BCO-P2-03", "monto": 500.0, "moneda": "BOB", "orden": "AP-P2-03", "estado": "APROBADO"}'::jsonb, 'hmac-demo-03', TRUE, now() - interval '11 days', now() - interval '11 days', 1, 'PROCESADO', 'demo-webhook-p2-03', NULL, (SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-03')),
      ((SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 'pago.acreditado', '{"referencia": "BCO-P2-05", "monto": 500.0, "moneda": "BOB", "orden": "AP-P2-05", "estado": "APROBADO"}'::jsonb, 'hmac-demo-05', TRUE, now() - interval '13 days', now() - interval '13 days', 1, 'PROCESADO', 'demo-webhook-p2-05', NULL, (SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-05')),
      ((SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), 'pago.acreditado', '{"referencia": "BCO-P2-06", "monto": 500.0, "moneda": "BOB", "orden": "AP-P2-06", "estado": "APROBADO"}'::jsonb, 'hmac-demo-06', TRUE, now() - interval '10 days', now() - interval '10 days', 1, 'PROCESADO', 'demo-webhook-p2-06', NULL, (SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-06'))
    ON CONFLICT (proveedor_id, clave_idempotencia) DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO conciliacion (pago_id, movimiento_bancario_id, estado, metodo, diferencia_monto, conciliado_por, fecha_conciliacion) VALUES
      ((SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-01'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-P2-01'), 'CONCILIADO_AUTOMATICO', 'REFERENCIA_EXACTA', 0.0, NULL, now() - interval '12 days'),
      ((SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-02'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-P2-02'), 'CONCILIADO_AUTOMATICO', 'REFERENCIA_EXACTA', 0.0, NULL, now() - interval '12 days'),
      ((SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-03'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-P2-03'), 'CONCILIADO_AUTOMATICO', 'REFERENCIA_EXACTA', 0.0, NULL, now() - interval '11 days'),
      ((SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-05'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-P2-05'), 'CONCILIADO_AUTOMATICO', 'REFERENCIA_EXACTA', 0.0, NULL, now() - interval '13 days'),
      ((SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-06'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-P2-06'), 'CONCILIADO_AUTOMATICO', 'REFERENCIA_EXACTA', 0.0, NULL, now() - interval '10 days')
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO constancia_pago (pago_id, codigo_verificacion, hash_contenido, url_publica, url_pdf, fecha_generacion) VALUES
      ((SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-01'), 'CONST-P2-01', encode(digest('constancia-p2-01', 'sha256'), 'hex'), 'https://pasanaku.bo/constancia/CONST-P2-01', 'https://almacen.pasanaku.test/constancias/CONST-P2-01.pdf', now() - interval '12 days'),
      ((SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-02'), 'CONST-P2-02', encode(digest('constancia-p2-02', 'sha256'), 'hex'), 'https://pasanaku.bo/constancia/CONST-P2-02', 'https://almacen.pasanaku.test/constancias/CONST-P2-02.pdf', now() - interval '12 days'),
      ((SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-03'), 'CONST-P2-03', encode(digest('constancia-p2-03', 'sha256'), 'hex'), 'https://pasanaku.bo/constancia/CONST-P2-03', 'https://almacen.pasanaku.test/constancias/CONST-P2-03.pdf', now() - interval '11 days'),
      ((SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-05'), 'CONST-P2-05', encode(digest('constancia-p2-05', 'sha256'), 'hex'), 'https://pasanaku.bo/constancia/CONST-P2-05', 'https://almacen.pasanaku.test/constancias/CONST-P2-05.pdf', now() - interval '13 days'),
      ((SELECT id FROM pago WHERE clave_idempotencia = 'demo-pago-p2-06'), 'CONST-P2-06', encode(digest('constancia-p2-06', 'sha256'), 'hex'), 'https://pasanaku.bo/constancia/CONST-P2-06', 'https://almacen.pasanaku.test/constancias/CONST-P2-06.pdf', now() - interval '10 days')
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO transaccion_billetera (tipo, estado, moneda, monto_total, grupo_id, origen_tipo, origen_id, canal, clave_idempotencia, hash_registro, ocurrida_en, registrada_en) VALUES
      ('APORTE_A_GRUPO', 'APLICADA', 'BOB', 500.0, (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), 'OBLIGACION_APORTE', (SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000001'), 'API', 'demo-aporte-p2-01', '', now() - interval '12 days', now() - interval '12 days'),
      ('APORTE_A_GRUPO', 'APLICADA', 'BOB', 500.0, (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), 'OBLIGACION_APORTE', (SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000002'), 'API', 'demo-aporte-p2-02', '', now() - interval '12 days', now() - interval '12 days'),
      ('APORTE_A_GRUPO', 'APLICADA', 'BOB', 500.0, (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), 'OBLIGACION_APORTE', (SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000003'), 'API', 'demo-aporte-p2-03', '', now() - interval '11 days', now() - interval '11 days'),
      ('APORTE_A_GRUPO', 'APLICADA', 'BOB', 500.0, (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), 'OBLIGACION_APORTE', (SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000005'), 'API', 'demo-aporte-p2-05', '', now() - interval '13 days', now() - interval '13 days'),
      ('APORTE_A_GRUPO', 'APLICADA', 'BOB', 500.0, (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), 'OBLIGACION_APORTE', (SELECT o.id FROM obligacion_aporte o JOIN participante p ON p.id = o.participante_id JOIN usuario u ON u.id = p.usuario_id JOIN periodo pe ON pe.id = o.periodo_id WHERE o.tipo = 'APORTE_PERIODICO' AND pe.numero = 2 AND u.codigo_publico = 'USR000006'), 'API', 'demo-aporte-p2-06', '', now() - interval '10 days', now() - interval '10 days')
    ON CONFLICT (COALESCE(iniciada_por, '00000000-0000-0000-0000-000000000000'::uuid), origen_tipo, clave_idempotencia) DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO movimiento_billetera (transaccion_id, cuenta_billetera_id, orden, sentido, monto, saldo_disponible_posterior, saldo_retenido_posterior, glosa, registrado_en) VALUES
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-aporte-p2-01'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'GRP-0000001'), 1, 'CREDITO', 500.0, 500.0, 0.0, 'Aporte del período 2 cobrado por QR interoperable — USR000001', now() - interval '12 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-aporte-p2-01'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'SYS-CUSTODIA'), 2, 'DEBITO', 500.0, -7900.0, 0.0, 'Ingreso a custodia por cobro QR del aporte — USR000001', now() - interval '12 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-aporte-p2-02'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'GRP-0000001'), 1, 'CREDITO', 500.0, 1000.0, 0.0, 'Aporte del período 2 cobrado por QR interoperable — USR000002', now() - interval '12 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-aporte-p2-02'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'SYS-CUSTODIA'), 2, 'DEBITO', 500.0, -8400.0, 0.0, 'Ingreso a custodia por cobro QR del aporte — USR000002', now() - interval '12 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-aporte-p2-03'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'GRP-0000001'), 1, 'CREDITO', 500.0, 1500.0, 0.0, 'Aporte del período 2 cobrado por QR interoperable — USR000003', now() - interval '11 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-aporte-p2-03'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'SYS-CUSTODIA'), 2, 'DEBITO', 500.0, -8900.0, 0.0, 'Ingreso a custodia por cobro QR del aporte — USR000003', now() - interval '11 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-aporte-p2-05'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'GRP-0000001'), 1, 'CREDITO', 500.0, 2000.0, 0.0, 'Aporte del período 2 cobrado por QR interoperable — USR000005', now() - interval '13 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-aporte-p2-05'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'SYS-CUSTODIA'), 2, 'DEBITO', 500.0, -9400.0, 0.0, 'Ingreso a custodia por cobro QR del aporte — USR000005', now() - interval '13 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-aporte-p2-06'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'GRP-0000001'), 1, 'CREDITO', 500.0, 2500.0, 0.0, 'Aporte del período 2 cobrado por QR interoperable — USR000006', now() - interval '10 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-aporte-p2-06'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'SYS-CUSTODIA'), 2, 'DEBITO', 500.0, -9900.0, 0.0, 'Ingreso a custodia por cobro QR del aporte — USR000006', now() - interval '10 days')
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

-- El dinero de los cobros QR entró a la cuenta de custodia: sin esto el encaje no cuadraría
DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO movimiento_custodia (cuenta_custodia_id, movimiento_bancario_id, fecha_valor, tipo, sentido, monto, referencia_bancaria, glosa, conciliado, registrado_en) VALUES
      ((SELECT id FROM cuenta_custodia WHERE contrato_referencia = 'FID-DEMO-001'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-P2-01'), (current_date + interval '-12 days'), 'INGRESO', 'CREDITO', 500.0, 'BCO-P2-01', 'Cobro QR del aporte del período 2 — USR000001', TRUE, now() - interval '12 days'),
      ((SELECT id FROM cuenta_custodia WHERE contrato_referencia = 'FID-DEMO-001'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-P2-02'), (current_date + interval '-12 days'), 'INGRESO', 'CREDITO', 500.0, 'BCO-P2-02', 'Cobro QR del aporte del período 2 — USR000002', TRUE, now() - interval '12 days'),
      ((SELECT id FROM cuenta_custodia WHERE contrato_referencia = 'FID-DEMO-001'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-P2-03'), (current_date + interval '-11 days'), 'INGRESO', 'CREDITO', 500.0, 'BCO-P2-03', 'Cobro QR del aporte del período 2 — USR000003', TRUE, now() - interval '11 days'),
      ((SELECT id FROM cuenta_custodia WHERE contrato_referencia = 'FID-DEMO-001'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-P2-05'), (current_date + interval '-13 days'), 'INGRESO', 'CREDITO', 500.0, 'BCO-P2-05', 'Cobro QR del aporte del período 2 — USR000005', TRUE, now() - interval '13 days'),
      ((SELECT id FROM cuenta_custodia WHERE contrato_referencia = 'FID-DEMO-001'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-P2-06'), (current_date + interval '-10 days'), 'INGRESO', 'CREDITO', 500.0, 'BCO-P2-06', 'Cobro QR del aporte del período 2 — USR000006', TRUE, now() - interval '10 days')
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

-- Custodia = 7.400 de recargas + 2.500 de cobros QR. Debe igualar el dinero electrónico emitido
UPDATE cuenta_custodia SET saldo_segun_libro = 9900.00, saldo_segun_banco = 9900.00,
       fecha_saldo = now()
 WHERE contrato_referencia = 'FID-DEMO-001';

-- Por qué acá no hay UPDATE de saldos
-- El saldo NO se escribe desde el seeder: lo mantiene la base.
-- tg_movimiento_sincroniza_saldo recalcula saldo_disponible y saldo_retenido
-- en cada INSERT de movimiento_billetera, y tg_retencion_sincroniza_saldo hace
-- lo mismo al crear o cambiar una retención. Escribirlo a mano acá duplicaría
-- el efecto y es exactamente la clase de defecto que R-BIL-03 previene.
SELECT 1;
