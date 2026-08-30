-- Seis personas de prueba con su ciclo completo de identidad, debida diligencia y billetera.
-- GENERADO desde seeders/dev/02-usuarios-y-billeteras.json — no editar a mano.

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO usuario (codigo_publico, nombres, apellidos, telefono_e164, correo, fecha_nacimiento, estado, nivel_kyc, idioma, zona_horaria, fecha_registro) VALUES
      ('USR000001', 'María Elena', 'Quispe Mamani', '+59171000001', 'demo1@pasanaku.test', '1988-04-12', 'ACTIVO', 'COMPLETO', 'es-BO', 'America/La_Paz', now()),
      ('USR000002', 'Juan Carlos', 'Rojas Vargas', '+59171000002', 'demo2@pasanaku.test', '1991-09-30', 'ACTIVO', 'INTERMEDIO', 'es-BO', 'America/La_Paz', now()),
      ('USR000003', 'Ana Lucía', 'Choque Flores', '+59171000003', 'demo3@pasanaku.test', '1995-02-18', 'ACTIVO', 'INTERMEDIO', 'es-BO', 'America/La_Paz', now()),
      ('USR000004', 'Pedro Antonio', 'Gutiérrez Soto', '+59171000004', 'demo4@pasanaku.test', '1983-11-05', 'ACTIVO', 'BASICO', 'es-BO', 'America/La_Paz', now()),
      ('USR000005', 'Rosa', 'Condori Apaza', '+59171000005', 'demo5@pasanaku.test', '1979-07-22', 'ACTIVO', 'COMPLETO', 'es-BO', 'America/La_Paz', now()),
      ('USR000006', 'Luis Fernando', 'Mendoza Paz', '+59171000006', 'demo6@pasanaku.test', '1993-01-09', 'ACTIVO', 'INTERMEDIO', 'es-BO', 'America/La_Paz', now())
    ON CONFLICT (codigo_publico) DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO credencial_acceso (usuario_id, hash_contrasena, algoritmo, parametros_kdf, cambiada_en, requiere_cambio) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), repeat('b', 64), 'argon2id', '{"m": 65536, "t": 3, "p": 4}'::jsonb, now(), FALSE),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), repeat('b', 64), 'argon2id', '{"m": 65536, "t": 3, "p": 4}'::jsonb, now(), FALSE),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), repeat('b', 64), 'argon2id', '{"m": 65536, "t": 3, "p": 4}'::jsonb, now(), FALSE),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), repeat('b', 64), 'argon2id', '{"m": 65536, "t": 3, "p": 4}'::jsonb, now(), FALSE),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), repeat('b', 64), 'argon2id', '{"m": 65536, "t": 3, "p": 4}'::jsonb, now(), FALSE),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), repeat('b', 64), 'argon2id', '{"m": 65536, "t": 3, "p": 4}'::jsonb, now(), FALSE)
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

-- `version_llave` dice con qué versión de la llave maestra se cifró el dato: rotar la llave es cifrar de nuevo y subir el número, no perder el acceso a lo viejo.
DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO documento_identidad (usuario_id, tipo, numero_cifrado, hash_numero, pais_emision, url_anverso, hash_archivo, estado, version_llave) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'CI', 'cifrado-USR000001', encode(digest('USR000001','sha256'),'hex'), 'BO', 'https://demo.local/USR000001-a.jpg', repeat('a', 64), 'APROBADA', 1),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'CI', 'cifrado-USR000002', encode(digest('USR000002','sha256'),'hex'), 'BO', 'https://demo.local/USR000002-a.jpg', repeat('a', 64), 'APROBADA', 1),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 'CI', 'cifrado-USR000003', encode(digest('USR000003','sha256'),'hex'), 'BO', 'https://demo.local/USR000003-a.jpg', repeat('a', 64), 'APROBADA', 1),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'CI', 'cifrado-USR000004', encode(digest('USR000004','sha256'),'hex'), 'BO', 'https://demo.local/USR000004-a.jpg', repeat('a', 64), 'APROBADA', 1),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'CI', 'cifrado-USR000005', encode(digest('USR000005','sha256'),'hex'), 'BO', 'https://demo.local/USR000005-a.jpg', repeat('a', 64), 'APROBADA', 1),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'CI', 'cifrado-USR000006', encode(digest('USR000006','sha256'),'hex'), 'BO', 'https://demo.local/USR000006-a.jpg', repeat('a', 64), 'APROBADA', 1)
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO verificacion_kyc (usuario_id, nivel_solicitado, estado, iniciada_en) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'INTERMEDIO', 'APROBADA', now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'INTERMEDIO', 'APROBADA', now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 'INTERMEDIO', 'APROBADA', now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'INTERMEDIO', 'APROBADA', now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'INTERMEDIO', 'APROBADA', now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'INTERMEDIO', 'APROBADA', now())
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

