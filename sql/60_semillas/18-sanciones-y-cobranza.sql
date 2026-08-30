-- Qué pasa cuando alguien no paga: la escalera de cobranza y la matriz que dice qué sanción corresponde a qué incumplimiento. Nada de esto se decide caso por caso ni se improvisa en el código.
-- GENERADO desde seeders/minimos/18-sanciones-y-cobranza.json — no editar a mano.

-- Política global (grupo_id nulo). Un grupo puede tener la suya, pero nunca con plazos de descargo o apelación menores que estos.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM politica_sancion) THEN
  INSERT INTO politica_sancion (grupo_id, version, requiere_acuerdo_grupo, plazo_descargo_dias, plazo_apelacion_dias, prescribe_en_dias, vigente_desde) VALUES
    (NULL, 'v1', FALSE, 5, 5, 365, '2026-01-01T00:00:00-04:00');
  END IF;
END $$;

-- Atrasos y pagos parciales: la primera vez se advierte. La escalera existe para que reincidir cueste, no para castigar un olvido.
INSERT INTO matriz_sancion (politica_id, tipo_incumplimiento, severidad, numero_reincidencia, tipo_sancion, valor, duracion_dias, es_automatica, requiere_revision_humana) VALUES
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'APORTE_ATRASADO', 'LEVE', 1, 'ADVERTENCIA', 0.0, NULL, TRUE, FALSE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'APORTE_ATRASADO', 'LEVE', 2, 'AFECTACION_REPUTACION', 25.0, NULL, TRUE, FALSE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'APORTE_ATRASADO', 'MODERADA', 3, 'SUSPENSION_DE_VOTO', 0.0, 30, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'APORTE_PARCIAL', 'LEVE', 1, 'ADVERTENCIA', 0.0, NULL, TRUE, FALSE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'APORTE_PARCIAL', 'MODERADA', 2, 'AFECTACION_REPUTACION', 40.0, NULL, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'PAGO_RECHAZADO_O_REVERSADO', 'LEVE', 1, 'ADVERTENCIA', 0.0, NULL, TRUE, FALSE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'PAGO_RECHAZADO_O_REVERSADO', 'MODERADA', 2, 'RESTRICCION_NUEVOS_GRUPOS', 0.0, 60, FALSE, TRUE)
ON CONFLICT (severidad, numero_reincidencia, tipo_incumplimiento) DO NOTHING;

-- Impago y abandono: acá el grupo ya perdió plata. La retención de entrega precede a la expulsión, porque devolver el cupo es peor negocio que cobrarlo.
INSERT INTO matriz_sancion (politica_id, tipo_incumplimiento, severidad, numero_reincidencia, tipo_sancion, valor, duracion_dias, es_automatica, requiere_revision_humana) VALUES
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'APORTE_IMPAGO', 'MODERADA', 1, 'AFECTACION_REPUTACION', 60.0, NULL, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'APORTE_IMPAGO', 'GRAVE', 2, 'RETENCION_DE_ENTREGA', 0.0, NULL, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'APORTE_IMPAGO', 'GRAVE', 3, 'PERDIDA_DE_PRIORIDAD_DE_TURNO', 0.0, NULL, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'APORTE_IMPAGO', 'CRITICA', 4, 'EXPULSION_DEL_GRUPO', 0.0, NULL, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'INCUMPLIMIENTO_PLAN_REGULARIZACION', 'GRAVE', 1, 'RETENCION_DE_ENTREGA', 0.0, NULL, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'INCUMPLIMIENTO_PLAN_REGULARIZACION', 'GRAVE', 2, 'RESTRICCION_NUEVOS_GRUPOS', 0.0, 180, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'ABANDONO_DE_GRUPO', 'GRAVE', 1, 'AFECTACION_REPUTACION', 200.0, NULL, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'ABANDONO_DE_GRUPO', 'CRITICA', 2, 'INHABILITACION_PLATAFORMA', 0.0, 365, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'BENEFICIARIO_NO_CONTINUA_APORTANDO', 'GRAVE', 1, 'RESTRICCION_NUEVOS_GRUPOS', 0.0, 365, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'BENEFICIARIO_NO_CONTINUA_APORTANDO', 'CRITICA', 2, 'INHABILITACION_PLATAFORMA', 0.0, 730, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'INCUMPLIMIENTO_AVAL', 'GRAVE', 1, 'AFECTACION_REPUTACION', 150.0, NULL, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'INCUMPLIMIENTO_ORGANIZADOR', 'GRAVE', 1, 'RESTRICCION_NUEVOS_GRUPOS', 0.0, 180, FALSE, TRUE)
ON CONFLICT (severidad, numero_reincidencia, tipo_incumplimiento) DO NOTHING;

