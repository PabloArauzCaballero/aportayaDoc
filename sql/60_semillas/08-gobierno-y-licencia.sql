-- Gobierno, licencia y atención al consumidor financiero.
-- GENERADO desde seeders/minimos/08-gobierno-y-licencia.json — no editar a mano.

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM licencia_regulatoria) THEN
  INSERT INTO licencia_regulatoria (organismo, tipo, categoria_actividad, estado, fecha_solicitud, alcance_autorizado, garantia_seriedad) VALUES
    ('ASFI', 'CERTIFICADO_ADECUACION', 'PAGOS_Y_PLATAFORMAS_DE_PAGO', 'EN_TRAMITE', current_date, '[]'::jsonb, NULL);
  END IF;
END $$;

INSERT INTO comite_gobierno (tipo, periodicidad_minima, quorum_minimo, activo, composicion_requerida) VALUES
  ('DIRECTORIO', 'MENSUAL', 3, TRUE, '["Presidente", "Directores titulares"]'::jsonb),
  ('RIESGOS', 'MENSUAL', 3, TRUE, '["Miembro del Directorio (preside)", "Gerente General", "Responsable de Gestión de Riesgos", "Gerente de Operaciones (con derecho a voz)"]'::jsonb),
  ('CUMPLIMIENTO', 'MENSUAL', 2, TRUE, '["Oficial de Cumplimiento", "Gerente General", "Miembro del Directorio"]'::jsonb),
  ('AUDITORIA', 'TRIMESTRAL', 2, TRUE, '["Auditor Interno", "Miembro del Directorio"]'::jsonb),
  ('SEGURIDAD_INFORMACION', 'TRIMESTRAL', 2, TRUE, '["Responsable de Seguridad de la Información", "Responsable de Tecnología", "Responsable de Riesgos"]'::jsonb)
ON CONFLICT (tipo) DO NOTHING;

INSERT INTO punto_reclamo (codigo, tipo, descripcion, horario, activo) VALUES
  ('PR-APP', 'APP', 'Punto de Reclamo — aplicación móvil', '24/7', TRUE),
  ('PR-WEB', 'WEB', 'Punto de Reclamo — sitio web', '24/7', TRUE),
  ('PR-TEL', 'TELEFONO', 'Punto de Reclamo — línea de atención', 'Lunes a viernes 08:30-18:30', TRUE),
  ('PR-MAIL', 'CORREO', 'Punto de Reclamo — correo electrónico', '24/7 (respuesta en horario hábil)', TRUE)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO matriz_riesgo_lft (version, dimension, factor, ponderacion, escala, base_normativa, vigente_desde) VALUES
  (1, 'CLIENTE', 'Condición de persona expuesta políticamente', 25, '{"NO": 0, "ALLEGADO": 15, "FAMILIAR": 20, "NACIONAL": 25, "EXTRANJERO": 25}'::jsonb, 'Instructivo EIF — enfoque basado en riesgo', current_date),
  (1, 'CLIENTE', 'Actividad económica declarada', 20, '{"ASALARIADO": 5, "COMERCIO_MINORISTA": 10, "CAMBISTA": 25, "NO_DECLARADA": 25}'::jsonb, 'Instructivo EIF — enfoque basado en riesgo', current_date),
  (1, 'CLIENTE', 'Nivel de debida diligencia alcanzado', 15, '{"REFORZADA": 5, "AMPLIADA": 8, "ESTANDAR": 12, "SIMPLIFICADA": 15}'::jsonb, 'Instructivo EIF — enfoque basado en riesgo', current_date),
  (1, 'PRODUCTO', 'Producto contratado', 15, '{"GRUPO_PASANAKU": 10, "BILLETERA": 15, "TRANSFERENCIA_P2P": 20}'::jsonb, 'Instructivo EIF — enfoque basado en riesgo', current_date),
  (1, 'CANAL', 'Canal de vinculación', 10, '{"PRESENCIAL": 5, "APP": 15}'::jsonb, 'Instructivo EIF — enfoque basado en riesgo', current_date),
  (1, 'ZONA_GEOGRAFICA', 'Zona de residencia u operación', 15, '{"URBANA": 5, "RURAL": 8, "FRONTERA": 20, "ZONA_DE_RIESGO": 25}'::jsonb, 'Instructivo EIF — enfoque basado en riesgo', current_date)
ON CONFLICT (version, dimension, factor) DO NOTHING;

-- Tipologías parametrizables: cargar una nueva es un INSERT
INSERT INTO regla_monitoreo_lft (codigo, tipologia, descripcion, expresion, ventana_evaluacion, umbral_monto, umbral_cantidad, severidad, accion_automatica, fuente_normativa, activa, vigente_desde) VALUES
  ('FRACCIONAMIENTO', 'Fraccionamiento para eludir el umbral', 'Varias operaciones por debajo del umbral que sumadas lo superan dentro de la ventana', '{"tipo": "acumulado", "porcentaje_del_umbral": 0.9, "minimo_operaciones": 3}'::jsonb, '3 dias', 1000, 3, 'ALTA', 'SOLO_ALERTAR', 'Tipología de pitufeo — instructivo UIF', TRUE, now()),
  ('ENTRADA_SALIDA_INMEDIATA', 'Entrada y salida inmediata del mismo monto', 'Carga y retiro del mismo importe en un lapso corto, sin uso del saldo', '{"tipo": "entrada_salida", "tolerancia_monto": 0.05, "minutos": 120}'::jsonb, '1 dia', 500, 1, 'ALTA', 'RETENER_OPERACION', 'Tipología de uso de la billetera como puente', TRUE, now()),
  ('CIRCULARIDAD', 'Circularidad entre cuentas del mismo círculo', 'A envía a B y B devuelve a A dentro de la ventana', '{"tipo": "circular", "saltos_maximos": 3, "horas": 48}'::jsonb, '2 dias', 0, 2, 'MEDIA', 'SOLO_ALERTAR', 'Tipología de estructuración', TRUE, now()),
  ('GRUPO_PANTALLA', 'Grupo de pasanaku usado como pantalla', 'Grupo entre personas sin vínculo con aportes muy superiores al perfil declarado', '{"tipo": "desvio_perfil_grupo", "factor": 3}'::jsonb, '1 mes', 0, 0, 'ALTA', 'SOLO_ALERTAR', 'Tipología específica del producto', TRUE, now()),
  ('DESVIO_PERFIL', 'Desvío severo del perfil transaccional declarado', 'El movimiento observado supera en más de tres veces el declarado', '{"tipo": "desvio", "factor": 3}'::jsonb, '1 mes', 0, 0, 'MEDIA', 'SOLO_ALERTAR', 'Instructivo EIF — perfil transaccional', TRUE, now())
ON CONFLICT (codigo) DO NOTHING;
