-- El expediente de cumplimiento que se le muestra a la UIF y a ASFI: declaraciones de origen de fondos, revisión periódica de KYC, desvío de perfil, alerta de monitoreo escalada a caso, coincidencia de lista descartada como falso positivo, y el ciclo completo de un reporte regulatorio con su acuse.
-- GENERADO desde seeders/dev/13-cumplimiento-uif.json — no editar a mano.

-- USR000005 opera con montos altos para su segmento: declaró origen y adjuntó respaldo, y cumplimiento lo verificó. Sin esta fila, la operación sobre umbral no debería haberse acreditado.
INSERT INTO declaracion_origen_fondos (usuario_id, transaccion_id, verificada_por, monto, moneda, origen, descripcion, documento_respaldo_url, hash_documento, estado, declarada_en) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), (SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-05'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), 2000.0, 'BOB', 'NEGOCIO', 'Ingresos del comercio mayorista de la titular. Adjunta declaración jurada y últimas tres facturas de venta.', 'https://almacen.pasanaku.test/origen-fondos/USR000005.pdf', encode(digest('origen-fondos-usr5', 'sha256'), 'hex'), 'VERIFICADA', now() - interval '44 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), (SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-01'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), 1200.0, 'BOB', 'NEGOCIO', 'Ingresos del comercio minorista de la titular. Persona expuesta políticamente: la declaración es obligatoria por su nivel de debida diligencia reforzada.', 'https://almacen.pasanaku.test/origen-fondos/USR000001.pdf', encode(digest('origen-fondos-usr1', 'sha256'), 'hex'), 'VERIFICADA', now() - interval '44 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), NULL, NULL, 600.0, 'BOB', 'SALARIO', 'Ingresos del servicio de transporte. Declarada por el titular, pendiente de respaldo documental.', NULL, NULL, 'DECLARADA', now() - interval '44 days')
ON CONFLICT DO NOTHING;

-- El beneficiario final de una persona natural es ella misma con 100 %. Se registra igual: si mañana entra una persona jurídica, la consulta ya funciona y no hay que migrar nada.
INSERT INTO beneficiario_final (usuario_id, nombre, documento, porcentaje_participacion, tipo_control, verificado, registrado_en) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), 'María Elena Quispe Mamani', '9000001', 100.0, 'CONTROL_EFECTIVO', TRUE, now() - interval '120 days'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), 'Rosa Condori Apaza', '9000005', 100.0, 'CONTROL_EFECTIVO', TRUE, now() - interval '90 days')
ON CONFLICT DO NOTHING;

-- La periodicidad sale del nivel de riesgo: la persona expuesta políticamente se revisa cada año y ya se ejecutó; una de riesgo medio tiene la suya programada; la de USR000004 está VENCIDA, que es el caso que debe frenar su operativa.
INSERT INTO revision_periodica_kyc (usuario_id, calificacion_riesgo_id, ejecutada_por, fecha_programada, fecha_ejecutada, resultado, estado) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000001'), NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), (current_date + interval '-60 days'), (current_date + interval '-58 days'), 'SIN_CAMBIOS', 'EJECUTADA'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), NULL, NULL, (current_date + interval '45 days'), NULL, NULL, 'PROGRAMADA'),
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), NULL, NULL, (current_date + interval '-10 days'), NULL, NULL, 'VENCIDA')
ON CONFLICT DO NOTHING;

-- USR000005 declaró Bs 15.000 mensuales y movió Bs 2.500 en el mes: no hay desvío al alza. El caso sembrado es el contrario y por eso importa: USR000004 declaró Bs 2.500 y movió Bs 600, un desvío a la baja que se justifica solo y no escala.
INSERT INTO desvio_perfil (usuario_id, perfil_transaccional_id, alerta_monitoreo_id, periodo, monto_observado, monto_esperado, desvio_porcentual, severidad, estado, justificacion, detectado_en) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000004'), (SELECT id FROM perfil_transaccional WHERE usuario_id = (SELECT id FROM usuario WHERE codigo_publico = 'USR000004') LIMIT 1), NULL, '2026-07', 600.0, 2500.0, -76.0, 'BAJA', 'JUSTIFICADO', 'Desvío a la baja: el titular operó menos de lo declarado. No hay riesgo de lavado en operar por debajo del perfil; se registra para calibrar el perfil en la próxima revisión.', now() - interval '9 days')
ON CONFLICT DO NOTHING;

