-- Plan de cuentas mínimo. El saldo de los usuarios es pasivo exigible (2.1.x), nunca patrimonio.
-- GENERADO desde seeders/minimos/01-plan-de-cuentas.json — no editar a mano.

-- Jerarquía en la propia tabla: una sentencia por fila para que cada
-- hija vea a su madre ya insertada.
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1', 'Activo', 'ACTIVO', 'DEUDORA', NULL, 1, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('2', 'Pasivo', 'PASIVO', 'ACREEDORA', NULL, 1, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('3', 'Patrimonio', 'PATRIMONIO', 'ACREEDORA', NULL, 1, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('4', 'Ingresos', 'INGRESO', 'ACREEDORA', NULL, 1, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('5', 'Egresos', 'EGRESO', 'DEUDORA', NULL, 1, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.1', 'Disponibilidades', 'ACTIVO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1'), 2, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.2', 'Cuentas por cobrar', 'ACTIVO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1'), 2, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.3', 'Activo fijo', 'ACTIVO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1'), 2, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('2.1', 'Obligaciones con usuarios y grupos', 'PASIVO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '2'), 2, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('2.2', 'Obligaciones fiscales', 'PASIVO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '2'), 2, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('2.3', 'Obligaciones comerciales', 'PASIVO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '2'), 2, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('3.1', 'Capital', 'PATRIMONIO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '3'), 2, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('3.2', 'Resultados', 'PATRIMONIO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '3'), 2, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('4.1', 'Ingresos por comisiones', 'INGRESO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '4'), 2, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('4.2', 'Ingresos por publicidad', 'INGRESO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '4'), 2, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('5.1', 'Costos de operación', 'EGRESO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '5'), 2, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('5.2', 'Pérdidas y devoluciones', 'EGRESO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '5'), 2, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('5.3', 'Gastos de administración', 'EGRESO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '5'), 2, FALSE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.1.01', 'Cuenta de custodia — dinero electrónico', 'ACTIVO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1.1'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.1.02', 'Bancos recaudadores', 'ACTIVO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1.1'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.1.03', 'Efectivo en puntos de atención', 'ACTIVO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1.1'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.2.01', 'Cuentas por cobrar — comisiones', 'ACTIVO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1.2'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.2.02', 'Cuentas por cobrar — subrogación fondo de garantía', 'ACTIVO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1.2'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.2.03', 'Cuentas por cobrar — publicidad', 'ACTIVO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1.2'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.3.01', 'Equipo de computación', 'ACTIVO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1.3'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.3.02', 'Muebles y enseres', 'ACTIVO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1.3'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.3.03', 'Licencias y software', 'ACTIVO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1.3'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.3.51', 'Depreciación acumulada — equipo de computación', 'ACTIVO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1.3'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.3.52', 'Depreciación acumulada — muebles y enseres', 'ACTIVO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1.3'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('1.3.53', 'Depreciación acumulada — licencias y software', 'ACTIVO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '1.3'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('2.1.01', 'Dinero electrónico por pagar — usuarios', 'PASIVO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '2.1'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('2.1.02', 'Dinero electrónico por pagar — grupos', 'PASIVO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '2.1'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('2.1.03', 'Fondo de garantía — recursos de los grupos', 'PASIVO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '2.1'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('2.1.04', 'Saldos en suspenso no identificados', 'PASIVO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '2.1'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('2.2.01', 'IVA débito fiscal por pagar', 'PASIVO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '2.2'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('2.2.02', 'Impuesto a las transacciones por pagar', 'PASIVO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '2.2'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('2.3.01', 'Cuentas por pagar — proveedores', 'PASIVO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '2.3'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('3.1.01', 'Capital', 'PATRIMONIO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '3.1'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('3.2.01', 'Resultados acumulados', 'PATRIMONIO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '3.2'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('4.1.01', 'Ingresos por comisión de servicio', 'INGRESO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '4.1'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('4.1.02', 'Ingresos por comisión de retiro', 'INGRESO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '4.1'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('4.2.01', 'Ingresos por publicidad en la aplicación', 'INGRESO', 'ACREEDORA', (SELECT id FROM cuenta_contable WHERE codigo = '4.2'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('5.1.01', 'Costos de proveedores de pago', 'EGRESO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '5.1'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('5.1.02', 'Costos de mensajería', 'EGRESO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '5.1'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('5.2.01', 'Pérdidas por riesgo operativo', 'EGRESO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '5.2'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('5.2.02', 'Devoluciones y bonificaciones a clientes', 'EGRESO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '5.2'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('5.3.01', 'Depreciación de activo fijo', 'EGRESO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '5.3'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO cuenta_contable (codigo, nombre, tipo, naturaleza, cuenta_padre_id, nivel, es_cuenta_de_movimiento, saldo) VALUES
  ('5.3.02', 'Gastos generales y servicios de terceros', 'EGRESO', 'DEUDORA', (SELECT id FROM cuenta_contable WHERE codigo = '5.3'), 3, TRUE, 0)
ON CONFLICT (codigo) DO NOTHING;
