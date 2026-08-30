-- Por dónde entra el dinero: instrumentos de fondeo verificados, un punto de atención en efectivo, la cuenta de billetera del fondo de garantía y las seis recargas que financian el grupo.
-- GENERADO desde seeders/dev/06-instrumentos-y-recargas.json — no editar a mano.

-- El fondo de garantía tiene cuenta propia: no es del grupo ni del organizador (ck_cuenta_titularidad exige titular nulo para este tipo).
DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO cuenta_billetera (numero_cuenta, tipo, moneda, estado, nivel_debida_diligencia, fecha_apertura, cuenta_contable_id) VALUES
      ('FDG-0000001', 'FONDO_GARANTIA', 'BOB', 'ACTIVA', 'REFORZADA', now() - interval '45 days', (SELECT id FROM cuenta_contable WHERE codigo = '2.1.03'))
    ON CONFLICT (numero_cuenta) DO NOTHING;
  END IF;
END $siembra$;

-- Instrumentos verificados y con titular coincidente. ck_instrumento_sin_pan impide guardar el número completo: solo enmascarado y hash.
DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO instrumento_fondeo (usuario_id, tipo, entidad_financiera, token_proveedor, hash_identificador, enmascarado, titular_nombre, titular_documento, titular_coincide, moneda, es_principal, estado_verificacion, metodo_verificacion, verificado_en, bloqueado_hasta) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'CUENTA_BANCARIA', 'BANCO DEMO S.A.', NULL, encode(digest('demo-instrumento-01', 'sha256'), 'hex'), '****4001', 'Titular demo USR000001', '9000001', TRUE, 'BOB', TRUE, 'VERIFICADO', 'MICRODEPOSITO', now() - interval '46 days', NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'CUENTA_BANCARIA', 'BANCO DEMO S.A.', NULL, encode(digest('demo-instrumento-02', 'sha256'), 'hex'), '****4002', 'Titular demo USR000002', '9000002', TRUE, 'BOB', TRUE, 'VERIFICADO', 'MICRODEPOSITO', now() - interval '46 days', NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 'CUENTA_BANCARIA', 'BANCO DEMO S.A.', NULL, encode(digest('demo-instrumento-03', 'sha256'), 'hex'), '****4003', 'Titular demo USR000003', '9000003', TRUE, 'BOB', TRUE, 'VERIFICADO', 'MICRODEPOSITO', now() - interval '46 days', NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'CUENTA_BANCARIA', 'BANCO UNION S.A.', NULL, encode(digest('demo-instrumento-04', 'sha256'), 'hex'), '****4004', 'Titular demo USR000004', '9000004', TRUE, 'BOB', TRUE, 'VERIFICADO', 'MICRODEPOSITO', now() - interval '46 days', NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'CUENTA_BANCARIA', 'BANCO DEMO S.A.', NULL, encode(digest('demo-instrumento-05', 'sha256'), 'hex'), '****4005', 'Titular demo USR000005', '9000005', TRUE, 'BOB', TRUE, 'VERIFICADO', 'MICRODEPOSITO', now() - interval '46 days', NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'CUENTA_BANCARIA', 'BANCO DEMO S.A.', NULL, encode(digest('demo-instrumento-06', 'sha256'), 'hex'), '****4006', 'Titular demo USR000006', '9000006', TRUE, 'BOB', TRUE, 'VERIFICADO', 'MICRODEPOSITO', now() - interval '46 days', NULL)
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO extracto_bancario (proveedor_id, cuenta, fecha_desde, fecha_hasta, saldo_inicial, saldo_final, archivo_url, importado_en, importado_por) VALUES
      ((SELECT id FROM proveedor_pago WHERE codigo = 'BANCO_CUSTODIO'), '****4321', (current_date + interval '-45 days'), (current_date + interval '-1 days'), 0.0, 9900.0, 'https://almacen.pasanaku.test/extractos/demo-45d.csv', now() - interval '1 day', (SELECT id FROM usuario WHERE codigo_publico = 'USR000008'))
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