-- Dos alertas: una descartada y otra escalada a caso. `ck_alerta_conclusion` exige conclusión escrita de al menos 20 caracteres en AMBOS desenlaces, no solo al descartar: escalar también es cerrar la alerta y hay que decir por qué.
INSERT INTO alerta_monitoreo_lft (regla_monitoreo_id, usuario_id, cuenta_billetera_id, transaccion_id, caso_id, asignada_a, monto_involucrado, detalle, severidad, estado, conclusion, detectada_en, cerrada_en) VALUES
  ((SELECT id FROM regla_monitoreo_lft WHERE codigo = 'FRACCIONAMIENTO'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000005'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000005'), (SELECT id FROM transaccion_billetera WHERE clave_idempotencia = 'demo-recarga-05'), NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), 2000.0, '{"operaciones": 1, "umbral_usd": 10000, "monto_usd": 287.36, "regla": "FRACCIONAMIENTO"}'::jsonb, 'BAJA', 'DESCARTADA', 'Operación única de Bs 2.000 (USD 287) muy por debajo del umbral, con declaración de origen verificada y respaldo documental. No hay patrón de fraccionamiento: una sola operación no puede fraccionar nada.', now() - interval '44 days', now() - interval '43 days'),
  ((SELECT id FROM regla_monitoreo_lft WHERE codigo = 'CIRCULARIDAD'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), (SELECT id FROM cuenta_billetera WHERE numero_cuenta = 'BOB-0000003'), NULL, NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), 200.0, '{"saltos": 2, "horas": 30, "regla": "CIRCULARIDAD", "cuentas": ["BOB-0000003", "BOB-0000006"]}'::jsonb, 'MEDIA', 'ESCALADA', 'Patrón compatible con la tipología de circularidad: A envía a B y B devuelve a A dentro de 30 horas. El monto es bajo y ambos son del mismo grupo, pero la regla exige análisis. Se escala a caso para revisar el vínculo entre los titulares antes de descartar.', now() - interval '20 days', NULL)
ON CONFLICT DO NOTHING;

-- El caso que abrió la alerta escalada. Tiene plazo límite guardado, analista asignado y revisor distinto: quien analiza no es quien aprueba reportar.
INSERT INTO caso_investigacion_lft (codigo, usuario_id, analista_id, revisado_por, reporte_operacion_sospechosa_id, origen, estado, prioridad, resumen, hallazgos, decision, abierto_en, plazo_limite, cerrado_en) VALUES
  ('CASO-LFT-0001', (SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), NULL, NULL, 'ALERTA', 'EN_ANALISIS', 'MEDIA', 'Movimiento de ida y vuelta de Bs 200 entre dos titulares del mismo grupo de pasanaku dentro de 30 horas. Se analiza si corresponde a un préstamo entre conocidos —uso normal del producto— o a un patrón de circularidad.', 'Ambos titulares son participantes activos del mismo grupo desde hace 45 días, con aportes al día y sin coincidencias en listas restrictivas. El movimiento coincide con la fecha de vencimiento del aporte del período 2.', NULL, now() - interval '20 days', now() + interval '10 days', NULL)
ON CONFLICT DO NOTHING;

