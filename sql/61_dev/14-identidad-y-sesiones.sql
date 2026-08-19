-- La capa de identidad completa de los seis clientes: dirección, perfil financiero, consentimientos con hash, segundo factor, sesiones activas y revocadas, intentos de autenticación fallidos, reputación calculada y una restricción vigente por incumplimiento.
-- GENERADO desde seeders/dev/14-identidad-y-sesiones.json — no editar a mano.

INSERT INTO direccion_usuario (usuario_id, departamento, ciudad, zona, detalle, latitud, longitud) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'SANTA CRUZ', 'Santa Cruz de la Sierra', 'Plan Tres Mil', 'Calle 6 esq. Av. Paurito 45', -17.812345, -63.123456),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'SANTA CRUZ', 'Santa Cruz de la Sierra', 'Villa 1ro de Mayo', 'Av. Che Guevara 210', -17.798765, -63.154321),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 'LA PAZ', 'El Alto', 'Ciudad Satélite', 'Calle 5 nro. 88', -16.512345, -68.201234),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'SANTA CRUZ', 'Santa Cruz de la Sierra', 'Los Lotes', 'Calle Los Cusis 12', -17.823456, -63.098765),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'COCHABAMBA', 'Cochabamba', 'La Cancha', 'Av. Punata 340, puesto 12', -17.401234, -66.156789),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'SANTA CRUZ', 'Santa Cruz de la Sierra', 'Equipetrol', 'Calle H nro. 15, dpto. 3B', -17.76789, -63.190123)
ON CONFLICT DO NOTHING;

-- `es_pep` en USR000001 es lo que obliga a la debida diligencia reforzada y a la segunda revisión independiente (R-UIF-10).
INSERT INTO perfil_financiero (usuario_id, ocupacion, ingreso_mensual_declarado, capacidad_aporte_declarada, fuente_ingresos, es_pep, actualizado_en) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'Comerciante minorista', 8000.0, 1500.0, 'Venta de abarrotes en mercado', TRUE, now() - interval '120 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'Asalariado', 4500.0, 700.0, 'Salario en empresa privada', FALSE, now() - interval '110 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 'Asalariada', 3800.0, 600.0, 'Salario en institución educativa', FALSE, now() - interval '100 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'Transportista', 2500.0, 500.0, 'Servicio de transporte propio', FALSE, now() - interval '95 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'Comerciante mayorista', 15000.0, 3000.0, 'Distribución de productos de limpieza', FALSE, now() - interval '90 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'Profesional independiente', 6000.0, 1000.0, 'Honorarios por consultoría', FALSE, now() - interval '85 days')
ON CONFLICT DO NOTHING;

-- Consentimientos con versión, hash del documento, IP y fecha. USR000004 revocó el de marketing: es lo que hace que sus avisos comerciales se supriman y los obligatorios no.
INSERT INTO consentimiento (usuario_id, tipo, version_documento, hash_documento, otorgado, fecha_hora, ip_origen, agente_usuario, revocado_en) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'TERMINOS', 'v1', encode(digest('terminos-v1', 'sha256'), 'hex'), TRUE, now() - interval '120 days', '190.129.0.11', 'AportaYa/1.0 (Android 14)', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'TRATAMIENTO_DATOS', 'v1', encode(digest('tratamiento-datos-v1', 'sha256'), 'hex'), TRUE, now() - interval '120 days', '190.129.0.11', 'AportaYa/1.0 (Android 14)', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'TERMINOS', 'v1', encode(digest('terminos-v1', 'sha256'), 'hex'), TRUE, now() - interval '110 days', '190.129.0.12', 'AportaYa/1.0 (Android 14)', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 'TERMINOS', 'v1', encode(digest('terminos-v1', 'sha256'), 'hex'), TRUE, now() - interval '100 days', '190.129.0.13', 'AportaYa/1.0 (iOS 17)', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'TERMINOS', 'v1', encode(digest('terminos-v1', 'sha256'), 'hex'), TRUE, now() - interval '95 days', '190.129.0.14', 'AportaYa/1.0 (Android 13)', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'MARKETING', 'v1', encode(digest('marketing-v1', 'sha256'), 'hex'), TRUE, now() - interval '95 days', '190.129.0.14', 'AportaYa/1.0 (Android 13)', now() - interval '20 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'TERMINOS', 'v1', encode(digest('terminos-v1', 'sha256'), 'hex'), TRUE, now() - interval '90 days', '190.129.0.15', 'AportaYa/1.0 (Android 14)', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'TERMINOS', 'v1', encode(digest('terminos-v1', 'sha256'), 'hex'), TRUE, now() - interval '85 days', '190.129.0.16', 'AportaYa/1.0 (iOS 17)', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'MARKETING', 'v1', encode(digest('marketing-v1', 'sha256'), 'hex'), FALSE, now() - interval '85 days', '190.129.0.16', 'AportaYa/1.0 (iOS 17)', NULL)
ON CONFLICT DO NOTHING;

