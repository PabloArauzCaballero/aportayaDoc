-- El andamiaje que una inspección pide primero: políticas internas, controles con dueño y frecuencia, inventario de activos de información, planes de continuidad con RTO y RPO, y los documentos que la norma obliga a publicar.
-- GENERADO desde seeders/minimos/20-control-interno-y-continuidad.json — no editar a mano.

-- Cuerpo normativo interno mínimo para operar como entidad supervisada. Cada fila lleva su fecha de próxima revisión: una política sin revisión vencida es un hallazgo de auditoría.
INSERT INTO politica_interna (acta_comite_id, responsable_id, codigo, tipo, materia, version, estado, url_documento, hash_documento, aprobada_por_directorio, vigente_desde, proxima_revision) VALUES
  (NULL, NULL, 'POL-LGIFT-01', 'POLITICA', 'LGI_FT', 1, 'EN_APROBACION', 'https://pasanaku.bo/gobierno/politica-lgift-v1.pdf', repeat('0', 64), FALSE, '2026-01-01', '2027-01-01'),
  (NULL, NULL, 'MAN-LGIFT-01', 'MANUAL', 'LGI_FT', 1, 'EN_APROBACION', 'https://pasanaku.bo/gobierno/manual-lgift-v1.pdf', repeat('0', 64), FALSE, '2026-01-01', '2027-01-01'),
  (NULL, NULL, 'PRO-LGIFT-ROS', 'PROCEDIMIENTO', 'LGI_FT', 1, 'EN_APROBACION', 'https://pasanaku.bo/gobierno/procedimiento-ros-v1.pdf', repeat('0', 64), FALSE, '2026-01-01', '2027-01-01'),
  (NULL, NULL, 'MET-RIESGO-LFT', 'METODOLOGIA', 'LGI_FT', 1, 'EN_APROBACION', 'https://pasanaku.bo/gobierno/metodologia-riesgo-lft-v1.pdf', repeat('0', 64), FALSE, '2026-01-01', '2027-01-01'),
  (NULL, NULL, 'POL-RO-01', 'POLITICA', 'RIESGO_OPERATIVO', 1, 'EN_APROBACION', 'https://pasanaku.bo/gobierno/politica-riesgo-operativo-v1.pdf', repeat('0', 64), FALSE, '2026-01-01', '2027-01-01'),
  (NULL, NULL, 'MAN-RO-01', 'MANUAL', 'RIESGO_OPERATIVO', 1, 'EN_APROBACION', 'https://pasanaku.bo/gobierno/manual-riesgo-operativo-v1.pdf', repeat('0', 64), FALSE, '2026-01-01', '2027-01-01'),
  (NULL, NULL, 'POL-SI-01', 'POLITICA', 'SEGURIDAD_INFORMACION', 1, 'EN_APROBACION', 'https://pasanaku.bo/gobierno/politica-seguridad-informacion-v1.pdf', repeat('0', 64), FALSE, '2026-01-01', '2027-01-01'),
  (NULL, NULL, 'PRO-SI-INC', 'PROCEDIMIENTO', 'SEGURIDAD_INFORMACION', 1, 'EN_APROBACION', 'https://pasanaku.bo/gobierno/procedimiento-incidentes-seguridad-v1.pdf', repeat('0', 64), FALSE, '2026-01-01', '2027-01-01'),
  (NULL, NULL, 'PLA-CONT-01', 'PLAN', 'CONTINUIDAD', 1, 'EN_APROBACION', 'https://pasanaku.bo/gobierno/plan-continuidad-negocio-v1.pdf', repeat('0', 64), FALSE, '2026-01-01', '2027-01-01'),
  (NULL, NULL, 'POL-CONS-01', 'POLITICA', 'CONSUMIDOR', 1, 'EN_APROBACION', 'https://pasanaku.bo/gobierno/politica-consumidor-financiero-v1.pdf', repeat('0', 64), FALSE, '2026-01-01', '2027-01-01'),
  (NULL, NULL, 'PRO-CONS-RECL', 'PROCEDIMIENTO', 'CONSUMIDOR', 1, 'EN_APROBACION', 'https://pasanaku.bo/gobierno/procedimiento-reclamos-v1.pdf', repeat('0', 64), FALSE, '2026-01-01', '2027-01-01'),
  (NULL, NULL, 'POL-TERC-01', 'POLITICA', 'TERCERIZACION', 1, 'EN_APROBACION', 'https://pasanaku.bo/gobierno/politica-tercerizacion-v1.pdf', repeat('0', 64), FALSE, '2026-01-01', '2027-01-01')
