-- Las dos cuentas de desarrollo con contraseña real: una participante y una de plataforma. Sirven para entrar a la app y al backoffice sin registrar a nadie a mano.
-- GENERADO desde seeders/dev/15-usuarios-dev.json — no editar a mano.

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO usuario (codigo_publico, nombres, apellidos, telefono_e164, correo, fecha_nacimiento, estado, nivel_kyc, idioma, zona_horaria, fecha_registro) VALUES
      ('USR000090', 'Pablo', 'Arauz Caballero', '+59171000090', 'a2020115468@estudiantes.upsa.edu.bo', '1998-06-15', 'ACTIVO', 'COMPLETO', 'es-BO', 'America/La_Paz', now() - interval '30 days'),
      ('USR000091', 'Pablo', 'Arauz Caballero (plataforma)', '+59171000091', 'pabliarca@gmail.com', '1998-06-15', 'ACTIVO', 'COMPLETO', 'es-BO', 'America/La_Paz', now() - interval '30 days')
    ON CONFLICT (codigo_publico) DO NOTHING;
  END IF;
END $siembra$;

-- Argon2id real de la contraseña de desarrollo. Sal fija a propósito: el archivo tiene que ser reproducible y su diff, legible.
DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO credencial_acceso (usuario_id, hash_contrasena, algoritmo, parametros_kdf, requiere_cambio, cambiada_en) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), '$argon2id$v=19$m=65536,t=3,p=4$YXBvcnRheWEtZGV2LTAwMQ$kpVHrAJ5acxqeNs4b0YoCYLkHKe5faS1ZmKvyg5NxEM', 'argon2id', '{"m": 65536, "t": 3, "p": 4}'::jsonb, FALSE, now() - interval '30 days'),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), '$argon2id$v=19$m=65536,t=3,p=4$YXBvcnRheWEtZGV2LTAwMg$dVEKPtg04c3pt5kPxfwwdlHocFbnNri5tlyGYitVskE', 'argon2id', '{"m": 65536, "t": 3, "p": 4}'::jsonb, FALSE, now() - interval '30 days')
    ON CONFLICT (usuario_id) DO NOTHING;
  END IF;
END $siembra$;

-- USR000090 entra a la app; USR000091 entra al backoffice.
DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO asignacion_rol (usuario_id, rol_id, ambito, ambito_id, otorgada_por, otorgada_en, vigente_hasta) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), (SELECT id FROM rol WHERE codigo = 'PARTICIPANTE'), 'GLOBAL', NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000009'), now() - interval '30 days', NULL),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), (SELECT id FROM rol WHERE codigo = 'ADMIN_PLATAFORMA'), 'GLOBAL', NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000009'), now() - interval '30 days', NULL)
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

-- USR000091 entra al backoffice, así que R-SEG-10 le exige TOTP confirmado: sin esta fila no puede abrir sesión, y eso es lo correcto (ADR-038). El secreto es un literal de demostración y no autentica nada.
DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO factor_mfa (usuario_id, tipo, secreto_cifrado, activo, es_principal, confirmado_en, ultimo_uso_en, version_llave) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), 'TOTP', 'enc:v1:demo-totp-admin-dev', TRUE, TRUE, now() - interval '30 days', now() - interval '2 hours', 1),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), 'RESPALDO', 'enc:v1:demo-respaldo-admin-dev', TRUE, FALSE, now() - interval '30 days', NULL, 1)
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

-- Los dos canales por defecto del proyecto: correo verificado y bandeja interna. Ni WhatsApp ni SMS: esos adaptadores están apagados.
DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO canal_vinculado (usuario_id, tipo, identificador, verificado, verificado_en, opt_in_en, rebotes_consecutivos, estado) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), 'CORREO', 'a2020115468@estudiantes.upsa.edu.bo', TRUE, now() - interval '30 days', now() - interval '30 days', 0, 'ACTIVO'),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), 'IN_APP', 'USR000090', TRUE, now() - interval '30 days', now() - interval '30 days', 0, 'ACTIVO'),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), 'CORREO', 'pabliarca@gmail.com', TRUE, now() - interval '30 days', now() - interval '30 days', 0, 'ACTIVO'),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), 'IN_APP', 'USR000091', TRUE, now() - interval '30 days', now() - interval '30 days', 0, 'ACTIVO')
    ON CONFLICT (identificador, tipo) DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO preferencia_notificacion (usuario_id, canal_primario, canal_respaldo, acepta_whatsapp, acepta_correo, acepta_sms, acepta_push, tope_diario_mensajes, frecuencia_resumen) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), 'PUSH_APP', 'CORREO', FALSE, TRUE, FALSE, TRUE, 20, 'DIARIO'),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), 'CORREO', 'PUSH_APP', FALSE, TRUE, FALSE, TRUE, 50, 'DIARIO')
    ON CONFLICT (usuario_id) DO NOTHING;
  END IF;
END $siembra$;