-- Segundo factor por WhatsApp para casi todos y TOTP para la organizadora, que además es persona expuesta políticamente. El secreto va cifrado: la columna guarda el sobre, no el secreto. `version_llave` dice con qué versión de la llave maestra se cifró el dato: rotar la llave es cifrar de nuevo y subir el número, no perder el acceso a lo viejo.
INSERT INTO factor_mfa (usuario_id, tipo, secreto_cifrado, activo, es_principal, confirmado_en, ultimo_uso_en, version_llave) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'TOTP', 'enc:v1:demo-totp-01', TRUE, TRUE, now() - interval '119 days', now() - interval '1 day', 1),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'RESPALDO', 'enc:v1:demo-respaldo-01', TRUE, FALSE, now() - interval '119 days', NULL, 1),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'WHATSAPP', 'enc:v1:demo-wa-02', TRUE, TRUE, now() - interval '109 days', now() - interval '2 days', 1),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 'WHATSAPP', 'enc:v1:demo-wa-03', TRUE, TRUE, now() - interval '99 days', now() - interval '3 days', 1),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'SMS', 'enc:v1:demo-sms-04', TRUE, TRUE, now() - interval '94 days', now() - interval '9 days', 1),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'WHATSAPP', 'enc:v1:demo-wa-05', TRUE, TRUE, now() - interval '89 days', now() - interval '6 hours', 1),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'WHATSAPP', 'enc:v1:demo-wa-06', TRUE, TRUE, now() - interval '84 days', now() - interval '4 days', 1)
ON CONFLICT DO NOTHING;

-- Cuatro sesiones vigentes y una revocada con motivo. La revocada es la que permite probar que cerrar sesión no borra el rastro.
INSERT INTO sesion (usuario_id, dispositivo_id, refresco_familia_id, iniciada_en, ultima_actividad_en, expira_en, ip_origen, geolocalizacion_aprox, revocada_en, motivo_revocacion) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), (SELECT id FROM dispositivo WHERE huella = encode(digest('demo-dispositivo-01', 'sha256'), 'hex')), NULL, now() - interval '1 day', now() - interval '2 hours', now() + interval '29 days', '190.129.0.11', 'Santa Cruz de la Sierra, BO', NULL, NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), (SELECT id FROM dispositivo WHERE huella = encode(digest('demo-dispositivo-02', 'sha256'), 'hex')), NULL, now() - interval '2 days', now() - interval '2 days', now() + interval '28 days', '190.129.0.12', 'Santa Cruz de la Sierra, BO', NULL, NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), (SELECT id FROM dispositivo WHERE huella = encode(digest('demo-dispositivo-04', 'sha256'), 'hex')), NULL, now() - interval '9 days', now() - interval '9 days', now() + interval '21 days', '190.129.0.14', 'Santa Cruz de la Sierra, BO', NULL, NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), (SELECT id FROM dispositivo WHERE huella = encode(digest('demo-dispositivo-05', 'sha256'), 'hex')), NULL, now() - interval '7 hours', now() - interval '5 hours', now() + interval '29 days', '190.129.0.15', 'Cochabamba, BO', NULL, NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), (SELECT id FROM dispositivo WHERE huella = encode(digest('demo-dispositivo-03', 'sha256'), 'hex')), NULL, now() - interval '30 days', now() - interval '26 days', now() - interval '1 day', '190.129.0.13', 'El Alto, BO', now() - interval '26 days', 'Cierre de sesión solicitado por el titular')
ON CONFLICT DO NOTHING;