-- Se declara ANTES de la debida diligencia: el trigger tg_ddd_pep la evalúa al insertarla
DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO declaracion_pep (usuario_id, es_pep, tipo_pep, cargo, institucion, pais, desde, declarada_en) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), TRUE, 'NACIONAL', 'Concejal municipal', 'Gobierno Autónomo Municipal', 'BO', '2024-01-01', now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), FALSE, NULL, NULL, NULL, NULL, NULL, now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), FALSE, NULL, NULL, NULL, NULL, NULL, now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), FALSE, NULL, NULL, NULL, NULL, NULL, now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), FALSE, NULL, NULL, NULL, NULL, NULL, now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), FALSE, NULL, NULL, NULL, NULL, NULL, now())
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO debida_diligencia (usuario_id, tipo, estado, documentos_requeridos, documentos_recibidos, iniciada_en, completada_en, aprobada_por, segunda_revision_por) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'REFORZADA', 'COMPLETA', '["CI", "SELFIE"]'::jsonb, '["CI", "SELFIE"]'::jsonb, now(), now(), (SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000006')),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'ESTANDAR', 'COMPLETA', '["CI", "SELFIE"]'::jsonb, '["CI", "SELFIE"]'::jsonb, now(), now(), NULL, NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 'ESTANDAR', 'COMPLETA', '["CI", "SELFIE"]'::jsonb, '["CI", "SELFIE"]'::jsonb, now(), now(), NULL, NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'SIMPLIFICADA', 'COMPLETA', '["CI", "SELFIE"]'::jsonb, '["CI", "SELFIE"]'::jsonb, now(), now(), NULL, NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'AMPLIADA', 'COMPLETA', '["CI", "SELFIE"]'::jsonb, '["CI", "SELFIE"]'::jsonb, now(), now(), NULL, NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'ESTANDAR', 'COMPLETA', '["CI", "SELFIE"]'::jsonb, '["CI", "SELFIE"]'::jsonb, now(), now(), NULL, NULL)
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO calificacion_riesgo_cliente (usuario_id, nivel, puntaje_total, nivel_dd_requerido, periodicidad_revision_meses, vigente_desde, proxima_revision, es_automatica, motivo_cambio) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'ALTO', 72, 'REFORZADA', 6, now(), (current_date + interval '180 days'), TRUE, 'Persona expuesta políticamente'),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'MEDIO', 45, 'ESTANDAR', 12, now(), (current_date + interval '180 days'), TRUE, NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 'BAJO', 22, 'ESTANDAR', 24, now(), (current_date + interval '180 days'), TRUE, NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'MEDIO', 45, 'SIMPLIFICADA', 12, now(), (current_date + interval '180 days'), TRUE, NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'ALTO', 72, 'AMPLIADA', 6, now(), (current_date + interval '180 days'), TRUE, NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'BAJO', 22, 'ESTANDAR', 24, now(), (current_date + interval '180 days'), TRUE, NULL)
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO expediente_cliente (usuario_id, completitud_porcentaje, documentos, retencion_hasta, estado, ultima_actualizacion) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 100, '["CI", "SELFIE", "DOMICILIO"]'::jsonb, (current_date + interval '3650 days'), 'COMPLETO', now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 100, '["CI", "SELFIE", "DOMICILIO"]'::jsonb, (current_date + interval '3650 days'), 'COMPLETO', now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 100, '["CI", "SELFIE", "DOMICILIO"]'::jsonb, (current_date + interval '3650 days'), 'COMPLETO', now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 100, '["CI", "SELFIE", "DOMICILIO"]'::jsonb, (current_date + interval '3650 days'), 'COMPLETO', now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 100, '["CI", "SELFIE", "DOMICILIO"]'::jsonb, (current_date + interval '3650 days'), 'COMPLETO', now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 100, '["CI", "SELFIE", "DOMICILIO"]'::jsonb, (current_date + interval '3650 days'), 'COMPLETO', now())
    ON CONFLICT (usuario_id) DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO perfil_transaccional (usuario_id, tipo, monto_mensual_estimado, cantidad_operaciones_estimada, actividad_economica, origen_fondos_declarado, moneda, fuente, vigente_desde, actualizado_en) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'DECLARADO', 8000, 12, 'Comerciante minorista', 'Ingresos del negocio', 'BOB', 'DECLARACION_APP', now(), now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'DECLARADO', 4500, 12, 'Asalariado', 'Salario', 'BOB', 'DECLARACION_APP', now(), now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 'DECLARADO', 3800, 12, 'Asalariada', 'Salario', 'BOB', 'DECLARACION_APP', now(), now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'DECLARADO', 2500, 12, 'Transportista', 'Ingresos del servicio', 'BOB', 'DECLARACION_APP', now(), now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'DECLARADO', 15000, 12, 'Comerciante mayorista', 'Ingresos del negocio', 'BOB', 'DECLARACION_APP', now(), now()),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'DECLARADO', 6000, 12, 'Profesional independiente', 'Honorarios', 'BOB', 'DECLARACION_APP', now(), now())
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO aceptacion_contrato (contrato_adhesion_id, usuario_id, version_aceptada, ip, hash_evidencia, aceptado_en) VALUES
      ((SELECT id FROM contrato_adhesion WHERE codigo = 'CTO-BILLETERA' AND version = 1), (SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 1, '190.129.0.1', repeat('a', 64), now()),
      ((SELECT id FROM contrato_adhesion WHERE codigo = 'CTO-BILLETERA' AND version = 1), (SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 1, '190.129.0.1', repeat('a', 64), now()),
      ((SELECT id FROM contrato_adhesion WHERE codigo = 'CTO-BILLETERA' AND version = 1), (SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 1, '190.129.0.1', repeat('a', 64), now()),
      ((SELECT id FROM contrato_adhesion WHERE codigo = 'CTO-BILLETERA' AND version = 1), (SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 1, '190.129.0.1', repeat('a', 64), now()),
      ((SELECT id FROM contrato_adhesion WHERE codigo = 'CTO-BILLETERA' AND version = 1), (SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 1, '190.129.0.1', repeat('a', 64), now()),
      ((SELECT id FROM contrato_adhesion WHERE codigo = 'CTO-BILLETERA' AND version = 1), (SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 1, '190.129.0.1', repeat('a', 64), now())
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO cuenta_billetera (numero_cuenta, tipo, usuario_id, moneda, estado, nivel_debida_diligencia, fecha_apertura, politica_billetera_id, cuenta_contable_id) VALUES
      ('BOB-0000001', 'USUARIO', (SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'BOB', 'ACTIVA', 'REFORZADA', now(), (SELECT id FROM politica_billetera WHERE codigo = 'GENERAL_BOB'), (SELECT id FROM cuenta_contable WHERE codigo = '2.1.01')),
      ('BOB-0000002', 'USUARIO', (SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'BOB', 'ACTIVA', 'ESTANDAR', now(), (SELECT id FROM politica_billetera WHERE codigo = 'GENERAL_BOB'), (SELECT id FROM cuenta_contable WHERE codigo = '2.1.01')),
      ('BOB-0000003', 'USUARIO', (SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 'BOB', 'ACTIVA', 'ESTANDAR', now(), (SELECT id FROM politica_billetera WHERE codigo = 'GENERAL_BOB'), (SELECT id FROM cuenta_contable WHERE codigo = '2.1.01')),
      ('BOB-0000004', 'USUARIO', (SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'BOB', 'ACTIVA', 'SIMPLIFICADA', now(), (SELECT id FROM politica_billetera WHERE codigo = 'GENERAL_BOB'), (SELECT id FROM cuenta_contable WHERE codigo = '2.1.01')),
      ('BOB-0000005', 'USUARIO', (SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'BOB', 'ACTIVA', 'AMPLIADA', now(), (SELECT id FROM politica_billetera WHERE codigo = 'GENERAL_BOB'), (SELECT id FROM cuenta_contable WHERE codigo = '2.1.01')),
      ('BOB-0000006', 'USUARIO', (SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'BOB', 'ACTIVA', 'ESTANDAR', now(), (SELECT id FROM politica_billetera WHERE codigo = 'GENERAL_BOB'), (SELECT id FROM cuenta_contable WHERE codigo = '2.1.01'))
    ON CONFLICT (numero_cuenta) DO NOTHING;
  END IF;
END $siembra$;