-- Fraude y uso indebido: no hay escalera. Va directo a revisión humana y, si se confirma, deriva a cumplimiento (posible operación sospechosa).
INSERT INTO matriz_sancion (politica_id, tipo_incumplimiento, severidad, numero_reincidencia, tipo_sancion, valor, duracion_dias, es_automatica, requiere_revision_humana) VALUES
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'COMPROBANTE_FALSO', 'CRITICA', 1, 'INHABILITACION_PLATAFORMA', 0.0, 1095, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'FRAUDE_CONFIRMADO', 'CRITICA', 1, 'INHABILITACION_PLATAFORMA', 0.0, 3650, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'USO_INDEBIDO_DE_LA_PLATAFORMA', 'GRAVE', 1, 'RESTRICCION_NUEVOS_GRUPOS', 0.0, 90, FALSE, TRUE),
  ((SELECT id FROM politica_sancion WHERE version = 'v1'), 'USO_INDEBIDO_DE_LA_PLATAFORMA', 'CRITICA', 2, 'INHABILITACION_PLATAFORMA', 0.0, 365, FALSE, TRUE)
ON CONFLICT (severidad, numero_reincidencia, tipo_incumplimiento) DO NOTHING;

-- Los tramos son contiguos y sin huecos: de -3 a 1095 días toda mora cae en exactamente una etapa. `max_contactos_por_semana` es un tope duro que el motor de notificaciones respeta aunque la regla dispare más veces.
INSERT INTO estrategia_cobranza (etapa, dias_mora_desde, dias_mora_hasta, canales_permitidos, frecuencia_dias, max_contactos_por_semana, plantilla_notificacion_codigo, requiere_gestor_humano, permite_quita, quita_maxima_porcentaje, siguiente_etapa) VALUES
  ('PREVENTIVA', -3, 0, 'PUSH,WHATSAPP,IN_APP', 3, 1, 'COB_APORTE_POR_VENCER_WA', FALSE, FALSE, 0.0, 'TEMPRANA'),
  ('TEMPRANA', 1, 7, 'PUSH,WHATSAPP,SMS,IN_APP', 2, 3, 'COB_APORTE_VENCIDO_WA', FALSE, FALSE, 0.0, 'ADMINISTRATIVA'),
  ('ADMINISTRATIVA', 8, 30, 'WHATSAPP,SMS,LLAMADA_VOZ,CORREO', 5, 3, 'COB_APORTE_VENCIDO_WA', TRUE, FALSE, 0.0, 'PREJUDICIAL'),
  ('PREJUDICIAL', 31, 90, 'WHATSAPP,LLAMADA_VOZ,CORREO', 15, 2, NULL, TRUE, TRUE, 20.0, 'JUDICIAL'),
  ('JUDICIAL', 91, 365, 'CORREO,LLAMADA_VOZ', 30, 1, NULL, TRUE, TRUE, 40.0, 'CASTIGO'),
  ('CASTIGO', 366, 1095, 'CORREO', 90, 1, NULL, TRUE, TRUE, 70.0, NULL)
ON CONFLICT (dias_mora_desde, etapa) DO NOTHING;
