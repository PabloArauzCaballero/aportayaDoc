-- Los controles que rodean a la salida de dinero: cuenta bancaria verificada, desembolso que el banco rechazó, retiro retenido en ventana de enfriamiento, evaluación antifraude, bloqueo por orden de autoridad y una transferencia entre billeteras.
-- GENERADO desde seeders/dev/12-retiro-y-controles.json — no editar a mano.

-- Cuentas de destino verificadas por microdepósito. `ck_cuenta_bancaria_sin_claro` rechaza cualquier fila cuyo enmascarado contenga 9 dígitos seguidos o cuyo hash no mida 64 caracteres. `version_llave` dice con qué versión de la llave maestra se cifró el dato: rotar la llave es cifrar de nuevo y subir el número, no perder el acceso a lo viejo.
INSERT INTO cuenta_bancaria_beneficiario (usuario_id, tipo_cuenta, entidad_financiera, numero_cuenta_cifrado, hash_numero_cuenta, numero_enmascarado, titular_nombre, titular_documento, moneda, es_principal, estado_verificacion, metodo_verificacion, verificada_en, bloqueada_hasta, version_llave) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'AHORRO', 'BANCO DEMO S.A.', 'enc:v1:demo-cuenta-01', encode(digest('cuenta-beneficiario-01', 'sha256'), 'hex'), '****4001', 'María Elena Quispe Mamani', '9000001', 'BOB', TRUE, 'VERIFICADA', 'MICRODEPOSITO', now() - interval '40 days', NULL, 1),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'AHORRO', 'BANCO DEMO S.A.', 'enc:v1:demo-cuenta-05', encode(digest('cuenta-beneficiario-05', 'sha256'), 'hex'), '****4005', 'Rosa Condori Apaza', '9000005', 'BOB', TRUE, 'VERIFICADA', 'MICRODEPOSITO', now() - interval '38 days', NULL, 1),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'CORRIENTE', 'BANCO DEMO S.A.', 'enc:v1:demo-cuenta-02', encode(digest('cuenta-beneficiario-02', 'sha256'), 'hex'), '****4002', 'Juan Carlos Rojas Vargas', '9000002', 'BOB', TRUE, 'PENDIENTE', NULL, NULL, NULL, 1)
ON CONFLICT DO NOTHING;

-- Primer intento de pagar la bolsa del período 1 por transferencia bancaria: el banco la devolvió. Recién después se resolvió por compensación interna, que es como quedó registrada la entrega. Nada de esto movió dinero.
INSERT INTO orden_desembolso (entrega_id, proveedor_id, cuenta_destino_id, monto, moneda, estado, referencia_proveedor, glosa, clave_idempotencia, creada_en, acreditada_en) VALUES
  ((SELECT e.id FROM entrega_fondo e JOIN periodo pe ON pe.id = e.periodo_id JOIN grupo g ON g.id = e.grupo_id WHERE g.codigo_publico = 'GRP-DEMO-01' AND pe.numero = 1), (SELECT id FROM proveedor_pago WHERE codigo = 'ACH_INTERBANCARIA'), (SELECT id FROM cuenta_bancaria_beneficiario WHERE numero_enmascarado = '****4001'), 2989.5, 'BOB', 'DEVUELTA_POR_BANCO', 'ACH-DEMO-000001', 'Entrega período 1 grupo GRP-DEMO-01', 'demo-desembolso-entrega-p1', now() - interval '31 days', NULL)
ON CONFLICT DO NOTHING;

-- Dos intentos con error clasificado. El primero fue un timeout —reintentable—; el segundo, una cuenta inactiva, que no se reintenta y escala a una persona.
INSERT INTO intento_desembolso (orden_desembolso_id, numero_intento, iniciado_en, finalizado_en, resultado, codigo_error, mensaje_proveedor, reintentable_en) VALUES
  ((SELECT id FROM orden_desembolso WHERE clave_idempotencia = 'demo-desembolso-entrega-p1'), 1, now() - interval '31 days', now() - interval '31 days', 'TIMEOUT', 'GATEWAY_TIMEOUT', 'Sin respuesta del banco en 30 segundos', now() - interval '31 days' + interval '15 minutes'),
  ((SELECT id FROM orden_desembolso WHERE clave_idempotencia = 'demo-desembolso-entrega-p1'), 2, now() - interval '31 days' + interval '20 minutes', now() - interval '31 days' + interval '21 minutes', 'FALLIDO', 'CUENTA_INACTIVA', 'La cuenta de destino no admite abonos', NULL)
ON CONFLICT DO NOTHING;

