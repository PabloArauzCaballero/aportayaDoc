-- Catálogo de proveedores de pago y de mensajería con su prioridad de enrutamiento. Ningún endpoint elige proveedor por nombre en el código: elige por prioridad y salud sobre esta tabla.
-- GENERADO desde seeders/minimos/14-proveedores-externos.json — no editar a mano.

-- Prioridad 1 es el riel preferido. La conmutación a prioridad 2 la decide la salud medida, nunca una constante en el código, y siempre queda registrada (R-PRV).
INSERT INTO proveedor_pago (codigo, nombre, tipo, url_base, referencia_credenciales, comision_fija, comision_porcentual, soporta_webhook, soporta_consulta_estado, activo, prioridad) VALUES
  ('QR_INTEROP', 'QR interoperable — Reglamento de Servicios de Pago del BCB', 'PASARELA', 'https://qr.proveedor.example.bo/v1', 'secreto://aportaya/pagos/qr-interop', 0.0, 0.35, TRUE, TRUE, FALSE, 1),
  ('ACH_INTERBANCARIA', 'Transferencia interbancaria — cámara de compensación', 'BANCO', 'https://ach.proveedor.example.bo/v1', 'secreto://aportaya/pagos/ach', 2.5, 0.0, FALSE, TRUE, FALSE, 2),
  ('BILLETERA_TERCERO', 'Billetera móvil de tercero — abono a cuenta de custodia', 'BILLETERA', 'https://billetera.proveedor.example.bo/v1', 'secreto://aportaya/pagos/billetera-tercero', 1.0, 0.5, TRUE, TRUE, FALSE, 3),
  ('AGENTE_EFECTIVO', 'Red de agentes corresponsales — recaudación en efectivo', 'PASARELA', 'https://agentes.proveedor.example.bo/v1', 'secreto://aportaya/pagos/agentes', 3.0, 0.0, TRUE, TRUE, FALSE, 4),
  ('TARJETA_ADQUIRENCIA', 'Adquirencia de tarjetas de débito y crédito', 'PASARELA', 'https://tarjetas.proveedor.example.bo/v1', 'secreto://aportaya/pagos/tarjetas', 0.0, 2.85, TRUE, TRUE, FALSE, 5),
  ('BANCO_CUSTODIO', 'Banco custodio — extractos y desembolsos de la cuenta de custodia', 'BANCO', 'https://custodio.proveedor.example.bo/v1', 'secreto://aportaya/custodia/banco', 0.0, 0.0, FALSE, TRUE, FALSE, 9)
ON CONFLICT (codigo) DO NOTHING;

-- Orden de prioridad = orden de los adaptadores por defecto: bandeja interna, push y correo. La bandeja nace activa porque no depende de ningún tercero ni cuesta nada; los demás nacen apagados y los enciende el entorno (dev) o un contrato (producción). `salud_porcentaje` arranca en 100 y la recalcula la ventana móvil de entregas. La cadena de respaldo por evento (archivo 15) decide a qué canal se cae, no este orden.
INSERT INTO proveedor_mensajeria (codigo, nombre, canales_soportados, url_base, referencia_credenciales, costo_por_mensaje, limite_mensajes_por_segundo, prioridad, activo, salud_porcentaje) VALUES
  ('BANDEJA_INTERNA', 'Bandeja de entrada de la app — adaptador interno, sin proveedor', 'IN_APP', 'interno://notificaciones/bandeja', 'no-aplica', 0.0, 1000, 1, TRUE, 100.0),
  ('WHATSAPP_BSP', 'WhatsApp Business — adaptador opcional, apagado', 'WHATSAPP', 'https://waba.proveedor.example.bo/v1', 'secreto://aportaya/mensajeria/whatsapp', 0.28, 80, 8, FALSE, 100.0),
  ('PUSH_APP', 'Notificación push de la aplicación móvil', 'PUSH', 'https://push.proveedor.example.bo/v1', 'secreto://aportaya/mensajeria/push', 0.0, 500, 2, FALSE, 100.0),
  ('SMS_LOCAL', 'SMS por operador local — adaptador opcional, apagado', 'SMS', 'https://sms.proveedor.example.bo/v1', 'secreto://aportaya/mensajeria/sms', 0.42, 30, 9, FALSE, 100.0),
  ('CORREO_SMTP', 'Correo transaccional — canal por defecto junto con la bandeja', 'CORREO', 'https://correo.proveedor.example.bo/v1', 'secreto://aportaya/mensajeria/correo', 0.02, 100, 3, FALSE, 100.0),
  ('VOZ_IVR', 'Llamada de voz automatizada — último recurso de cobranza', 'LLAMADA_VOZ', 'https://voz.proveedor.example.bo/v1', 'secreto://aportaya/mensajeria/voz', 1.1, 10, 10, FALSE, 100.0)
ON CONFLICT (codigo) DO NOTHING;
