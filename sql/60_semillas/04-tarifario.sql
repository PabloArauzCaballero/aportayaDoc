-- Política de cobro — tarifario v1: 0,3 % de la bolsa con piso Bs 10 y techo Bs 50, a cargo del beneficiario del turno, deducido de la entrega.
-- GENERADO desde seeders/minimos/04-tarifario.json — no editar a mano.

-- Hechos del sistema sobre los que se puede cobrar
INSERT INTO catalogo_hecho_generador (codigo, descripcion, entidad_evento, campo_monto_base, unidad_conteo, modulo_origen, activo) VALUES
  ('ENTREGA_FONDO_ACREDITADA', 'El beneficiario del turno cobró la bolsa', 'entrega_fondo', 'monto_bolsa_bruto', 'ENTREGA', '04', TRUE),
  ('APORTE_ACREDITADO', 'Un aporte fue acreditado y conciliado', 'pago', 'monto', 'PAGO', '03', TRUE),
  ('RECARGA_ACREDITADA', 'El usuario cargó saldo en su billetera', 'orden_recarga', 'monto_bruto', 'RECARGA', '10', TRUE),
  ('RETIRO_EJECUTADO', 'El usuario retiró saldo de su billetera', 'orden_retiro', 'monto_solicitado', 'RETIRO', '10', TRUE),
  ('TRANSFERENCIA_EJECUTADA', 'Transferencia entre billeteras', 'transferencia_p2p', 'monto', 'TRANSFERENCIA', '10', TRUE),
  ('CICLO_INICIADO', 'Arrancó un ciclo del grupo', 'periodo', NULL, 'CICLO', '02', TRUE),
  ('PARTICIPANTE_INSCRITO', 'Un participante tomó un cupo', 'participante', NULL, 'PARTICIPANTE', '02', TRUE)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO tarifario (codigo, version, nombre, estado, moneda_base, vigente_desde, dias_preaviso, publicado_en, url_publicacion, hash_documento) VALUES
  ('GENERAL', 1, 'Tarifario general v1', 'VIGENTE', 'BOB', now(), 30, now(), 'https://pasanaku.bo/legal/tarifario-v1.pdf', repeat('0', 64))
ON CONFLICT (codigo, version) DO NOTHING;

-- El único concepto que cobra al arrancar
INSERT INTO concepto_tarifa (tarifario_id, hecho_generador_id, politica_redondeo_id, cuenta_ingreso_id, codigo, nombre_comercial, descripcion_usuario, metodo_calculo, base_calculo, valor_porcentual, monto_minimo, monto_maximo, sujeto_obligado, forma_cobro, momento_cobro, gravado_iva, gravado_it, precio_incluye_impuesto, orden_aplicacion, activo) VALUES
  ((SELECT id FROM tarifario WHERE codigo = 'GENERAL' AND version = 1), (SELECT id FROM catalogo_hecho_generador WHERE codigo = 'ENTREGA_FONDO_ACREDITADA'), (SELECT id FROM politica_redondeo WHERE codigo = 'BOB_COMISION'), (SELECT id FROM cuenta_contable WHERE codigo = '4.1.01'), 'COM_ENTREGA', 'Comisión por cobro de turno', 'Se descuenta de la bolsa cuando cobrás tu turno. Incluye impuestos.', 'PORCENTUAL', 'MONTO_BOLSA_BRUTO', 0.3, 10.0, 50.0, 'BENEFICIARIO_DEL_TURNO', 'DEDUCCION_DE_ENTREGA', 'AL_LIQUIDAR_ENTREGA', TRUE, TRUE, TRUE, 1, TRUE)
ON CONFLICT (tarifario_id, codigo) DO NOTHING;

-- Lo gratuito también se declara: el tarifario publicado muestra lo que no se cobra
INSERT INTO concepto_tarifa (tarifario_id, hecho_generador_id, codigo, nombre_comercial, descripcion_usuario, metodo_calculo, base_calculo, sujeto_obligado, forma_cobro, momento_cobro, gravado_iva, gravado_it, precio_incluye_impuesto, orden_aplicacion, activo) VALUES
  ((SELECT id FROM tarifario WHERE codigo = 'GENERAL' AND version = 1), (SELECT id FROM catalogo_hecho_generador WHERE codigo = 'APORTE_ACREDITADO'), 'COM_APORTE', 'Aporte al grupo', 'Aportar a tu pasanaku no tiene costo.', 'GRATUITO', 'SIN_BASE', 'PAGADOR_DE_LA_OPERACION', 'DEBITO_DE_BILLETERA', 'AL_DEVENGAR', FALSE, FALSE, TRUE, 10, TRUE),
  ((SELECT id FROM tarifario WHERE codigo = 'GENERAL' AND version = 1), (SELECT id FROM catalogo_hecho_generador WHERE codigo = 'RECARGA_ACREDITADA'), 'COM_RECARGA', 'Carga de saldo', 'Cargar saldo no tiene costo.', 'GRATUITO', 'SIN_BASE', 'PAGADOR_DE_LA_OPERACION', 'DEBITO_DE_BILLETERA', 'AL_DEVENGAR', FALSE, FALSE, TRUE, 11, TRUE),
  ((SELECT id FROM tarifario WHERE codigo = 'GENERAL' AND version = 1), (SELECT id FROM catalogo_hecho_generador WHERE codigo = 'RETIRO_EJECUTADO'), 'COM_RETIRO', 'Retiro de saldo', 'Retirar tu saldo no tiene costo.', 'GRATUITO', 'SIN_BASE', 'PAGADOR_DE_LA_OPERACION', 'DEBITO_DE_BILLETERA', 'AL_DEVENGAR', FALSE, FALSE, TRUE, 12, TRUE),
  ((SELECT id FROM tarifario WHERE codigo = 'GENERAL' AND version = 1), (SELECT id FROM catalogo_hecho_generador WHERE codigo = 'TRANSFERENCIA_EJECUTADA'), 'COM_TRANSF', 'Transferencia', 'Enviar saldo a otra persona no tiene costo.', 'GRATUITO', 'SIN_BASE', 'PAGADOR_DE_LA_OPERACION', 'DEBITO_DE_BILLETERA', 'AL_DEVENGAR', FALSE, FALSE, TRUE, 13, TRUE),
  ((SELECT id FROM tarifario WHERE codigo = 'GENERAL' AND version = 1), (SELECT id FROM catalogo_hecho_generador WHERE codigo = 'PARTICIPANTE_INSCRITO'), 'COM_INSCRIP', 'Inscripción al grupo', 'Entrar a un grupo no tiene costo.', 'GRATUITO', 'SIN_BASE', 'PAGADOR_DE_LA_OPERACION', 'DEBITO_DE_BILLETERA', 'AL_DEVENGAR', FALSE, FALSE, TRUE, 14, TRUE)