ON CONFLICT (version, codigo) DO NOTHING;

-- Cada control dice qué riesgo mitiga y con qué frecuencia se ejecuta. Los automatizados dejan evidencia sola; los manuales necesitan una prueba de control que alguien firme.
INSERT INTO control_interno (codigo, proceso, descripcion, tipo, frecuencia, automatizado, riesgo_mitigado, responsable_id, activo) VALUES
  ('CI-01', 'Contabilidad de billetera', 'Cuadre de partida doble: para cada transacción, la suma de débitos iguala la de créditos antes de confirmar', 'PREVENTIVO', 'CONTINUA', TRUE, 'Descuadre de saldos y pérdida contable', NULL, TRUE),
  ('CI-02', 'Custodia de fondos', 'Conciliación diaria entre el saldo de libro y el extracto del banco custodio, con excepciones abiertas identificadas', 'DETECTIVO', 'DIARIA', TRUE, 'Encaje insuficiente y dinero del cliente no respaldado', NULL, TRUE),
  ('CI-03', 'Reversos y ajustes', 'Doble autorización para todo reverso o ajuste operativo: quien lo pide no puede aprobarlo', 'PREVENTIVO', 'CONTINUA', TRUE, 'Fraude interno y ajuste no autorizado', NULL, TRUE),
  ('CI-04', 'Gestión de accesos', 'Revisión de segregación de funciones: ningún usuario acumula alta de beneficiario y autorización de desembolso', 'DETECTIVO', 'TRIMESTRAL', FALSE, 'Concentración de funciones incompatibles', NULL, TRUE),
  ('CI-05', 'Prevención LGI/FT', 'Tratamiento de alertas de monitoreo dentro del plazo, con decisión motivada y trazable', 'DETECTIVO', 'DIARIA', FALSE, 'Omisión de reporte de operación sospechosa', NULL, TRUE),
  ('CI-06', 'Efectivo en puntos de atención', 'Arqueo por punto y fecha, con diferencia derivada y evento de riesgo si hay faltante', 'DETECTIVO', 'DIARIA', FALSE, 'Faltante de efectivo no detectado', NULL, TRUE),
  ('CI-07', 'Remisión regulatoria', 'Control de vencimientos del calendario de reportes: alerta antes de vencer y acuse archivado tras enviar', 'PREVENTIVO', 'DIARIA', TRUE, 'Sanción por remisión fuera de plazo', NULL, TRUE),
  ('CI-08', 'Gestión de accesos', 'Recertificación de usuarios y roles del backoffice; se revocan los accesos sin uso en 90 días', 'DETECTIVO', 'SEMESTRAL', FALSE, 'Acceso indebido a datos de clientes', NULL, TRUE),
  ('CI-09', 'Continuidad', 'Ensayo de restauración de respaldos con medición de RTO y RPO reales', 'CORRECTIVO', 'SEMESTRAL', FALSE, 'Respaldo que existe pero no restaura', NULL, TRUE),
  ('CI-10', 'Prevención LGI/FT', 'Actualización y recontraste de listas restrictivas contra la base de clientes', 'DETECTIVO', 'DIARIA', TRUE, 'Operar con persona listada', NULL, TRUE),
  ('CI-11', 'Debida diligencia', 'Detección de verificaciones de identidad vencidas y bloqueo de operativa hasta actualizar', 'PREVENTIVO', 'MENSUAL', TRUE, 'Cliente operando con debida diligencia vencida', NULL, TRUE),
  ('CI-12', 'Plazos legales', 'Carga del calendario de días no hábiles del año siguiente antes de que se agote el horizonte vigente', 'PREVENTIVO', 'ANUAL', FALSE, 'Cálculo erróneo de plazos hábiles', NULL, TRUE),
  ('CI-13', 'Transparencia', 'Verificación de que el tarifario y los contratos publicados coinciden con los vigentes en la base', 'DETECTIVO', 'MENSUAL', TRUE, 'Cobro distinto de lo publicado', NULL, TRUE),
  ('CI-14', 'Atención al consumidor', 'Revisión de reclamos próximos a vencer y de la efectividad de la reparación ofrecida', 'DETECTIVO', 'SEMANAL', FALSE, 'Reclamo vencido sin respuesta', NULL, TRUE),
  ('CI-15', 'Tercerización', 'Evaluación de proveedores críticos: vigencia contractual, nivel de servicio medido y plan de salida', 'PREVENTIVO', 'SEMESTRAL', FALSE, 'Dependencia de un proveedor sin alternativa', NULL, TRUE),
  ('CI-16', 'Entregas de fondo', 'Validaciones previas obligatorias antes de autorizar una entrega: cuenta verificada, período liquidado y sin retención vigente', 'PREVENTIVO', 'CONTINUA', TRUE, 'Entrega a beneficiario o cuenta equivocada', NULL, TRUE)