-- Tres fallos seguidos contra el mismo identificador desde la misma IP, y el cuarto exitoso. Sin filas de fallo no se puede probar el bloqueo por intentos ni la detección de fuerza bruta.
INSERT INTO intento_autenticacion (usuario_id, identificador_usado, fecha_hora, exitoso, motivo_fallo, ip_origen, agente_usuario, huella_dispositivo, puntaje_riesgo) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), '+59171000004', now() - interval '10 days' - interval '4 minutes', FALSE, 'CONTRASENA_INCORRECTA', '181.114.20.55', 'AportaYa/1.0 (Android 13)', encode(digest('demo-dispositivo-04', 'sha256'), 'hex'), 22.0),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), '+59171000004', now() - interval '10 days' - interval '3 minutes', FALSE, 'CONTRASENA_INCORRECTA', '181.114.20.55', 'AportaYa/1.0 (Android 13)', encode(digest('demo-dispositivo-04', 'sha256'), 'hex'), 38.0),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), '+59171000004', now() - interval '10 days' - interval '2 minutes', FALSE, 'CONTRASENA_INCORRECTA', '181.114.20.55', 'AportaYa/1.0 (Android 13)', encode(digest('demo-dispositivo-04', 'sha256'), 'hex'), 55.0),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), '+59171000004', now() - interval '10 days', TRUE, NULL, '181.114.20.55', 'AportaYa/1.0 (Android 13)', encode(digest('demo-dispositivo-04', 'sha256'), 'hex'), 18.0),
  (NULL, '+59171000099', now() - interval '3 days', FALSE, 'USUARIO_INEXISTENTE', '45.190.77.12', 'curl/8.4.0', NULL, 88.0),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), '+59171000005', now() - interval '7 hours', TRUE, NULL, '190.129.0.15', 'AportaYa/1.0 (Android 14)', encode(digest('demo-dispositivo-05', 'sha256'), 'hex'), 12.0)
ON CONFLICT DO NOTHING;

-- Contraseñas anteriores, para impedir reutilizarlas. Solo el hash: no se guarda ni se puede recuperar la contraseña.
INSERT INTO historial_credencial (usuario_id, hash_contrasena, reemplazada_en) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), '$argon2id$v=19$m=65536,t=3,p=4$ZGVtb2hpc3QwNDE$0000000000000000000000000000000000000000000', now() - interval '10 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), '$argon2id$v=19$m=65536,t=3,p=4$ZGVtb2hpc3QwMTE$0000000000000000000000000000000000000000000', now() - interval '80 days')
ON CONFLICT DO NOTHING;

-- Puntaje calculado con el modelo v1 del catálogo mínimo 16. USR000004 arrastra el incumplimiento del período 2 y por eso baja de 700 a 615; el resto sube por puntualidad.
INSERT INTO reputacion_usuario (usuario_id, puntaje, indice_puntualidad, total_obligaciones, obligaciones_cumplidas, obligaciones_en_mora, incumplimientos_graves, grupos_completados, version_modelo, calculado_en) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 745.0, 100.0, 3, 3, 0, 0, 0, 'v1', now() - interval '12 hours'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 742.0, 100.0, 3, 3, 0, 0, 0, 'v1', now() - interval '12 hours'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), 742.0, 100.0, 3, 3, 0, 0, 0, 'v1', now() - interval '12 hours'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 615.0, 66.67, 3, 2, 1, 0, 0, 'v1', now() - interval '12 hours'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 747.0, 100.0, 3, 3, 0, 0, 0, 'v1', now() - interval '12 hours'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 742.0, 100.0, 3, 3, 0, 0, 0, 'v1', now() - interval '12 hours')
ON CONFLICT DO NOTHING;

-- Restricción vigente por incumplimiento: no puede unirse a grupos nuevos mientras la deuda esté abierta. La levanta una persona y el motivo queda escrito.
INSERT INTO restriccion_usuario (usuario_id, tipo, origen, referencia_origen_id, valor_limite, vigente_desde, vigente_hasta, levantada_por, motivo_levantamiento) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'NO_UNIRSE', 'INCUMPLIMIENTO', (SELECT id FROM registro_incumplimiento WHERE codigo_expediente = 'INC-DEMO-0001'), NULL, now() - interval '3 days', NULL, NULL, NULL)
ON CONFLICT DO NOTHING;

-- Referencias verificadas por llamada. `acepta_ser_avalista` es lo que habilita a la persona a respaldar un cupo, y pesa en el emparejamiento cuando alguien no tiene historial en la plataforma.
INSERT INTO referencia_personal (usuario_id, nombre, telefono, relacion, verificada, verificada_en, acepta_ser_avalista) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), 'Teresa Gutiérrez Soto', '+59171000404', 'FAMILIAR', TRUE, now() - interval '94 days', TRUE),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), 'Carlos Mendoza Paz', '+59171000606', 'FAMILIAR', TRUE, now() - interval '84 days', FALSE),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), 'Marta Rojas Vargas', '+59171000202', 'LABORAL', FALSE, NULL, FALSE)
ON CONFLICT DO NOTHING;