-- La incidencia que abre el desembolso devuelto: tiene dueño, plazo y cierre documentado.
INSERT INTO incidencia_entrega (entrega_id, tipo, severidad, descripcion, reportada_por, asignada_a, estado, sla_horas, fecha_limite_sla, resolucion, evidencias, abierta_en, resuelta_en) VALUES
  ((SELECT e.id FROM entrega_fondo e JOIN periodo pe ON pe.id = e.periodo_id JOIN grupo g ON g.id = e.grupo_id WHERE g.codigo_publico = 'GRP-DEMO-01' AND pe.numero = 1), 'DESEMBOLSO_RECHAZADO', 'ALTA', 'El banco devolvió el abono de Bs 2.989,50 por cuenta de destino inactiva. La beneficiaria quedó sin cobrar su turno hasta resolverlo.', (SELECT id FROM usuario WHERE codigo_publico = 'USR000008'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000008'), 'RESUELTA', 24, now() - interval '30 days', 'Se acordó con la beneficiaria acreditar en su billetera de la plataforma. La entrega se ejecutó por compensación interna el mismo día.', '{"intentos": 2, "codigos": ["GATEWAY_TIMEOUT", "CUENTA_INACTIVA"], "orden_desembolso": "demo-desembolso-entrega-p1"}'::jsonb, now() - interval '31 days', now() - interval '30 days')
ON CONFLICT DO NOTHING;

-- La retención del retiro en curso. Baja el disponible y sube el retenido: el total no cambia, que es lo que impide que el mismo saldo se comprometa dos veces.
INSERT INTO retencion_saldo (cuenta_billetera_id, transaccion_origen_id, liberada_por, motivo, referencia_tipo, referencia_id, monto, estado, expira_en, creada_en, liberada_en) VALUES
  ((SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000005'), NULL, NULL, 'ANTIFRAUDE', 'orden_retiro', NULL, 900.0, 'VIGENTE', now() + interval '29 days', now() - interval '6 hours', NULL)
ON CONFLICT DO NOTHING;

-- Retiro en revisión: pasó el segundo factor pero cayó en la ventana de enfriamiento de 24 horas de la política de billetera, y el motor antifraude pidió revisión humana.
INSERT INTO orden_retiro (cuenta_billetera_id, instrumento_destino_id, retencion_id, transaccion_id, aprobada_por, proveedor_id, monto_solicitado, costo_retiro, monto_neto, moneda, estado, mfa_verificado, requiere_doble_aprobacion, ventana_enfriamiento_hasta, referencia_proveedor, clave_idempotencia, solicitada_en, pagada_en, solicitada_por) VALUES
  ((SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000005'), (SELECT id FROM instrumento_fondeo WHERE hash_identificador = encode(digest('demo-instrumento-05', 'sha256'), 'hex')), (SELECT id FROM retencion_saldo WHERE cuenta_billetera_id = (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000005') AND estado = 'VIGENTE'), NULL, NULL, (SELECT id FROM proveedor_pago WHERE codigo = 'ACH_INTERBANCARIA'), 900.0, 0.0, 900.0, 'BOB', 'EN_REVISION', TRUE, FALSE, now() + interval '18 hours', NULL, 'demo-retiro-05', now() - interval '6 hours', NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000005'))
ON CONFLICT DO NOTHING;

-- Dos evaluaciones del motor: la del retiro que quedó en revisión y la de un aporte que pasó limpio. Guardar las reglas disparadas es lo que permite explicarle a la persona por qué se le frenó la operación.
INSERT INTO evaluacion_antifraude (transaccion_id, cuenta_billetera_id, revisada_por, motor_version, puntaje_riesgo, decision, reglas_disparadas, latencia_ms, evaluada_en, revisada_en) VALUES
  (NULL, (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000005'), NULL, 'v1', 62.0, 'REVISAR', '{"disparadas": ["VELOCIDAD_RETIROS", "RETIRO_INSTRUMENTO_NUEVO"], "puntajes": {"VELOCIDAD_RETIROS": 30, "RETIRO_INSTRUMENTO_NUEVO": 32}}'::jsonb, 84, now() - interval '6 hours', NULL),
  ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-aporte-p1-02'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000002'), NULL, 'v1', 8.0, 'PERMITIR', '{"disparadas": [], "puntajes": {}}'::jsonb, 41, now() - interval '36 days', NULL)
ON CONFLICT DO NOTHING;

-- Orden de autoridad ya levantada. Se conserva con número de oficio y hash del documento: es la prueba de que se cumplió y de cuándo se levantó.
INSERT INTO bloqueo_saldo (cuenta_billetera_id, retencion_id, levantada_por, autoridad, tipo_orden, numero_oficio, monto_bloqueado, alcance, documento_url, hash_documento, estado, recibido_en, vence_en, levantado_en) VALUES
  ((SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000003'), NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), 'JUZGADO', 'RETENCION', 'OF-JUZ-2026-0142', 200.0, 'PARCIAL', 'https://almacen.pasanaku.test/oficios/OF-JUZ-2026-0142.pdf', encode(digest('oficio-juz-2026-0142', 'sha256'), 'hex'), 'LEVANTADO', now() - interval '25 days', NULL, now() - interval '18 days')
ON CONFLICT DO NOTHING;

-- Una cuenta no se cierra con saldo ni con obligaciones abiertas: la solicitud queda EN_VALIDACION hasta que el grupo termine y el saldo tenga destino.
INSERT INTO solicitud_cierre_billetera (cuenta_billetera_id, orden_retiro_id, aprobada_por, motivo, saldo_al_solicitar, destino_saldo, estado, solicitada_en, ejecutada_en) VALUES
  ((SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000006'), NULL, NULL, 'El titular quiere cerrar la billetera al terminar el pasanaku', 600.0, 'RETIRO', 'EN_VALIDACION', now() - interval '5 days', NULL)
ON CONFLICT DO NOTHING;

-- El saldo lo movió el trigger, no el seeder
-- La retención de Bs 900 ya movió el saldo sola: al insertar la fila en
-- retencion_saldo, tg_retencion_sincroniza_saldo dejó BOB-0000005 con
-- saldo_disponible 500,00 y saldo_retenido 900,00. El saldo_total sigue
-- siendo 1.400,00 porque es una columna generada.
SELECT 1;