-- Lo que el banco dice que pasó. Contra esto se concilia todo lo demás.
DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO movimiento_bancario (extracto_id, fecha_movimiento, monto, moneda, glosa, referencia_banco, cuenta_origen, conciliado) VALUES
      ((SELECT id FROM extracto_bancario WHERE archivo_url = 'https://almacen.pasanaku.test/extractos/demo-45d.csv'), (current_date + interval '-44 days'), 1200.0, 'BOB', 'Abono recarga billetera USR000001', 'BCO-REC-01', '****4001', TRUE),
      ((SELECT id FROM extracto_bancario WHERE archivo_url = 'https://almacen.pasanaku.test/extractos/demo-45d.csv'), (current_date + interval '-44 days'), 1200.0, 'BOB', 'Abono recarga billetera USR000002', 'BCO-REC-02', '****4002', TRUE),
      ((SELECT id FROM extracto_bancario WHERE archivo_url = 'https://almacen.pasanaku.test/extractos/demo-45d.csv'), (current_date + interval '-44 days'), 1200.0, 'BOB', 'Abono recarga billetera USR000003', 'BCO-REC-03', '****4003', TRUE),
      ((SELECT id FROM extracto_bancario WHERE archivo_url = 'https://almacen.pasanaku.test/extractos/demo-45d.csv'), (current_date + interval '-44 days'), 600.0, 'BOB', 'Abono recarga billetera USR000004', 'BCO-REC-04', '****4004', TRUE),
      ((SELECT id FROM extracto_bancario WHERE archivo_url = 'https://almacen.pasanaku.test/extractos/demo-45d.csv'), (current_date + interval '-44 days'), 2000.0, 'BOB', 'Abono recarga billetera USR000005', 'BCO-REC-05', '****4005', TRUE),
      ((SELECT id FROM extracto_bancario WHERE archivo_url = 'https://almacen.pasanaku.test/extractos/demo-45d.csv'), (current_date + interval '-44 days'), 1200.0, 'BOB', 'Abono recarga billetera USR000006', 'BCO-REC-06', '****4006', TRUE),
      ((SELECT id FROM extracto_bancario WHERE archivo_url = 'https://almacen.pasanaku.test/extractos/demo-45d.csv'), (current_date + interval '-12 days'), 500.0, 'BOB', 'Cobro QR aporte periodo 2 USR000001', 'BCO-P2-01', '****4001', TRUE),
      ((SELECT id FROM extracto_bancario WHERE archivo_url = 'https://almacen.pasanaku.test/extractos/demo-45d.csv'), (current_date + interval '-12 days'), 500.0, 'BOB', 'Cobro QR aporte periodo 2 USR000002', 'BCO-P2-02', '****4002', TRUE),
      ((SELECT id FROM extracto_bancario WHERE archivo_url = 'https://almacen.pasanaku.test/extractos/demo-45d.csv'), (current_date + interval '-11 days'), 500.0, 'BOB', 'Cobro QR aporte periodo 2 USR000003', 'BCO-P2-03', '****4003', TRUE),
      ((SELECT id FROM extracto_bancario WHERE archivo_url = 'https://almacen.pasanaku.test/extractos/demo-45d.csv'), (current_date + interval '-13 days'), 500.0, 'BOB', 'Cobro QR aporte periodo 2 USR000005', 'BCO-P2-05', '****4005', TRUE),
      ((SELECT id FROM extracto_bancario WHERE archivo_url = 'https://almacen.pasanaku.test/extractos/demo-45d.csv'), (current_date + interval '-10 days'), 500.0, 'BOB', 'Cobro QR aporte periodo 2 USR000006', 'BCO-P2-06', '****4006', TRUE)
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO orden_recarga (cuenta_billetera_id, instrumento_fondeo_id, proveedor_id, pago_id, transaccion_id, monto_bruto, costo_proveedor, monto_acreditado, moneda, estado, referencia_externa, clave_idempotencia, solicitada_en, acreditada_en, expira_en) VALUES
      ((SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000001'), (SELECT id FROM instrumento_fondeo WHERE hash_identificador = encode(digest('demo-instrumento-01', 'sha256'), 'hex')), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), NULL, NULL, 1200.0, 0.0, 1200.0, 'BOB', 'ACREDITADA', 'QR-000001-INI', 'demo-recarga-01', now() - interval '44 days', now() - interval '44 days', now() - interval '43 days'),
      ((SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000002'), (SELECT id FROM instrumento_fondeo WHERE hash_identificador = encode(digest('demo-instrumento-02', 'sha256'), 'hex')), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), NULL, NULL, 1200.0, 0.0, 1200.0, 'BOB', 'ACREDITADA', 'QR-000002-INI', 'demo-recarga-02', now() - interval '44 days', now() - interval '44 days', now() - interval '43 days'),
      ((SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000003'), (SELECT id FROM instrumento_fondeo WHERE hash_identificador = encode(digest('demo-instrumento-03', 'sha256'), 'hex')), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), NULL, NULL, 1200.0, 0.0, 1200.0, 'BOB', 'ACREDITADA', 'QR-000003-INI', 'demo-recarga-03', now() - interval '44 days', now() - interval '44 days', now() - interval '43 days'),
      ((SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000004'), (SELECT id FROM instrumento_fondeo WHERE hash_identificador = encode(digest('demo-instrumento-04', 'sha256'), 'hex')), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), NULL, NULL, 600.0, 0.0, 600.0, 'BOB', 'ACREDITADA', 'QR-DEMO-04', 'demo-recarga-04', now() - interval '44 days', now() - interval '44 days', now() - interval '43 days'),
      ((SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000005'), (SELECT id FROM instrumento_fondeo WHERE hash_identificador = encode(digest('demo-instrumento-05', 'sha256'), 'hex')), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), NULL, NULL, 2000.0, 0.0, 2000.0, 'BOB', 'ACREDITADA', 'QR-000005-INI', 'demo-recarga-05', now() - interval '44 days', now() - interval '44 days', now() - interval '43 days'),
      ((SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000006'), (SELECT id FROM instrumento_fondeo WHERE hash_identificador = encode(digest('demo-instrumento-06', 'sha256'), 'hex')), (SELECT id FROM proveedor_pago WHERE codigo = 'QR_INTEROP'), NULL, NULL, 1200.0, 0.0, 1200.0, 'BOB', 'ACREDITADA', 'QR-000006-INI', 'demo-recarga-06', now() - interval '44 days', now() - interval '44 days', now() - interval '43 days')
    ON CONFLICT (cuenta_billetera_id, clave_idempotencia) DO NOTHING;
  END IF;
END $siembra$;

-- hash_registro va vacío a propósito: lo calcula la base al insertar (R-AUD-03). Escribirlo desde el seeder sería fabricar la cadena.
DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO transaccion_billetera (tipo, estado, moneda, monto_total, grupo_id, origen_tipo, origen_id, canal, clave_idempotencia, hash_registro, ocurrida_en, registrada_en) VALUES
      ('RECARGA', 'APLICADA', 'BOB', 1200.0, NULL, 'ORDEN_RECARGA', (SELECT id FROM orden_recarga WHERE clave_idempotencia = 'demo-recarga-01'), 'APP', 'demo-recarga-01', '', now() - interval '44 days', now() - interval '44 days'),
      ('RECARGA', 'APLICADA', 'BOB', 1200.0, NULL, 'ORDEN_RECARGA', (SELECT id FROM orden_recarga WHERE clave_idempotencia = 'demo-recarga-02'), 'APP', 'demo-recarga-02', '', now() - interval '44 days', now() - interval '44 days'),
      ('RECARGA', 'APLICADA', 'BOB', 1200.0, NULL, 'ORDEN_RECARGA', (SELECT id FROM orden_recarga WHERE clave_idempotencia = 'demo-recarga-03'), 'APP', 'demo-recarga-03', '', now() - interval '44 days', now() - interval '44 days'),
      ('RECARGA', 'APLICADA', 'BOB', 600.0, NULL, 'ORDEN_RECARGA', (SELECT id FROM orden_recarga WHERE clave_idempotencia = 'demo-recarga-04'), 'APP', 'demo-recarga-04', '', now() - interval '44 days', now() - interval '44 days'),
      ('RECARGA', 'APLICADA', 'BOB', 2000.0, NULL, 'ORDEN_RECARGA', (SELECT id FROM orden_recarga WHERE clave_idempotencia = 'demo-recarga-05'), 'APP', 'demo-recarga-05', '', now() - interval '44 days', now() - interval '44 days'),
      ('RECARGA', 'APLICADA', 'BOB', 1200.0, NULL, 'ORDEN_RECARGA', (SELECT id FROM orden_recarga WHERE clave_idempotencia = 'demo-recarga-06'), 'APP', 'demo-recarga-06', '', now() - interval '44 days', now() - interval '44 days')
    ON CONFLICT (COALESCE(iniciada_por, '00000000-0000-0000-0000-000000000000'::uuid), origen_tipo, clave_idempotencia) DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO movimiento_billetera (transaccion_id, cuenta_billetera_id, orden, sentido, monto, saldo_disponible_posterior, saldo_retenido_posterior, glosa, registrado_en) VALUES
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-01'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000001'), 1, 'CREDITO', 1200.0, 1200.0, 0.0, 'Recarga acreditada — referencia QR-000001-INI', now() - interval '44 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-01'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'SYS-CUSTODIA'), 2, 'DEBITO', 1200.0, -1200.0, 0.0, 'Contrapartida en la cuenta puente de custodia — recarga 01', now() - interval '44 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-02'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000002'), 1, 'CREDITO', 1200.0, 1200.0, 0.0, 'Recarga acreditada — referencia QR-000002-INI', now() - interval '44 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-02'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'SYS-CUSTODIA'), 2, 'DEBITO', 1200.0, -2400.0, 0.0, 'Contrapartida en la cuenta puente de custodia — recarga 02', now() - interval '44 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-03'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000003'), 1, 'CREDITO', 1200.0, 1200.0, 0.0, 'Recarga acreditada — referencia QR-000003-INI', now() - interval '44 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-03'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'SYS-CUSTODIA'), 2, 'DEBITO', 1200.0, -3600.0, 0.0, 'Contrapartida en la cuenta puente de custodia — recarga 03', now() - interval '44 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-04'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000004'), 1, 'CREDITO', 600.0, 600.0, 0.0, 'Recarga acreditada — referencia QR-000004-INI', now() - interval '44 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-04'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'SYS-CUSTODIA'), 2, 'DEBITO', 600.0, -4200.0, 0.0, 'Contrapartida en la cuenta puente de custodia — recarga 04', now() - interval '44 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-05'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000005'), 1, 'CREDITO', 2000.0, 2000.0, 0.0, 'Recarga acreditada — referencia QR-000005-INI', now() - interval '44 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-05'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'SYS-CUSTODIA'), 2, 'DEBITO', 2000.0, -6200.0, 0.0, 'Contrapartida en la cuenta puente de custodia — recarga 05', now() - interval '44 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-06'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000006'), 1, 'CREDITO', 1200.0, 1200.0, 0.0, 'Recarga acreditada — referencia QR-000006-INI', now() - interval '44 days'),
      ((SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-06'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'SYS-CUSTODIA'), 2, 'DEBITO', 1200.0, -7400.0, 0.0, 'Contrapartida en la cuenta puente de custodia — recarga 06', now() - interval '44 days')
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

-- La orden y su transacción se apuntan mutuamente; el enlace se cierra después de insertar ambas
UPDATE orden_recarga o SET transaccion_id = t.id FROM transaccion_billetera t
 WHERE t.clave_idempotencia = o.clave_idempotencia AND o.transaccion_id IS NULL;

-- El lado bancario de cada recarga, ya conciliado contra el extracto
DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO movimiento_custodia (cuenta_custodia_id, movimiento_bancario_id, fecha_valor, tipo, sentido, monto, referencia_bancaria, glosa, conciliado, registrado_en) VALUES
      ((SELECT id FROM cuenta_custodia WHERE contrato_referencia = 'FID-DEMO-001'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-REC-01'), (current_date + interval '-44 days'), 'INGRESO', 'CREDITO', 1200.0, 'BCO-REC-01', 'Abono por recarga de billetera del titular USR000001', TRUE, now() - interval '44 days'),
      ((SELECT id FROM cuenta_custodia WHERE contrato_referencia = 'FID-DEMO-001'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-REC-02'), (current_date + interval '-44 days'), 'INGRESO', 'CREDITO', 1200.0, 'BCO-REC-02', 'Abono por recarga de billetera del titular USR000002', TRUE, now() - interval '44 days'),
      ((SELECT id FROM cuenta_custodia WHERE contrato_referencia = 'FID-DEMO-001'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-REC-03'), (current_date + interval '-44 days'), 'INGRESO', 'CREDITO', 1200.0, 'BCO-REC-03', 'Abono por recarga de billetera del titular USR000003', TRUE, now() - interval '44 days'),
      ((SELECT id FROM cuenta_custodia WHERE contrato_referencia = 'FID-DEMO-001'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-REC-04'), (current_date + interval '-44 days'), 'INGRESO', 'CREDITO', 600.0, 'BCO-REC-04', 'Abono por recarga de billetera del titular USR000004', TRUE, now() - interval '44 days'),
      ((SELECT id FROM cuenta_custodia WHERE contrato_referencia = 'FID-DEMO-001'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-REC-05'), (current_date + interval '-44 days'), 'INGRESO', 'CREDITO', 2000.0, 'BCO-REC-05', 'Abono por recarga de billetera del titular USR000005', TRUE, now() - interval '44 days'),
      ((SELECT id FROM cuenta_custodia WHERE contrato_referencia = 'FID-DEMO-001'), (SELECT id FROM movimiento_bancario WHERE referencia_banco = 'BCO-REC-06'), (current_date + interval '-44 days'), 'INGRESO', 'CREDITO', 1200.0, 'BCO-REC-06', 'Abono por recarga de billetera del titular USR000006', TRUE, now() - interval '44 days')
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;
