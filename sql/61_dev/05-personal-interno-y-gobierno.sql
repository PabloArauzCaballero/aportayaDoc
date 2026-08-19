-- El backoffice: tres personas internas con rol asignado, el oficial de cumplimiento designado por acta y las políticas internas puestas en vigencia por el directorio. Sin esto no hay quién autorice una entrega ni quién trate una alerta.
-- GENERADO desde seeders/dev/05-personal-interno-y-gobierno.json — no editar a mano.

INSERT INTO usuario (codigo_publico, nombres, apellidos, telefono_e164, correo, fecha_nacimiento, estado, nivel_kyc, idioma, zona_horaria, fecha_registro) VALUES
  ('USR000007', 'Silvia', 'Terceros Peña', '+59171000007', 'cumplimiento@aportaya.test', '1982-03-15', 'ACTIVO', 'COMPLETO', 'es-BO', 'America/La_Paz', now() - interval '150 days'),
  ('USR000008', 'Marcelo', 'Arce Villarroel', '+59171000008', 'tesoreria@aportaya.test', '1986-08-02', 'ACTIVO', 'COMPLETO', 'es-BO', 'America/La_Paz', now() - interval '150 days'),
  ('USR000009', 'Gabriela', 'Nogales Sandoval', '+59171000009', 'riesgos@aportaya.test', '1980-12-11', 'ACTIVO', 'COMPLETO', 'es-BO', 'America/La_Paz', now() - interval '150 days')
ON CONFLICT (codigo_publico) DO NOTHING;

-- Argon2id, nunca la contraseña. El hash de demo no corresponde a ninguna contraseña real y no sirve para entrar.
INSERT INTO credencial_acceso (usuario_id, hash_contrasena, algoritmo, parametros_kdf, requiere_cambio, cambiada_en) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), '$argon2id$v=19$m=65536,t=3,p=4$ZGVtb3NhbHQwMDc$0000000000000000000000000000000000000000000', 'argon2id', '{"m": 65536, "t": 3, "p": 4}'::jsonb, TRUE, now() - interval '150 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000008'), '$argon2id$v=19$m=65536,t=3,p=4$ZGVtb3NhbHQwMDg$0000000000000000000000000000000000000000000', 'argon2id', '{"m": 65536, "t": 3, "p": 4}'::jsonb, TRUE, now() - interval '150 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000009'), '$argon2id$v=19$m=65536,t=3,p=4$ZGVtb3NhbHQwMDk$0000000000000000000000000000000000000000000', 'argon2id', '{"m": 65536, "t": 3, "p": 4}'::jsonb, TRUE, now() - interval '150 days')
ON CONFLICT DO NOTHING;

-- Los roles vienen del catálogo mínimo 10 y traen su matriz de permisos. Nadie acumula ENTREGA_AUTORIZAR y ENTREGA_EJECUTAR, y `ck_asignacion_no_autoasignada` impide que alguien se otorgue un rol a sí mismo: el oficial de cumplimiento recibe el suyo de riesgos y viceversa.
INSERT INTO asignacion_rol (usuario_id, rol_id, ambito, ambito_id, otorgada_por, otorgada_en, vigente_hasta) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), (SELECT id FROM rol WHERE codigo = 'OFICIAL_CUMPLIMIENTO'), 'GLOBAL', NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000009'), now() - interval '150 days', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000008'), (SELECT id FROM rol WHERE codigo = 'TESORERIA'), 'GLOBAL', NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), now() - interval '150 days', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000009'), (SELECT id FROM rol WHERE codigo = 'RESPONSABLE_RIESGOS'), 'GLOBAL', NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), now() - interval '150 days', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), (SELECT id FROM rol WHERE codigo = 'ORGANIZADOR'), 'GRUPO', (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), now() - interval '45 days', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), (SELECT id FROM rol WHERE codigo = 'PARTICIPANTE'), 'GRUPO', (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), now() - interval '45 days', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), (SELECT id FROM rol WHERE codigo = 'PARTICIPANTE'), 'GRUPO', (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), now() - interval '45 days', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), (SELECT id FROM rol WHERE codigo = 'PARTICIPANTE'), 'GRUPO', (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), now() - interval '45 days', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), (SELECT id FROM rol WHERE codigo = 'PARTICIPANTE'), 'GRUPO', (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), now() - interval '45 days', NULL),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), (SELECT id FROM rol WHERE codigo = 'PARTICIPANTE'), 'GRUPO', (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), now() - interval '45 days', NULL)
ON CONFLICT DO NOTHING;

-- Un dispositivo de confianza por cliente: es lo que permite exigir dispositivo conocido en los propósitos de token que lo piden.
INSERT INTO dispositivo (usuario_id, huella, plataforma, modelo, version_app, token_push, es_confiable, autorizado_en, ultimo_uso_en) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), encode(digest('demo-dispositivo-01', 'sha256'), 'hex'), 'ANDROID', 'Moto G54', '1.0.0', 'demo-push-01', TRUE, now() - interval '120 days', now() - interval '1 day'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), encode(digest('demo-dispositivo-02', 'sha256'), 'hex'), 'ANDROID', 'Samsung A15', '1.0.0', 'demo-push-02', TRUE, now() - interval '110 days', now() - interval '2 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), encode(digest('demo-dispositivo-03', 'sha256'), 'hex'), 'IOS', 'iPhone 13', '1.0.0', 'demo-push-03', TRUE, now() - interval '100 days', now() - interval '3 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), encode(digest('demo-dispositivo-04', 'sha256'), 'hex'), 'ANDROID', 'Xiaomi Redmi 12', '1.0.0', 'demo-push-04', TRUE, now() - interval '95 days', now() - interval '9 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), encode(digest('demo-dispositivo-05', 'sha256'), 'hex'), 'ANDROID', 'Motorola E13', '1.0.0', 'demo-push-05', TRUE, now() - interval '90 days', now() - interval '1 day'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000006'), encode(digest('demo-dispositivo-06', 'sha256'), 'hex'), 'IOS', 'iPhone SE', '1.0.0', 'demo-push-06', TRUE, now() - interval '85 days', now() - interval '4 days')
ON CONFLICT DO NOTHING;

