-- Quién puede administrar un grupo, cómo se arman los grupos nuevos y qué hace sola la plataforma. Tres catálogos que deciden accesos y efectos, y por eso son dato configurable y no constantes en el código.
-- GENERADO desde seeders/minimos/17-organizador-y-emparejamiento.json — no editar a mano.

-- Requisitos acumulativos por nivel: para ser SENIOR hay que cumplir además todo lo de ESTANDAR. `valor_minimo` es el umbral medible; sin fila no hay nivel, y sin nivel no se crea grupo.
INSERT INTO requisito_habilitacion (codigo, descripcion, tipo, valor_minimo, es_obligatorio, nivel_requerido, activo) VALUES
  ('HAB_APRENDIZ_KYC', 'Verificación de identidad completa y vigente', 'KYC', 1.0, TRUE, 'APRENDIZ', TRUE),
  ('HAB_APRENDIZ_ANTIGUEDAD', 'Antigüedad mínima en la plataforma, en meses', 'ANTIGUEDAD', 3.0, TRUE, 'APRENDIZ', TRUE),
  ('HAB_APRENDIZ_REPUTACION', 'Puntaje de reputación mínimo', 'REPUTACION', 600.0, TRUE, 'APRENDIZ', TRUE),
  ('HAB_APRENDIZ_CAPACITACION', 'Módulos de capacitación aprobados y vigentes', 'CAPACITACION', 1.0, TRUE, 'APRENDIZ', TRUE),
  ('HAB_ESTANDAR_ANTIGUEDAD', 'Antigüedad mínima en la plataforma, en meses', 'ANTIGUEDAD', 6.0, TRUE, 'ESTANDAR', TRUE),
  ('HAB_ESTANDAR_REPUTACION', 'Puntaje de reputación mínimo', 'REPUTACION', 650.0, TRUE, 'ESTANDAR', TRUE),
  ('HAB_ESTANDAR_CAPACITACION', 'Módulos de capacitación aprobados y vigentes', 'CAPACITACION', 2.0, TRUE, 'ESTANDAR', TRUE),
  ('HAB_SENIOR_ANTIGUEDAD', 'Antigüedad mínima en la plataforma, en meses', 'ANTIGUEDAD', 12.0, TRUE, 'SENIOR', TRUE),
  ('HAB_SENIOR_REPUTACION', 'Puntaje de reputación mínimo', 'REPUTACION', 720.0, TRUE, 'SENIOR', TRUE),
  ('HAB_SENIOR_GARANTIA', 'Garantía económica constituida, en bolivianos', 'GARANTIA_ECONOMICA', 2000.0, TRUE, 'SENIOR', TRUE),
  ('HAB_MAESTRO_ANTIGUEDAD', 'Antigüedad mínima en la plataforma, en meses', 'ANTIGUEDAD', 24.0, TRUE, 'MAESTRO', TRUE),
  ('HAB_MAESTRO_REPUTACION', 'Puntaje de reputación mínimo', 'REPUTACION', 800.0, TRUE, 'MAESTRO', TRUE),
  ('HAB_MAESTRO_GARANTIA', 'Garantía económica constituida, en bolivianos', 'GARANTIA_ECONOMICA', 5000.0, TRUE, 'MAESTRO', TRUE),
  ('HAB_MAESTRO_CAPACITACION', 'Módulos de capacitación aprobados y vigentes, incluida la formación en prevención de LGI/FT', 'CAPACITACION', 3.0, TRUE, 'MAESTRO', TRUE)
ON CONFLICT (codigo) DO NOTHING;

-- Los cuatro pesos suman 1,000. Es la única fila vigente: cambiar el criterio es insertar otra con `vigente_desde` posterior, no editar esta.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM criterio_emparejamiento) THEN
  INSERT INTO criterio_emparejamiento (peso_reputacion, peso_monto, peso_historial_comun, peso_geografia, reputacion_minima, max_morosos_por_grupo, vigente_desde) VALUES
    (0.4, 0.25, 0.2, 0.15, 600.0, 1, '2026-01-01T00:00:00-04:00');
  END IF;
END $$;

-- Lo que corre solo: generar cobros, recordar y aplicar la mora que el reglamento ya pactó. Lo que mueve plata hacia afuera o castiga a alguien queda propuesto y espera a una persona.
INSERT INTO regla_automatizacion (codigo, descripcion, disparador, expresion_disparo, condicion, accion, requiere_confirmacion_humana, prioridad, activa) VALUES
  ('GENERAR_COBROS_DEL_PERIODO', 'Al abrir el período, emitir la obligación de aporte de cada participante activo', 'EVENTO', 'periodo.abierto', 'grupo.estado = ''ACTIVO'' AND periodo.estado = ''ABIERTO''', 'GENERAR_COBROS', FALSE, 10, TRUE),
  ('RECORDATORIO_T_MENOS_3', 'Recordar el aporte tres días antes del vencimiento', 'CRON', '0 13 * * *', 'obligacion.estado = ''PENDIENTE'' AND obligacion.fecha_vencimiento = current_date + 3', 'ENVIAR_RECORDATORIO', FALSE, 30, TRUE),
  ('RECORDATORIO_T_MENOS_1', 'Recordar el aporte el día antes del vencimiento', 'CRON', '0 13 * * *', 'obligacion.estado = ''PENDIENTE'' AND obligacion.fecha_vencimiento = current_date + 1', 'ENVIAR_RECORDATORIO', FALSE, 31, TRUE),
  ('RECORDATORIO_VENCIDO_D1', 'Avisar el día después del vencimiento, con el monto exacto y el recargo', 'CRON', '0 14 * * *', 'obligacion.estado = ''VENCIDA'' AND obligacion.dias_mora = 1', 'ENVIAR_RECORDATORIO', FALSE, 32, TRUE),
  ('APLICAR_MORA_DIARIA', 'Aplicar el recargo por mora que el reglamento del grupo ya pactó', 'CRON', '0 2 * * *', 'obligacion.estado = ''VENCIDA'' AND obligacion.dias_mora > politica_mora.dias_gracia', 'APLICAR_MORA', FALSE, 20, TRUE),
  ('LIQUIDAR_PERIODO_CERRADO', 'Liquidar el período: totalizar lo recaudado, la mora y las coberturas', 'EVENTO', 'periodo.cerrado', 'periodo.estado = ''CERRADO''', 'LIQUIDAR_PERIODO', FALSE, 15, TRUE),
  ('PROPONER_ENTREGA_DE_TURNO', 'Con el período liquidado y las validaciones previas en verde, dejar la entrega lista para autorizar', 'EVENTO', 'periodo.liquidado', 'validaciones_previas.todas_ok AND entrega.estado = ''PENDIENTE''', 'EJECUTAR_ENTREGA', TRUE, 5, TRUE),
  ('ESCALAR_COBRANZA_D15', 'A los 15 días de mora, pasar la gestión a la etapa siguiente con gestor humano', 'CRON', '0 8 * * 1-5', 'obligacion.dias_mora >= 15 AND gestion_cobranza.etapa = ''TEMPRANA''', 'ESCALAR_COBRANZA', TRUE, 40, TRUE)
ON CONFLICT (codigo) DO NOTHING;