-- Falso positivo por homonimia, resuelto por una persona. Guardar el puntaje y quién lo revisó es lo que permite defender la decisión si mañana la cuestionan.
INSERT INTO coincidencia_lista (lista_id, usuario_id, revisada_por, nombre_coincidente, puntaje_similitud, estado, revisada_en) VALUES
  ((SELECT id FROM lista_restrictiva_externa WHERE nombre_lista = 'OFAC_SDN' AND version = 'pendiente-carga'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000002'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), 'JUAN CARLOS ROJAS', 0.8421, 'FALSO_POSITIVO', now() - interval '100 days')
ON CONFLICT DO NOTHING;

-- Borrador de ROS asociado al caso abierto. Sin aprobación del oficial de cumplimiento no se envía, y sin envío no hay número de radicado.
INSERT INTO reporte_operacion_sospechosa (usuario_id, aprobado_por, tipologia, monto_total, periodo_analizado, narrativa, estado, numero_radicado, enviado_en) VALUES
  ((SELECT id FROM usuario WHERE codigo_publico = 'USR000003'), NULL, 'Circularidad entre cuentas del mismo círculo', 200.0, 'últimos 30 días', 'Se detectó un movimiento de ida y vuelta de Bs 200 entre dos titulares del mismo grupo de pasanaku dentro de 30 horas. El análisis preliminar sugiere un préstamo entre conocidos, que es un uso esperable del producto. El caso sigue abierto: este borrador existe para no perder el trabajo de análisis si la decisión final fuera reportar.', 'BORRADOR', NULL, NULL)
ON CONFLICT DO NOTHING;

-- Tres reportes en tres estados. En el ENVIADO, quien lo generó (cumplimiento) NO es quien lo aprobó (riesgos): `ck_reporte_segregacion` lo exige, y es la segregación que evita que una sola persona pueda remitirle cualquier cosa al regulador. `ck_reporte_en_cero` obliga a que la bandera coincida con la cantidad de registros: no se puede marcar en cero un reporte con filas, ni dejar sin marcar uno vacío. El CIRO pendiente todavía no se generó, así que va en cero.
INSERT INTO reporte_regulatorio (catalogo_reporte_id, generado_por, revisado_por, aprobado_por, periodo, fecha_corte, estado, cantidad_registros, reporte_en_cero, monto_total, url_archivo, hash_archivo, fecha_limite, generado_en) VALUES
  ((SELECT id FROM catalogo_reporte_regulatorio WHERE codigo = 'PCC-01'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000009'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000009'), '2026-06', (current_date + interval '-45 days'), 'ENVIADO', 0, TRUE, 0.0, 'https://almacen.pasanaku.test/reportes/PCC-01-2026-06.csv', encode(digest('pcc01-2026-06', 'sha256'), 'hex'), (current_date + interval '-30 days'), now() - interval '40 days'),
  ((SELECT id FROM catalogo_reporte_regulatorio WHERE codigo = 'RECLAMOS-M'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000008'), NULL, NULL, '2026-07', (current_date + interval '-15 days'), 'GENERADO', 1, FALSE, 10.0, 'https://almacen.pasanaku.test/reportes/RECLAMOS-M-2026-07.csv', encode(digest('reclamos-2026-07', 'sha256'), 'hex'), (current_date + interval '5 days'), now() - interval '2 days'),
  ((SELECT id FROM catalogo_reporte_regulatorio WHERE codigo = 'CIRO'), NULL, NULL, NULL, '2026-T2', (current_date + interval '-20 days'), 'PENDIENTE', 0, TRUE, 0.0, NULL, NULL, (current_date + interval '12 days'), NULL)
ON CONFLICT DO NOTHING;

-- El acuse del organismo con su número de constancia. Sin esta fila el reporte está generado pero NO remitido, y el control de vencimientos lo sigue contando como pendiente.
INSERT INTO envio_regulatorio (reporte_regulatorio_id, enviado_por, organismo, canal, fecha_envio, numero_constancia, estado, respuesta, reintentos) VALUES
  ((SELECT r.id FROM reporte_regulatorio r JOIN catalogo_reporte_regulatorio c ON c.id = r.catalogo_reporte_id WHERE c.codigo = 'PCC-01' AND r.periodo = '2026-06'), (SELECT id FROM usuario WHERE codigo_publico = 'USR000007'), 'UIF', 'PORTAL_WEB', now() - interval '38 days', 'UIF-2026-06-0000123', 'ACEPTADO', '{"codigo": "OK", "mensaje": "Reporte recibido conforme", "registros_aceptados": 0}'::jsonb, 0)
ON CONFLICT DO NOTHING;

-- Una observación de ASFI ya subsanada, con su plazo y su respuesta. Es el registro que evita que la misma observación vuelva a aparecer sin que nadie recuerde qué se hizo.
INSERT INTO observacion_regulatoria (envio_regulatorio_id, responsable_id, organismo, tipo, numero_documento, descripcion, monto_multa, plazo_respuesta, estado, respuesta, recibida_en, respondida_en) VALUES
  (NULL, (SELECT id FROM usuario WHERE codigo_publico = 'USR000009'), 'ASFI', 'REQUERIMIENTO', 'ASFI/DSR/2026/0087', 'Se solicita detallar el procedimiento de conciliación diaria de la cuenta de custodia y la evidencia de su ejecución en el último trimestre.', NULL, (current_date + interval '-55 days'), 'SUBSANADA', 'Se remitió el procedimiento PRO-CUS-01, los reportes diarios de conciliación del trimestre y el detalle del control CI-02 con su evidencia de ejecución.', now() - interval '70 days', now() - interval '60 days')
ON CONFLICT DO NOTHING;
