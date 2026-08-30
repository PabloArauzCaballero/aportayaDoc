-- Los reportes que la plataforma sabe generar, cada cuánto salen solos, cuánto tiempo se guarda cada cosa y qué dispara una alerta de cumplimiento. Sin la definición de reporte no hay extracción: no se corren consultas sueltas contra producción.
-- GENERADO desde seeders/minimos/19-reportes-y-retencion.json — no editar a mano.

-- Un reporte es una definición parametrizada con permiso propio, no una consulta improvisada. `cache_minutos` en 0 significa que siempre se recalcula: lo que va a un regulador nunca sale de caché.
INSERT INTO definicion_reporte (tipo, nombre, descripcion, consulta_base, parametros_esperados, columnas, permiso_requerido, contiene_datos_sensibles, cache_minutos, activa) VALUES
  ('ESTADO_DE_CUENTA_PARTICIPANTE', 'Estado de cuenta del participante', 'Movimientos de billetera, aportes, entregas y comisiones de un titular en un rango de fechas, con saldo inicial y final.', 'SELECT m.registrado_en, t.tipo, m.sentido, m.monto, m.glosa, m.saldo_disponible_posterior FROM movimiento_billetera m JOIN transaccion_billetera t ON t.id = m.transaccion_id JOIN cuenta_billetera c ON c.id = m.cuenta_billetera_id WHERE c.titular_usuario_id = :usuario_id AND m.registrado_en >= :desde AND m.registrado_en < :hasta ORDER BY m.registrado_en, m.orden', '{"usuario_id": "uuid", "desde": "date", "hasta": "date"}'::jsonb, '["registrado_en", "tipo", "sentido", "monto", "glosa", "saldo_disponible_posterior"]'::jsonb, 'BILLETERA_VER', TRUE, 0, TRUE),
  ('ESTADO_DE_GRUPO', 'Estado del grupo', 'Cupos, turnos, períodos y situación de cada participante del grupo: aportado, pendiente, en mora y cubierto por garantía.', 'SELECT p.id AS participante_id, u.codigo_publico, t.numero_turno, o.periodo_id, o.estado, o.monto_total, o.fecha_vencimiento FROM participante p JOIN usuario u ON u.id = p.usuario_id LEFT JOIN cupo cu ON cu.participante_id = p.id LEFT JOIN turno t ON t.cupo_id = cu.id LEFT JOIN obligacion_aporte o ON o.participante_id = p.id WHERE p.grupo_id = :grupo_id ORDER BY t.numero_turno, o.fecha_vencimiento', '{"grupo_id": "uuid"}'::jsonb, '["participante_id", "codigo_publico", "numero_turno", "periodo_id", "estado", "monto_total", "fecha_vencimiento"]'::jsonb, 'GRUPO_ADMINISTRAR', FALSE, 5, TRUE),
  ('HISTORICO_DE_PAGOS', 'Histórico de pagos conciliados', 'Pagos acreditados con su referencia de proveedor, su conciliación bancaria y su constancia, para responder un reclamo sin armar carpetas.', 'SELECT pg.fecha_hora_pago, pg.monto, pg.canal, pg.estado, pg.referencia_proveedor, c.estado AS estado_conciliacion, c.diferencia_monto FROM pago pg LEFT JOIN conciliacion c ON c.pago_id = pg.id JOIN obligacion_aporte o ON o.id = pg.obligacion_id WHERE o.grupo_id = :grupo_id AND pg.fecha_hora_pago >= :desde AND pg.fecha_hora_pago < :hasta ORDER BY pg.fecha_hora_pago', '{"grupo_id": "uuid", "desde": "date", "hasta": "date"}'::jsonb, '["fecha_hora_pago", "monto", "canal", "estado", "referencia_proveedor", "estado_conciliacion", "diferencia_monto"]'::jsonb, 'GRUPO_ADMINISTRAR', FALSE, 15, TRUE),
  ('CARTERA_EN_MORA', 'Cartera en mora por tramo', 'Obligaciones vencidas agrupadas por tramo de días de mora, con monto expuesto y cobertura del fondo de garantía.', 'SELECT CASE WHEN o.dias_mora BETWEEN 1 AND 7 THEN ''1-7'' WHEN o.dias_mora BETWEEN 8 AND 30 THEN ''8-30'' WHEN o.dias_mora BETWEEN 31 AND 90 THEN ''31-90'' ELSE ''90+'' END AS tramo, count(*) AS casos, sum(o.monto_total) AS monto_expuesto FROM obligacion_aporte o WHERE o.estado = ''VENCIDA'' AND (:grupo_id IS NULL OR o.grupo_id = :grupo_id) GROUP BY 1 ORDER BY 1', '{"grupo_id": "uuid?"}'::jsonb, '["tramo", "casos", "monto_expuesto"]'::jsonb, 'AUDITORIA_LEER', FALSE, 30, TRUE),
  ('CONCILIACION_DIARIA', 'Conciliación diaria de custodia', 'Saldo de libro contra saldo del banco custodio del día, con las excepciones abiertas y su antigüedad. Es el reporte que prueba el encaje.', 'SELECT cc.id AS cuenta_custodia_id, cc.saldo_segun_libro, cc.saldo_segun_banco, cc.saldo_segun_banco - cc.saldo_segun_libro AS diferencia, cc.fecha_saldo FROM cuenta_custodia cc WHERE cc.estado = ''ACTIVA'' AND cc.fecha_saldo::date = :fecha', '{"fecha": "date"}'::jsonb, '["cuenta_custodia_id", "saldo_segun_libro", "saldo_segun_banco", "diferencia", "fecha_saldo"]'::jsonb, 'AUDITORIA_LEER', FALSE, 0, TRUE),
  ('MOVIMIENTO_FONDO_GARANTIA', 'Movimiento del fondo de garantía', 'Constituciones, coberturas y recuperaciones del fondo de un grupo, con saldo resultante en cada punto.', 'SELECT mf.registrado_en, mf.tipo, mf.monto, mf.saldo_posterior, mf.glosa FROM movimiento_fondo mf JOIN fondo_garantia f ON f.id = mf.fondo_id WHERE f.grupo_id = :grupo_id AND mf.registrado_en >= :desde AND mf.registrado_en < :hasta ORDER BY mf.registrado_en', '{"grupo_id": "uuid", "desde": "date", "hasta": "date"}'::jsonb, '["registrado_en", "tipo", "monto", "saldo_posterior", "glosa"]'::jsonb, 'GRUPO_ADMINISTRAR', FALSE, 10, TRUE),
  ('INCUMPLIMIENTOS_Y_SANCIONES', 'Incumplimientos y sanciones firmes', 'Expedientes de incumplimiento con su estado procesal: notificación, descargo, resolución y apelación. Sin resolución firme no aparece como sancionado.', 'SELECT ri.codigo_expediente, ri.tipo, ri.severidad, ri.estado, ri.monto_involucrado, ri.monto_recuperado, ri.detectado_en, ri.notificado_en, ri.cerrado_en FROM registro_incumplimiento ri WHERE ri.grupo_id = :grupo_id AND ri.detectado_en >= :desde ORDER BY ri.detectado_en DESC', '{"grupo_id": "uuid", "desde": "date"}'::jsonb, '["codigo_expediente", "tipo", "severidad", "estado", "monto_involucrado", "monto_recuperado", "detectado_en", "notificado_en", "cerrado_en"]'::jsonb, 'AUDITORIA_LEER', TRUE, 0, TRUE),
  ('DESEMPENO_ORGANIZADOR', 'Desempeño del organizador', 'Métricas del organizador por período: grupos activos, tasa de cobranza, entregas en plazo e incidencias.', 'SELECT o.id AS organizador_id, u.codigo_publico, count(DISTINCT g.id) AS grupos_activos, count(DISTINCT ri.id) AS incidencias FROM organizador o JOIN usuario u ON u.id = o.usuario_id LEFT JOIN grupo g ON g.organizador_id = o.id AND g.estado = ''ACTIVO'' LEFT JOIN registro_incumplimiento ri ON ri.grupo_id = g.id AND ri.tipo = ''INCUMPLIMIENTO_ORGANIZADOR'' WHERE o.estado = ''HABILITADO'' GROUP BY o.id, u.codigo_publico ORDER BY incidencias DESC', '{}'::jsonb, '["organizador_id", "codigo_publico", "grupos_activos", "incidencias"]'::jsonb, 'AUDITORIA_LEER', FALSE, 60, TRUE),
  ('OPERACIONES_SOSPECHOSAS', 'Operaciones relevantes y alertas de monitoreo', 'Registro de operaciones relevantes y alertas de monitoreo LGI/FT del período, para la remisión a la UIF y para el comité de cumplimiento.', 'SELECT ro.registrado_en, ro.formulario, ro.concepto_operacion, ro.monto_usd, ro.usuario_id, ro.incluido_en_reporte FROM registro_operacion_relevante ro WHERE ro.registrado_en >= :desde AND ro.registrado_en < :hasta ORDER BY ro.registrado_en', '{"desde": "date", "hasta": "date"}'::jsonb, '["registrado_en", "formulario", "concepto_operacion", "monto_usd", "usuario_id", "incluido_en_reporte"]'::jsonb, 'CUMPLIMIENTO_REPORTAR', TRUE, 0, TRUE),
  ('KPI_PLATAFORMA', 'Indicadores de plataforma', 'Panel de indicadores del período con su meta y su variación respecto del período anterior.', 'SELECT k.codigo, k.nombre, k.valor, k.unidad, k.meta, k.variacion_periodo_anterior, k.calculado_en FROM indicador_kpi k WHERE k.periodo = :periodo AND k.dimension = ''GLOBAL'' ORDER BY k.codigo', '{"periodo": "string"}'::jsonb, '["codigo", "nombre", "valor", "unidad", "meta", "variacion_periodo_anterior", "calculado_en"]'::jsonb, 'AUDITORIA_LEER', FALSE, 60, TRUE)