-- Dos actas reales del gobierno interno: la que aprueba el cuerpo normativo y la que designa al oficial de cumplimiento. Los efectos se aplican con el acta, no antes.
INSERT INTO acta_comite (comite_gobierno_id, elaborada_por, numero, fecha, asistentes, cumple_quorum, temas_tratados, decisiones, url_documento, hash_documento) VALUES
  ((SELECT id FROM comite_gobierno WHERE tipo = 'DIRECTORIO'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), 'ACTA-DIR-2026-001', (current_date + interval '-150 days'), '[{"cargo": "Presidente del Directorio", "voto": "A_FAVOR"}, {"cargo": "Director titular 1", "voto": "A_FAVOR"}, {"cargo": "Director titular 2", "voto": "A_FAVOR"}]'::jsonb, TRUE, '["Aprobación del cuerpo normativo interno", "Designación del oficial de cumplimiento titular"]'::jsonb, '[{"tema": "Cuerpo normativo interno", "resultado": "APROBADO", "votos_a_favor": 3, "votos_en_contra": 0, "abstenciones": 0}, {"tema": "Designación de oficial de cumplimiento", "resultado": "APROBADO", "votos_a_favor": 3, "votos_en_contra": 0, "abstenciones": 0}]'::jsonb, 'https://almacen.pasanaku.test/actas/ACTA-DIR-2026-001.pdf', encode(digest('acta-dir-2026-001', 'sha256'), 'hex')),
  ((SELECT id FROM comite_gobierno WHERE tipo = 'CUMPLIMIENTO'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), 'ACTA-CUM-2026-001', (current_date + interval '-30 days'), '[{"cargo": "Oficial de Cumplimiento", "voto": "A_FAVOR"}, {"cargo": "Gerente General", "voto": "A_FAVOR"}, {"cargo": "Miembro del Directorio", "voto": "ABSTENCION"}]'::jsonb, TRUE, '["Revisión de alertas del período", "Calibración de umbrales de monitoreo"]'::jsonb, '[{"tema": "Mantener los umbrales vigentes", "resultado": "APROBADO", "votos_a_favor": 2, "votos_en_contra": 0, "abstenciones": 1}]'::jsonb, 'https://almacen.pasanaku.test/actas/ACTA-CUM-2026-001.pdf', encode(digest('acta-cum-2026-001', 'sha256'), 'hex'))
ON CONFLICT DO NOTHING;

INSERT INTO oficial_cumplimiento (usuario_id, tipo, fecha_designacion, acta_designacion, comunicada_al_regulador_en, fecha_baja, activo) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), 'TITULAR', (current_date + interval '-150 days'), 'ACTA-DIR-2026-001', (current_date + interval '-145 days'), NULL, TRUE),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000009'), 'SUPLENTE', (current_date + interval '-150 days'), 'ACTA-DIR-2026-001', (current_date + interval '-145 days'), NULL, TRUE)
ON CONFLICT DO NOTHING;

-- Los cargos que la norma obliga a designar y a comunicar al organismo. La fecha de comunicación es la que se exhibe en una inspección.
INSERT INTO designacion_regulatoria (usuario_id, acta_comite_id, cargo, tipo, fecha_designacion, organismo_comunicado, comunicada_al_organismo_en, fecha_baja, activo) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), (SELECT id FROM acta_comite WHERE numero = 'ACTA-DIR-2026-001'), 'OFICIAL_CUMPLIMIENTO', 'TITULAR', (current_date + interval '-150 days'), 'UIF', (current_date + interval '-145 days'), NULL, TRUE),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000009'), (SELECT id FROM acta_comite WHERE numero = 'ACTA-DIR-2026-001'), 'RESPONSABLE_RIESGOS', 'TITULAR', (current_date + interval '-150 days'), 'ASFI', (current_date + interval '-145 days'), NULL, TRUE),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000008'), (SELECT id FROM acta_comite WHERE numero = 'ACTA-DIR-2026-001'), 'RESPONSABLE_PUNTO_RECLAMO', 'TITULAR', (current_date + interval '-150 days'), 'ASFI', (current_date + interval '-145 days'), NULL, TRUE)
ON CONFLICT DO NOTHING;

-- Solo en desarrollo. En producción una política pasa a VIGENTE cuando existe el acta real: ck_politica_acta impide hacerlo sin ella, y este UPDATE funciona justamente porque el acta se acaba de registrar arriba
UPDATE politica_interna
   SET estado = 'VIGENTE',
       aprobada_por_directorio = TRUE,
       acta_comite_id = (SELECT id FROM acta_comite WHERE numero = 'ACTA-DIR-2026-001'),
       responsable_id = (SELECT id FROM usuario WHERE codigo_publico = 'USR000007')
 WHERE estado = 'EN_APROBACION';

-- Un control sin dueño no se ejecuta. En desarrollo se asigna todo a riesgos; en producción cada control tiene su responsable nominal
UPDATE control_interno
   SET responsable_id = (SELECT id FROM usuario WHERE codigo_publico = 'USR000009')
 WHERE responsable_id IS NULL;

UPDATE plan_continuidad
   SET responsable_id = (SELECT id FROM usuario WHERE codigo_publico = 'USR000009')
 WHERE responsable_id IS NULL;