ON CONFLICT (codigo) DO NOTHING;

-- Inventario mínimo de activos. Lo que contiene datos personales exige cifrado y deja huella de acceso; lo crítico entra al plan de continuidad.
INSERT INTO activo_informacion (propietario_id, custodio_id, contrato_tercero_id, codigo, nombre, tipo, clasificacion, contiene_datos_personales, contiene_datos_sensibles, criticidad, ubicacion, exige_cifrado, ultima_revision) VALUES
  (NULL, NULL, NULL, 'BD-CORE', 'Base de datos transaccional principal', 'BASE_DATOS', 'RESERVADA', TRUE, TRUE, 'CRITICA', 'Nube — región primaria', TRUE, '2026-01-01'),
  (NULL, NULL, NULL, 'BD-REPLICA', 'Réplica de lectura para reportes y paneles', 'BASE_DATOS', 'RESERVADA', TRUE, TRUE, 'ALTA', 'Nube — región primaria', TRUE, '2026-01-01'),
  (NULL, NULL, NULL, 'API-CORE', 'API de la billetera y de los grupos', 'APLICACION', 'CONFIDENCIAL', TRUE, FALSE, 'CRITICA', 'Nube — clúster de contenedores', TRUE, '2026-01-01'),
  (NULL, NULL, NULL, 'APP-MOVIL', 'Aplicación móvil del cliente', 'APLICACION', 'PUBLICA', TRUE, FALSE, 'ALTA', 'Tiendas de aplicaciones y dispositivo del cliente', TRUE, '2026-01-01'),
  (NULL, NULL, NULL, 'APP-BACKOFFICE', 'Backoffice de operaciones y cumplimiento', 'APLICACION', 'CONFIDENCIAL', TRUE, TRUE, 'ALTA', 'Nube — acceso restringido por red', TRUE, '2026-01-01'),
  (NULL, NULL, NULL, 'ALM-DOCUMENTOS', 'Almacenamiento de documentos de identidad y respaldos de debida diligencia', 'SERVICIO', 'RESERVADA', TRUE, TRUE, 'CRITICA', 'Almacenamiento de objetos cifrado', TRUE, '2026-01-01'),
  (NULL, NULL, NULL, 'GESTOR-SECRETOS', 'Gestor de secretos y credenciales de proveedores', 'SERVICIO', 'RESERVADA', FALSE, TRUE, 'CRITICA', 'Servicio administrado de secretos', TRUE, '2026-01-01'),
  (NULL, NULL, NULL, 'RESPALDOS', 'Respaldos cifrados y recuperación a un punto en el tiempo', 'SERVICIO', 'RESERVADA', TRUE, TRUE, 'CRITICA', 'Almacenamiento de objetos en región secundaria', TRUE, '2026-01-01'),
  (NULL, NULL, NULL, 'BITACORA', 'Bitácora de eventos y registro de acceso a datos', 'BASE_DATOS', 'CONFIDENCIAL', TRUE, FALSE, 'ALTA', 'Almacenamiento inmutable con sellado', TRUE, '2026-01-01'),
  (NULL, NULL, NULL, 'INT-PASARELAS', 'Integraciones con pasarelas de pago y banco custodio', 'SERVICIO', 'CONFIDENCIAL', TRUE, FALSE, 'CRITICA', 'Salida controlada por lista blanca', TRUE, '2026-01-01'),
  (NULL, NULL, NULL, 'DOC-GOBIERNO', 'Repositorio de políticas, actas y evidencia de controles', 'DOCUMENTO', 'INTERNA', FALSE, FALSE, 'MEDIA', 'Repositorio documental con control de versiones', FALSE, '2026-01-01'),
  (NULL, NULL, NULL, 'INFRA-RED', 'Red, balanceadores y punto único de entrada público', 'INFRAESTRUCTURA', 'INTERNA', FALSE, FALSE, 'ALTA', 'Nube — red privada con una sola entrada pública', TRUE, '2026-01-01')
ON CONFLICT (codigo) DO NOTHING;