ON CONFLICT (nombre) DO NOTHING;

-- Corridas automáticas. La toma del trabajo usa bloqueo entre réplicas: dos instancias no generan el mismo reporte dos veces (skill `automatizacion-tareas`).
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM programacion_reporte) THEN
  INSERT INTO programacion_reporte (definicion_id, expresion_cron, parametros_fijos, destinatarios, canal_entrega, formato, activa, ultima_ejecucion_en, proxima_ejecucion_en) VALUES
    ((SELECT id FROM definicion_reporte WHERE nombre = 'Conciliación diaria de custodia'), '0 7 * * *', '{"fecha": "$ayer"}'::jsonb, '["tesoreria@aportaya.bo", "contabilidad@aportaya.bo"]'::jsonb, 'CORREO', 'XLSX', TRUE, NULL, date_trunc('day', now()) + interval '1 day 7 hours'),
    ((SELECT id FROM definicion_reporte WHERE nombre = 'Cartera en mora por tramo'), '0 8 * * 1', '{"grupo_id": null}'::jsonb, '["riesgos@aportaya.bo"]'::jsonb, 'CORREO', 'XLSX', TRUE, NULL, date_trunc('week', now()) + interval '7 days 8 hours'),
    ((SELECT id FROM definicion_reporte WHERE nombre = 'Operaciones relevantes y alertas de monitoreo'), '0 6 1 * *', '{"desde": "$inicio_mes_anterior", "hasta": "$inicio_mes_actual"}'::jsonb, '["cumplimiento@aportaya.bo"]'::jsonb, 'ALMACENAMIENTO', 'CSV', TRUE, NULL, date_trunc('month', now()) + interval '1 month 6 hours'),
    ((SELECT id FROM definicion_reporte WHERE nombre = 'Indicadores de plataforma'), '0 9 1 * *', '{"periodo": "$mes_anterior"}'::jsonb, '["directorio@aportaya.bo", "riesgos@aportaya.bo"]'::jsonb, 'PORTAL', 'PDF', TRUE, NULL, date_trunc('month', now()) + interval '1 month 9 hours'),
    ((SELECT id FROM definicion_reporte WHERE nombre = 'Desempeño del organizador'), '0 9 1 */3 *', '{}'::jsonb, '["operaciones@aportaya.bo"]'::jsonb, 'PORTAL', 'XLSX', TRUE, NULL, date_trunc('month', now()) + interval '3 months 9 hours');
  END IF;