ON CONFLICT (tarifario_id, codigo) DO NOTHING;

-- Segmentos a los que se puede asignar un tarifario distinto. Existen desde el día uno para que una decisión comercial futura sea una fila, no una migración.
INSERT INTO segmento_comercial (codigo, descripcion, criterio, prioridad, activo) VALUES
  ('GENERAL', 'Todos los titulares que no califican a otro segmento', '{"tipo": "SIEMPRE"}'::jsonb, 100, TRUE),
  ('NUEVO_CLIENTE', 'Titulares con menos de 90 días desde la apertura de la billetera', '{"tipo": "ANTIGUEDAD_DIAS", "operador": "<", "valor": 90}'::jsonb, 20, TRUE),
  ('ALTO_VOLUMEN', 'Titulares con más de Bs 20.000 operados en los últimos 90 días', '{"tipo": "VOLUMEN_BOB_90D", "operador": ">=", "valor": 20000}'::jsonb, 10, TRUE),
  ('ORGANIZADOR_MAESTRO', 'Organizadores con nivel MAESTRO vigente y sin sanción firme', '{"tipo": "NIVEL_ORGANIZADOR", "operador": "=", "valor": "MAESTRO"}'::jsonb, 5, TRUE)
ON CONFLICT (codigo) DO NOTHING;

-- Tramos de la comisión de entrega. La regla que coincide gana sobre el valor por defecto del concepto (0,3 %); el piso de Bs 10 y el techo de Bs 50 se mantienen en todos los tramos, que es lo que impide que una bolsa chica quede confiscada por la comisión.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM regla_tarifa) THEN
  INSERT INTO regla_tarifa (concepto_tarifa_id, orden, condicion, monto_base_desde, monto_base_hasta, valor_porcentual, valor_fijo, monto_minimo, monto_maximo, vigente_desde, vigente_hasta) VALUES
    ((SELECT id FROM concepto_tarifa WHERE codigo = 'COM_ENTREGA' AND tarifario_id = (SELECT id FROM tarifario WHERE codigo = 'GENERAL' AND version = 1)), 1, '{"tramo": "bolsa pequeña"}'::jsonb, 0.0, 3000.0, 0.35, NULL, 10.0, 50.0, '2026-01-01T00:00:00-04:00', NULL),
    ((SELECT id FROM concepto_tarifa WHERE codigo = 'COM_ENTREGA' AND tarifario_id = (SELECT id FROM tarifario WHERE codigo = 'GENERAL' AND version = 1)), 2, '{"tramo": "bolsa media"}'::jsonb, 3000.01, 10000.0, 0.3, NULL, 10.0, 50.0, '2026-01-01T00:00:00-04:00', NULL),
    ((SELECT id FROM concepto_tarifa WHERE codigo = 'COM_ENTREGA' AND tarifario_id = (SELECT id FROM tarifario WHERE codigo = 'GENERAL' AND version = 1)), 3, '{"tramo": "bolsa grande"}'::jsonb, 10000.01, NULL, 0.25, NULL, 10.0, 50.0, '2026-01-01T00:00:00-04:00', NULL);
  END IF;
END $$;

-- Quién cobra con qué tarifario. La asignación GLOBAL es la red de seguridad: sin ella, un titular sin segmento no tendría tarifa y la operación se rechazaría por omisión.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM asignacion_tarifario) THEN
  INSERT INTO asignacion_tarifario (tarifario_id, segmento_id, grupo_id, usuario_id, autorizado_por, ambito, prioridad, motivo, vigente_desde, vigente_hasta) VALUES
    ((SELECT id FROM tarifario WHERE codigo = 'GENERAL' AND version = 1), NULL, NULL, NULL, NULL, 'GLOBAL', 100, 'Tarifario general aplicable por defecto a toda la plataforma', '2026-01-01T00:00:00-04:00', NULL),
    ((SELECT id FROM tarifario WHERE codigo = 'GENERAL' AND version = 1), (SELECT id FROM segmento_comercial WHERE codigo = 'GENERAL'), NULL, NULL, NULL, 'SEGMENTO', 90, 'Segmento general — mismo tarifario mientras no exista decisión comercial distinta', '2026-01-01T00:00:00-04:00', NULL);
  END IF;
END $$;
