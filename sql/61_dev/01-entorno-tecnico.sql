-- Entorno técnico local: tipo de cambio, cuentas de sistema y cuenta de custodia.
-- GENERADO desde seeders/dev/01-entorno-tecnico.json — no editar a mano.

-- Serie diaria de 60 días hacia atrás y 30 hacia adelante. Sin tipo de cambio del día de la operación, fn_fx_a_usd() falla y R-UIF-04 rechaza la transacción: una operación no se puede registrar si no se puede expresar en dólares para evaluar el umbral. Los datos de prueba tienen fechas históricas, así que necesitan la serie completa, no solo el día de hoy.
INSERT INTO tipo_cambio (moneda_origen, moneda_destino, fecha, tipo_cambio, fuente, cargado_en)
SELECT 'BOB', 'USD', d::date, 0.143678, 'MANUAL', now()
  FROM generate_series(current_date - 60, current_date + 30, interval '1 day') AS d
 ON CONFLICT DO NOTHING;

-- Cuentas técnicas: contrapartida de todo movimiento. Son las únicas con permite_saldo_negativo, porque representan la posición del sistema, no el dinero de una persona (R-BIL-02).
INSERT INTO cuenta_billetera (numero_cuenta, tipo, moneda, estado, nivel_debida_diligencia, fecha_apertura, cuenta_contable_id, permite_saldo_negativo) VALUES
  ('SYS-INGRESOS', 'PLATAFORMA_INGRESOS', 'BOB', 'ACTIVA', 'REFORZADA', now(), (SELECT id FROM cuenta_contable WHERE codigo = '4.1.01'), TRUE),
  ('SYS-IMPUESTOS', 'PLATAFORMA_IMPUESTOS_POR_PAGAR', 'BOB', 'ACTIVA', 'REFORZADA', now(), (SELECT id FROM cuenta_contable WHERE codigo = '2.2.01'), TRUE),
  ('SYS-CUSTODIA', 'PUENTE_CUSTODIA', 'BOB', 'ACTIVA', 'REFORZADA', now(), (SELECT id FROM cuenta_contable WHERE codigo = '1.1.01'), TRUE),
  ('SYS-SUSPENSO', 'SUSPENSO_NO_IDENTIFICADO', 'BOB', 'ACTIVA', 'REFORZADA', now(), (SELECT id FROM cuenta_contable WHERE codigo = '2.1.04'), TRUE)
ON CONFLICT (numero_cuenta) DO NOTHING;

-- `version_llave` dice con qué versión de la llave maestra se cifró el dato: rotar la llave es cifrar de nuevo y subir el número, no perder el acceso a lo viejo.
INSERT INTO cuenta_custodia (tipo, entidad_financiera, numero_cuenta_cifrado, numero_enmascarado, moneda, saldo_segun_banco, saldo_segun_libro, fecha_saldo, contrato_referencia, es_principal, estado, abierta_en, version_llave) VALUES
  ('FIDEICOMISO', 'BANCO DEMO S.A.', 'cifrado-demo', '****4321', 'BOB', 0, 0, now(), 'FID-DEMO-001', TRUE, 'ACTIVA', current_date, 1)
ON CONFLICT DO NOTHING;

-- Solo en desarrollo: los contratos de adhesión reales se registran ante ASFI antes de pasar a VIGENTE
UPDATE contrato_adhesion SET estado = 'VIGENTE' WHERE estado = 'BORRADOR';

-- Solo en desarrollo: se encienden los dos adaptadores de mensajería por defecto. CORREO apunta al buzón local (MailHog en el compose de dev), así que ningún correo sale a internet. WhatsApp, SMS y voz quedan apagados: son adaptadores opcionales.
UPDATE proveedor_mensajeria
   SET activo = true,
       url_base = 'http://mailhog:8025/api/v2/messages'
 WHERE codigo = 'CORREO_SMTP';
UPDATE proveedor_mensajeria
   SET activo = true,
       url_base = 'interno://notificaciones/push-simulado'
 WHERE codigo = 'PUSH_APP';
UPDATE proveedor_mensajeria
   SET activo = false
 WHERE codigo IN ('WHATSAPP_BSP', 'SMS_LOCAL', 'VOZ_IVR');