END $$;

-- Cuánto se guarda cada cosa y qué se hace al vencer. Borrar antes de plazo es tan grave como guardar para siempre: lo primero destruye evidencia, lo segundo viola la finalidad del dato.
INSERT INTO politica_retencion (entidad, meses_retencion_activa, meses_retencion_historica, accion_al_vencer, base_legal, vigente_desde) VALUES
  ('expediente_cliente', 60, 120, 'ANONIMIZAR', 'Instructivo específico EIF (UIF) — conservación mínima de 5 años desde el fin de la relación comercial; Código de Comercio art. 52', '2026-01-01'),
  ('documento_identidad', 60, 120, 'ANONIMIZAR', 'Instructivo específico EIF (UIF) — respaldo documental de la debida diligencia', '2026-01-01'),
  ('transaccion_billetera', 60, 120, 'ARCHIVAR', 'Código de Comercio art. 52 — libros y registros contables por 5 años', '2026-01-01'),
  ('movimiento_billetera', 60, 120, 'ARCHIVAR', 'Código de Comercio art. 52 — partida de cada transacción', '2026-01-01'),
  ('asiento_contable', 60, 120, 'ARCHIVAR', 'Código de Comercio art. 52', '2026-01-01'),
  ('pago', 60, 120, 'ARCHIVAR', 'Código de Comercio art. 52; respaldo de reclamos del consumidor financiero', '2026-01-01'),
  ('extracto_bancario', 60, 120, 'ARCHIVAR', 'Código de Comercio art. 52 — respaldo de la conciliación', '2026-01-01'),
  ('registro_operacion_relevante', 60, 120, 'ARCHIVAR', 'Instructivo específico EIF (UIF) art. 52 — respaldo de la remisión', '2026-01-01'),
  ('reporte_operacion_sospechosa', 120, 120, 'ARCHIVAR', 'Ley 1768 y normativa UIF — reserva y conservación del ROS', '2026-01-01'),
  ('registro_incumplimiento', 60, 120, 'ARCHIVAR', 'Respaldo del debido proceso y de la reputación calculada', '2026-01-01'),
  ('reclamo_cliente', 60, 120, 'ARCHIVAR', 'RNSF Libro 4 Título I — atención al consumidor financiero; la restricción ck_reclamo_conservacion exige 10 años desde el ingreso', '2026-01-01'),
  ('evento_riesgo_operativo', 60, 120, 'ARCHIVAR', 'RNSF Libro 3 Título V — base de datos de eventos de riesgo operativo (CIRO)', '2026-01-01'),
  ('bitacora_evento', 24, 60, 'ARCHIVAR', 'Pista de auditoría — RNSF Libro 3 Título VII, gestión de seguridad de la información', '2026-01-01'),
  ('registro_acceso_datos', 24, 60, 'ARCHIVAR', 'Huella de acceso a datos personales — respaldo ante requerimiento de autoridad', '2026-01-01'),
  ('envio_notificacion', 12, 24, 'ELIMINAR', 'Prueba de notificación mientras corren los plazos de reclamo y apelación', '2026-01-01'),
  ('webhook_pasarela', 12, 24, 'ELIMINAR', 'Respaldo técnico de la conciliación de pagos', '2026-01-01'),
  ('intento_autenticacion', 12, 24, 'ELIMINAR', 'Investigación de incidentes de seguridad', '2026-01-01'),
  ('sesion', 6, 12, 'ELIMINAR', 'Minimización: la sesión cerrada ya no cumple finalidad operativa', '2026-01-01'),
  ('token_verificacion', 3, 6, 'ELIMINAR', 'Minimización: el token consumido o expirado no cumple finalidad', '2026-01-01')