-- RTO y RPO comprometidos por proceso crítico. Son objetivos que se miden en el ensayo, no aspiraciones: si la restauración tarda más, el plan queda observado.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM plan_continuidad) THEN
  INSERT INTO plan_continuidad (politica_interna_id, responsable_id, proceso_critico, rto_minutos, rpo_minutos, estrategia, periodicidad_prueba_meses, vigente_desde, proxima_prueba) VALUES
    ((SELECT id FROM politica_interna WHERE codigo = 'PLA-CONT-01' AND version = 1), NULL, 'Acreditación de pagos y aportes', 60, 5, 'Réplica en caliente en región secundaria, reproceso de webhooks pendientes desde la cola y conciliación forzada contra el extracto del proveedor al recuperar.', 6, '2026-01-01', '2026-07-01'),
    ((SELECT id FROM politica_interna WHERE codigo = 'PLA-CONT-01' AND version = 1), NULL, 'Entrega de fondo al beneficiario', 240, 5, 'Suspensión controlada de desembolsos, retención del saldo del beneficiario y ejecución diferida con doble autorización una vez restablecida la conexión con el banco custodio.', 6, '2026-01-01', '2026-07-01'),
    ((SELECT id FROM politica_interna WHERE codigo = 'PLA-CONT-01' AND version = 1), NULL, 'Autenticación y acceso de clientes', 30, 0, 'Redundancia activa del servicio de sesión, degradación a consulta de saldo sin operar y conmutación del proveedor de mensajería para el segundo factor.', 6, '2026-01-01', '2026-07-01'),
    ((SELECT id FROM politica_interna WHERE codigo = 'PLA-CONT-01' AND version = 1), NULL, 'Conciliación de custodia y cierre diario', 480, 15, 'Reimportación del extracto bancario del día y recálculo del cierre; el cierre no se da por bueno hasta que la diferencia vuelve a cero o queda como excepción con dueño.', 12, '2026-01-01', '2027-01-01'),
    ((SELECT id FROM politica_interna WHERE codigo = 'PLA-CONT-01' AND version = 1), NULL, 'Remisión de reportes regulatorios', 1440, 60, 'Regeneración reproducible del reporte desde la réplica y remisión por canal alterno, con constancia de la causa del retraso comunicada al organismo.', 12, '2026-01-01', '2027-01-01');
  END IF;
END $$;

-- Transparencia obligatoria: tarifario, contratos, política de tratamiento de datos, canales de reclamo y horarios. `hash_documento` permite probar después que lo publicado es lo que estaba vigente ese día.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM documento_publicado) THEN
  INSERT INTO documento_publicado (tipo, referencia_tipo, referencia_id, publicado_por, url_publica, hash_documento, vigente_desde, vigente_hasta) VALUES
    ('TARIFARIO', 'tarifario', (SELECT id FROM tarifario WHERE codigo = 'GENERAL' AND version = 1), NULL, 'https://pasanaku.bo/legal/tarifario-v1.pdf', repeat('0', 64), '2026-01-01T00:00:00-04:00', NULL),
    ('CONTRATO', 'contrato_adhesion', (SELECT id FROM contrato_adhesion WHERE codigo = 'CTO-BILLETERA' AND version = 1), NULL, 'https://pasanaku.bo/legal/contrato-billetera-v1.pdf', repeat('0', 64), '2026-01-01T00:00:00-04:00', NULL),
    ('CONTRATO', 'contrato_adhesion', (SELECT id FROM contrato_adhesion WHERE codigo = 'CTO-GRUPO' AND version = 1), NULL, 'https://pasanaku.bo/legal/contrato-grupo-v1.pdf', repeat('0', 64), '2026-01-01T00:00:00-04:00', NULL),
    ('POLITICA_PRIVACIDAD', 'contrato_adhesion', (SELECT id FROM contrato_adhesion WHERE codigo = 'CTO-DATOS' AND version = 1), NULL, 'https://pasanaku.bo/legal/politica-privacidad-v1.pdf', repeat('0', 64), '2026-01-01T00:00:00-04:00', NULL),
    ('CANAL_RECLAMOS', 'punto_reclamo', (SELECT id FROM punto_reclamo WHERE codigo = 'PR-WEB'), NULL, 'https://pasanaku.bo/reclamos', repeat('0', 64), '2026-01-01T00:00:00-04:00', NULL),
    ('HORARIOS', 'punto_reclamo', (SELECT id FROM punto_reclamo WHERE codigo = 'PR-TEL'), NULL, 'https://pasanaku.bo/atencion/horarios', repeat('0', 64), '2026-01-01T00:00:00-04:00', NULL);
  END IF;
END $$;
