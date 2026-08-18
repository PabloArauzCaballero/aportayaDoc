-- Catálogos mínimos de contabilidad ERP (M13) y publicidad (M14): categorías de activo fijo con sus cuentas contables, centros de costo y el inventario de espacios publicitarios de la app.
-- GENERADO desde seeders/minimos/21-contabilidad-y-publicidad.json — no editar a mano.

INSERT INTO centro_costo (codigo, nombre, tipo, activo) VALUES
  ('CC-PLATAFORMA', 'Plataforma y tecnología', 'AREA', TRUE),
  ('CC-OPERACIONES', 'Operaciones y soporte', 'AREA', TRUE),
  ('CC-CUMPLIMIENTO', 'Cumplimiento y riesgos', 'AREA', TRUE),
  ('CC-COMERCIAL', 'Comercial y publicidad', 'AREA', TRUE),
  ('CC-PASANAKU', 'Producto pasanaku', 'PRODUCTO', TRUE)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO categoria_activo_fijo (codigo, nombre, vida_util_meses, metodo_depreciacion, cuenta_activo_id, cuenta_depreciacion_id, cuenta_gasto_depreciacion_id) VALUES
  ('CAT-EQUIPO-COMPUTO', 'Equipo de computación', 48, 'LINEA_RECTA', (SELECT id FROM cuenta_contable WHERE codigo = '1.3.01'), (SELECT id FROM cuenta_contable WHERE codigo = '1.3.51'), (SELECT id FROM cuenta_contable WHERE codigo = '5.3.01')),
  ('CAT-MOBILIARIO', 'Muebles y enseres', 120, 'LINEA_RECTA', (SELECT id FROM cuenta_contable WHERE codigo = '1.3.02'), (SELECT id FROM cuenta_contable WHERE codigo = '1.3.52'), (SELECT id FROM cuenta_contable WHERE codigo = '5.3.01')),
  ('CAT-SOFTWARE', 'Licencias y software', 36, 'LINEA_RECTA', (SELECT id FROM cuenta_contable WHERE codigo = '1.3.03'), (SELECT id FROM cuenta_contable WHERE codigo = '1.3.53'), (SELECT id FROM cuenta_contable WHERE codigo = '5.3.01'))
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO espacio_publicitario (codigo, nombre, tipo, capacidad_maxima_simultanea, activo) VALUES
  ('ESP-BANNER-INICIO', 'Banner de la pantalla de inicio', 'BANNER_INICIO', 3, TRUE),
  ('ESP-GRUPOS-DESTACADO', 'Grupo destacado en el listado de pasanakus', 'LISTADO_GRUPOS_DESTACADO', 5, TRUE),
  ('ESP-PUSH-PATROCINADO', 'Notificación patrocinada', 'PUSH_PATROCINADO', 1, TRUE),
  ('ESP-BANNER-BILLETERA', 'Banner de la pantalla de billetera', 'BANNER_BILLETERA', 2, TRUE)
ON CONFLICT (codigo) DO NOTHING;