ON CONFLICT (entidad) DO NOTHING;

-- Reglas operativas de cumplimiento sobre la billetera. Complementan a `regla_monitoreo_lft` (archivo 08), que es la tipología LGI/FT: acá está lo que se mide en la operación diaria. Ninguna bloquea a un usuario sola salvo coincidencia con lista restrictiva.
INSERT INTO regla_cumplimiento (codigo, descripcion, categoria, expresion, umbral, ventana_horas, severidad, accion_automatica, activa) VALUES
  ('UMBRAL_OPERACION_UNICA', 'Operación única igual o mayor al umbral de registro de operación relevante', 'UMBRAL_MONTO', 'operacion.monto_usd >= umbral(:PCC01_OPERACION_UNICA)', 10000.0, NULL, 'MEDIA', 'ALERTAR', TRUE),
  ('UMBRAL_ACUMULADO_10D', 'Acumulado del titular sobre el umbral dentro de la ventana de diez días', 'UMBRAL_MONTO', 'suma(operaciones.monto_usd, ventana) >= umbral(:PCC01_ACUMULADO)', 10000.0, 240, 'ALTA', 'ALERTAR', TRUE),
  ('FRACCIONAMIENTO_90', 'Tres o más operaciones entre el 80 % y el 99 % del umbral en la misma ventana', 'FRACCIONAMIENTO', 'conteo(operaciones donde monto_usd entre 0.8*umbral y 0.99*umbral, ventana) >= 3', 10000.0, 240, 'ALTA', 'ALERTAR', TRUE),
  ('VELOCIDAD_RECARGAS', 'Más de diez recargas acreditadas en veinticuatro horas', 'VELOCIDAD', 'conteo(orden_recarga.acreditada, ventana) > 10', 10.0, 24, 'MEDIA', 'ALERTAR', TRUE),
  ('ENTRADA_SALIDA_INMEDIATA', 'Recarga y retiro del mismo importe dentro de dos horas, sin uso del saldo', 'VELOCIDAD', 'existe(retiro donde abs(monto - recarga.monto) <= 0.05*recarga.monto, ventana)', NULL, 2, 'ALTA', 'RETENER_OPERACION', TRUE),
  ('COINCIDENCIA_LISTA_RESTRICTIVA', 'Coincidencia exacta del titular contra una lista restrictiva vigente', 'LISTA_RESTRICTIVA', 'coincidencia(usuario, listas_vigentes).puntaje >= 0.95', 0.95, NULL, 'CRITICA', 'BLOQUEAR_USUARIO', TRUE),
  ('COINCIDENCIA_LISTA_PARCIAL', 'Coincidencia parcial que requiere revisión de un analista antes de decidir', 'LISTA_RESTRICTIVA', 'coincidencia(usuario, listas_vigentes).puntaje entre 0.80 y 0.94', 0.8, NULL, 'ALTA', 'RETENER_OPERACION', TRUE),
  ('RED_MISMO_DISPOSITIVO', 'Cinco o más titulares distintos operando desde el mismo dispositivo en una semana', 'RED_SOSPECHOSA', 'conteo_distinto(usuario_id por dispositivo_id, ventana) >= 5', 5.0, 168, 'ALTA', 'ALERTAR', TRUE),
  ('RED_MISMO_INSTRUMENTO', 'Un mismo instrumento de fondeo asociado a tres o más titulares', 'RED_SOSPECHOSA', 'conteo_distinto(usuario_id por instrumento.hash_identificador) >= 3', 3.0, NULL, 'ALTA', 'RETENER_OPERACION', TRUE),
  ('DESVIO_PERFIL_TRANSACCIONAL', 'Operativa del mes que supera en tres veces el perfil declarado por el titular', 'UMBRAL_MONTO', 'suma(operaciones.monto_bob, mes) > 3 * perfil_transaccional.monto_mensual_estimado', 3.0, 720, 'MEDIA', 'ALERTAR', TRUE),
  ('PEP_OPERACION_RELEVANTE', 'Persona expuesta políticamente con una operación sobre el umbral de registro', 'UMBRAL_MONTO', 'usuario.es_pep AND operacion.monto_usd >= 5000', 5000.0, NULL, 'ALTA', 'ALERTAR', TRUE)