-- Android primero: el dispositivo de confianza de dev es un Android de gama media.
DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO dispositivo (usuario_id, huella, plataforma, modelo, version_app, token_push, es_confiable, autorizado_en, ultimo_uso_en) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), encode(digest('dev-android-USR000090', 'sha256'), 'hex'), 'ANDROID', 'Pixel 6a (emulador de dev)', '1.0.0', 'dev-push-USR000090', TRUE, now() - interval '30 days', now())
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO documento_identidad (usuario_id, tipo, numero_cifrado, version_llave, hash_numero, pais_emision, url_anverso, hash_archivo, estado) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), 'CI', 'cifrado-USR000090', 1, encode(digest('USR000090', 'sha256'), 'hex'), 'BO', 'local://identidad/dev/USR000090-anverso.jpg', repeat('a', 64), 'APROBADA'),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), 'CI', 'cifrado-USR000091', 1, encode(digest('USR000091', 'sha256'), 'hex'), 'BO', 'local://identidad/dev/USR000091-anverso.jpg', repeat('a', 64), 'APROBADA')
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO verificacion_kyc (usuario_id, nivel_solicitado, estado, iniciada_en, resuelta_en) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), 'AVANZADO', 'APROBADA', now() - interval '30 days', now() - interval '29 days'),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), 'AVANZADO', 'APROBADA', now() - interval '30 days', now() - interval '29 days')
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO declaracion_pep (usuario_id, es_pep, declarada_en) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), FALSE, now() - interval '30 days'),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), FALSE, now() - interval '30 days')
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO debida_diligencia (usuario_id, tipo, estado, documentos_requeridos, documentos_recibidos, iniciada_en, completada_en) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), 'ESTANDAR', 'COMPLETA', '["CI", "SELFIE"]'::jsonb, '["CI", "SELFIE"]'::jsonb, now() - interval '30 days', now() - interval '29 days'),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), 'ESTANDAR', 'COMPLETA', '["CI", "SELFIE"]'::jsonb, '["CI", "SELFIE"]'::jsonb, now() - interval '30 days', now() - interval '29 days')
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO calificacion_riesgo_cliente (usuario_id, nivel, puntaje_total, nivel_dd_requerido, periodicidad_revision_meses, vigente_desde, proxima_revision, es_automatica) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), 'BAJO', 18, 'ESTANDAR', 24, now() - interval '29 days', (current_date + interval '180 days'), TRUE),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), 'BAJO', 18, 'ESTANDAR', 24, now() - interval '29 days', (current_date + interval '180 days'), TRUE)
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO expediente_cliente (usuario_id, completitud_porcentaje, documentos, retencion_hasta, estado, ultima_actualizacion) VALUES
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), 100, '["CI", "SELFIE", "DOMICILIO"]'::jsonb, (current_date + interval '3650 days'), 'COMPLETO', now() - interval '29 days'),
      ((SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), 100, '["CI", "SELFIE", "DOMICILIO"]'::jsonb, (current_date + interval '3650 days'), 'COMPLETO', now() - interval '29 days')
    ON CONFLICT (usuario_id) DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO aceptacion_contrato (contrato_adhesion_id, usuario_id, version_aceptada, ip, hash_evidencia, aceptado_en) VALUES
      ((SELECT id FROM contrato_adhesion WHERE codigo = 'CTO-BILLETERA' AND version = 1), (SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), 1, '190.129.0.90', repeat('a', 64), now() - interval '30 days'),
      ((SELECT id FROM contrato_adhesion WHERE codigo = 'CTO-BILLETERA' AND version = 1), (SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), 1, '190.129.0.91', repeat('a', 64), now() - interval '30 days')
    ON CONFLICT DO NOTHING;
  END IF;
END $siembra$;

DO $siembra$
BEGIN
  IF current_setting('app.dev_sembrado', true) IS DISTINCT FROM 'si' THEN
    INSERT INTO cuenta_billetera (numero_cuenta, tipo, usuario_id, moneda, estado, nivel_debida_diligencia, fecha_apertura, politica_billetera_id, cuenta_contable_id) VALUES
      ('BOB-0000090', 'USUARIO', (SELECT id FROM usuario WHERE codigo_publico = 'USR000090'), 'BOB', 'ACTIVA', 'ESTANDAR', now() - interval '30 days', (SELECT id FROM politica_billetera WHERE codigo = 'GENERAL_BOB'), (SELECT id FROM cuenta_contable WHERE codigo = '2.1.01')),
      ('BOB-0000091', 'USUARIO', (SELECT id FROM usuario WHERE codigo_publico = 'USR000091'), 'BOB', 'ACTIVA', 'ESTANDAR', now() - interval '30 days', (SELECT id FROM politica_billetera WHERE codigo = 'GENERAL_BOB'), (SELECT id FROM cuenta_contable WHERE codigo = '2.1.01'))
    ON CONFLICT (numero_cuenta) DO NOTHING;
  END IF;
END $siembra$;
