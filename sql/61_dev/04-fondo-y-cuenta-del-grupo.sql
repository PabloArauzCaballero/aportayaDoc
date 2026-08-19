-- Fondo de garantía del grupo demo.
-- GENERADO desde seeders/dev/04-fondo-y-cuenta-del-grupo.json — no editar a mano.

INSERT INTO fondo_garantia (ambito, grupo_id, politica_cobertura_id, cuenta_contable_id, moneda, saldo_disponible, saldo_comprometido, total_aportado, total_cubierto, total_recuperado, estado, total_castigado, version) VALUES
  ('POR_GRUPO', (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), (SELECT id FROM politica_cobertura LIMIT 1), (SELECT id FROM cuenta_contable WHERE codigo = '2.1.03'), 'BOB', 0, 0, 0, 0, 0, 'ACTIVO', 0, 0)
ON CONFLICT DO NOTHING;

-- La bolsa del grupo tiene cuenta propia: el titular es el grupo, nunca el organizador (R-GRP-04)
INSERT INTO cuenta_billetera (numero_cuenta, tipo, grupo_id, moneda, estado, nivel_debida_diligencia, fecha_apertura, cuenta_contable_id) VALUES
  ('GRP-0000001', 'GRUPO', (SELECT id FROM grupo WHERE codigo_publico = 'GRP-DEMO-01'), 'BOB', 'ACTIVA', 'ESTANDAR', now(), (SELECT id FROM cuenta_contable WHERE codigo = '2.1.02'))
ON CONFLICT (numero_cuenta) DO NOTHING;