ON CONFLICT (codigo) DO NOTHING;

-- Umbral por nivel de verificación de identidad, usado por cumplimiento para monitorear. Quien RECHAZA la operación es `limite_operativo_billetera` (archivo 03): esta tabla mide, aquella impide. Si los dos números divergen, manda el del archivo 03 y hay un hallazgo que corregir. Correspondencia de niveles: NINGUNO = sin verificar, BASICO = diligencia SIMPLIFICADA, INTERMEDIO = ESTANDAR, COMPLETO = AMPLIADA o REFORZADA.
INSERT INTO umbral_operativo (concepto, nivel_kyc_requerido, monto_maximo, moneda, vigente_desde) VALUES
  ('SALDO_MAXIMO', 'NINGUNO', 0.0, 'BOB', '2026-01-01'),
  ('SALDO_MAXIMO', 'BASICO', 2000.0, 'BOB', '2026-01-01'),
  ('SALDO_MAXIMO', 'INTERMEDIO', 5000.0, 'BOB', '2026-01-01'),
  ('SALDO_MAXIMO', 'COMPLETO', 50000.0, 'BOB', '2026-01-01'),
  ('RECARGA_OPERACION', 'NINGUNO', 0.0, 'BOB', '2026-01-01'),
  ('RECARGA_OPERACION', 'BASICO', 1000.0, 'BOB', '2026-01-01'),
  ('RECARGA_OPERACION', 'INTERMEDIO', 2500.0, 'BOB', '2026-01-01'),
  ('RECARGA_OPERACION', 'COMPLETO', 20000.0, 'BOB', '2026-01-01'),
  ('RETIRO_OPERACION', 'NINGUNO', 0.0, 'BOB', '2026-01-01'),
  ('RETIRO_OPERACION', 'BASICO', 1000.0, 'BOB', '2026-01-01'),
  ('RETIRO_OPERACION', 'INTERMEDIO', 2500.0, 'BOB', '2026-01-01'),
  ('RETIRO_OPERACION', 'COMPLETO', 20000.0, 'BOB', '2026-01-01'),
  ('TRANSFERENCIA_P2P_DIA', 'NINGUNO', 0.0, 'BOB', '2026-01-01'),
  ('TRANSFERENCIA_P2P_DIA', 'BASICO', 500.0, 'BOB', '2026-01-01'),
  ('TRANSFERENCIA_P2P_DIA', 'INTERMEDIO', 2000.0, 'BOB', '2026-01-01'),
  ('TRANSFERENCIA_P2P_DIA', 'COMPLETO', 10000.0, 'BOB', '2026-01-01'),
  ('EFECTIVO_DIA', 'NINGUNO', 0.0, 'BOB', '2026-01-01'),
  ('EFECTIVO_DIA', 'BASICO', 700.0, 'BOB', '2026-01-01'),
  ('EFECTIVO_DIA', 'INTERMEDIO', 2000.0, 'BOB', '2026-01-01'),
  ('EFECTIVO_DIA', 'COMPLETO', 7000.0, 'BOB', '2026-01-01')
ON CONFLICT (nivel_kyc_requerido, concepto) DO NOTHING;

-- Se siembra la fila de control con `registros = 0`: la lista real la carga el proceso de actualización, no un seeder. Una lista sembrada con datos falsos produciría coincidencias falsas contra personas reales.
INSERT INTO lista_restrictiva_externa (nombre_lista, version, fecha_actualizacion, registros) VALUES
  ('ONU_CSNU', 'pendiente-carga', '2026-01-01', 0),
  ('OFAC_SDN', 'pendiente-carga', '2026-01-01', 0),
  ('UE_SANCIONES', 'pendiente-carga', '2026-01-01', 0),
  ('PEP_NACIONAL', 'pendiente-carga', '2026-01-01', 0),
  ('LISTA_INTERNA', 'pendiente-carga', '2026-01-01', 0)
ON CONFLICT (version, nombre_lista) DO NOTHING;
