-- Permisos sobre las tablas ya creadas (invariante 11).
-- Generado por scripts/generar_ddl.py — no editar a mano.
--
-- Se aplica DESPUES de crear las tablas: ALTER DEFAULT PRIVILEGES solo
-- cubre lo que se cree a partir de entonces.

GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA aportes TO svc_aportes;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_aportes;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_aportes;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA auditoria TO svc_auditoria;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_auditoria;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_auditoria;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA cumplimiento TO svc_cumplimiento;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_cumplimiento;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_cumplimiento;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA entregas TO svc_entregas;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_entregas;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_entregas;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA erp TO svc_erp;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_erp;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_erp;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA garantia TO svc_garantia;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_garantia;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_garantia;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA grupos TO svc_grupos;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_grupos;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_grupos;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA identidad TO svc_identidad;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_identidad;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_identidad;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA notificaciones TO svc_notificaciones;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_notificaciones;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_notificaciones;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA nucleo_financiero TO svc_nucleo_financiero;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_nucleo_financiero;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_nucleo_financiero;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA organizador TO svc_organizador;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_organizador;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_organizador;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA publicidad TO svc_publicidad;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_publicidad;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_publicidad;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA tarifas TO svc_tarifas;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_tarifas;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_tarifas;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA transparencia TO svc_transparencia;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_transparencia;
GRANT INSERT ON ALL TABLES IN SCHEMA comun TO svc_transparencia;

GRANT SELECT ON ALL TABLES IN SCHEMA aportes TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA auditoria TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA cumplimiento TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA entregas TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA erp TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA garantia TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA grupos TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA identidad TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA notificaciones TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA nucleo_financiero TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA organizador TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA publicidad TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA tarifas TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA transparencia TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO rol_auditor;
GRANT SELECT ON ALL TABLES IN SCHEMA comun TO rol_auditor;

-- Append-only: ni el rol dueño puede editar. La base rechaza; el
-- analisis estatico solo adelanta el fallo (invariante 5).
REVOKE UPDATE, DELETE ON garantia.abono_recuperacion FROM svc_garantia;
REVOKE UPDATE, DELETE ON cumplimiento.acta_comite FROM svc_cumplimiento;
REVOKE UPDATE, DELETE ON nucleo_financiero.asiento_contable FROM svc_nucleo_financiero;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_aportes;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_auditoria;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_cumplimiento;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_entregas;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_erp;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_garantia;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_grupos;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_identidad;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_notificaciones;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_nucleo_financiero;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_organizador;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_publicidad;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_tarifas;
REVOKE UPDATE, DELETE ON comun.bitacora_evento FROM svc_transparencia;
REVOKE UPDATE, DELETE ON erp.cierre_periodo_contable FROM svc_erp;
REVOKE UPDATE, DELETE ON publicidad.clic_anuncio FROM svc_publicidad;
REVOKE UPDATE, DELETE ON erp.cobro_cuenta_por_cobrar FROM svc_erp;
REVOKE UPDATE, DELETE ON publicidad.conversion_anuncio FROM svc_publicidad;
REVOKE UPDATE, DELETE ON erp.cuenta_por_cobrar FROM svc_erp;
REVOKE UPDATE, DELETE ON erp.depreciacion_activo FROM svc_erp;
REVOKE UPDATE, DELETE ON tarifas.devengo_comision FROM svc_tarifas;
REVOKE UPDATE, DELETE ON erp.estado_financiero_generado FROM svc_erp;
REVOKE UPDATE, DELETE ON transparencia.evento_reputacion FROM svc_transparencia;
REVOKE UPDATE, DELETE ON cumplimiento.evento_riesgo_operativo FROM svc_cumplimiento;
REVOKE UPDATE, DELETE ON erp.factura_proveedor FROM svc_erp;
REVOKE UPDATE, DELETE ON publicidad.factura_publicidad FROM svc_publicidad;
REVOKE UPDATE, DELETE ON garantia.historial_estado_incumplimiento FROM svc_garantia;
REVOKE UPDATE, DELETE ON publicidad.impresion_anuncio FROM svc_publicidad;
REVOKE UPDATE, DELETE ON nucleo_financiero.movimiento_billetera FROM svc_nucleo_financiero;
REVOKE UPDATE, DELETE ON nucleo_financiero.movimiento_contable FROM svc_nucleo_financiero;
REVOKE UPDATE, DELETE ON nucleo_financiero.movimiento_custodia FROM svc_nucleo_financiero;
REVOKE UPDATE, DELETE ON garantia.movimiento_fondo FROM svc_garantia;
REVOKE UPDATE, DELETE ON erp.pago_a_proveedor FROM svc_erp;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_aportes;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_auditoria;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_cumplimiento;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_entregas;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_erp;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_garantia;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_grupos;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_identidad;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_notificaciones;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_nucleo_financiero;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_organizador;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_publicidad;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_tarifas;
REVOKE UPDATE, DELETE ON comun.registro_acceso_datos FROM svc_transparencia;
REVOKE UPDATE, DELETE ON garantia.registro_incumplimiento FROM svc_garantia;
REVOKE UPDATE, DELETE ON cumplimiento.registro_operacion_relevante FROM svc_cumplimiento;
REVOKE UPDATE, DELETE ON transparencia.registro_sellado FROM svc_transparencia;
REVOKE UPDATE, DELETE ON nucleo_financiero.saldo_diario_billetera FROM svc_nucleo_financiero;
REVOKE UPDATE, DELETE ON nucleo_financiero.transaccion_billetera FROM svc_nucleo_financiero;

-- ADR-029 · catalogo: lo lee todo el mundo (SELECT ya otorgado), pero
--   la escritura en caliente la tiene solo el servicio dueño del ciclo
--   administrativo. El resto de svc_* no puede cambiar un parametro.
GRANT INSERT, UPDATE ON catalogo.impuesto TO svc_tarifas;
GRANT INSERT, UPDATE ON catalogo.licencia_regulatoria TO svc_cumplimiento;
GRANT INSERT, UPDATE ON catalogo.limite_operativo_billetera TO svc_cumplimiento;
GRANT INSERT, UPDATE ON catalogo.tarifario TO svc_tarifas;
GRANT INSERT, UPDATE ON catalogo.umbral_operativo TO svc_cumplimiento;
GRANT INSERT, UPDATE ON catalogo.umbral_reporte_uif TO svc_cumplimiento;

-- ADR-027 · outbox por esquema: el svc_* dueño publica su propio
--   outbox, pero el payload es inmutable. UPDATE SOLO sobre las
--   columnas de estado; evento_consumido no se edita nunca.
REVOKE UPDATE ON aportes.evento_dominio FROM svc_aportes;
GRANT UPDATE (publicado_en, estado, intentos) ON aportes.evento_dominio TO svc_aportes;
REVOKE UPDATE ON aportes.evento_consumido FROM svc_aportes;
REVOKE UPDATE ON auditoria.evento_dominio FROM svc_auditoria;
GRANT UPDATE (publicado_en, estado, intentos) ON auditoria.evento_dominio TO svc_auditoria;
REVOKE UPDATE ON auditoria.evento_consumido FROM svc_auditoria;
REVOKE UPDATE ON cumplimiento.evento_dominio FROM svc_cumplimiento;
GRANT UPDATE (publicado_en, estado, intentos) ON cumplimiento.evento_dominio TO svc_cumplimiento;
REVOKE UPDATE ON cumplimiento.evento_consumido FROM svc_cumplimiento;
REVOKE UPDATE ON entregas.evento_dominio FROM svc_entregas;
GRANT UPDATE (publicado_en, estado, intentos) ON entregas.evento_dominio TO svc_entregas;
REVOKE UPDATE ON entregas.evento_consumido FROM svc_entregas;
REVOKE UPDATE ON erp.evento_dominio FROM svc_erp;
GRANT UPDATE (publicado_en, estado, intentos) ON erp.evento_dominio TO svc_erp;
REVOKE UPDATE ON erp.evento_consumido FROM svc_erp;
REVOKE UPDATE ON garantia.evento_dominio FROM svc_garantia;
GRANT UPDATE (publicado_en, estado, intentos) ON garantia.evento_dominio TO svc_garantia;
REVOKE UPDATE ON garantia.evento_consumido FROM svc_garantia;
REVOKE UPDATE ON grupos.evento_dominio FROM svc_grupos;
GRANT UPDATE (publicado_en, estado, intentos) ON grupos.evento_dominio TO svc_grupos;
REVOKE UPDATE ON grupos.evento_consumido FROM svc_grupos;
REVOKE UPDATE ON identidad.evento_dominio FROM svc_identidad;
GRANT UPDATE (publicado_en, estado, intentos) ON identidad.evento_dominio TO svc_identidad;
REVOKE UPDATE ON identidad.evento_consumido FROM svc_identidad;
REVOKE UPDATE ON notificaciones.evento_dominio FROM svc_notificaciones;
GRANT UPDATE (publicado_en, estado, intentos) ON notificaciones.evento_dominio TO svc_notificaciones;
REVOKE UPDATE ON notificaciones.evento_consumido FROM svc_notificaciones;
REVOKE UPDATE ON nucleo_financiero.evento_dominio FROM svc_nucleo_financiero;
GRANT UPDATE (publicado_en, estado, intentos) ON nucleo_financiero.evento_dominio TO svc_nucleo_financiero;
REVOKE UPDATE ON nucleo_financiero.evento_consumido FROM svc_nucleo_financiero;
REVOKE UPDATE ON organizador.evento_dominio FROM svc_organizador;
GRANT UPDATE (publicado_en, estado, intentos) ON organizador.evento_dominio TO svc_organizador;
REVOKE UPDATE ON organizador.evento_consumido FROM svc_organizador;
REVOKE UPDATE ON publicidad.evento_dominio FROM svc_publicidad;
GRANT UPDATE (publicado_en, estado, intentos) ON publicidad.evento_dominio TO svc_publicidad;
REVOKE UPDATE ON publicidad.evento_consumido FROM svc_publicidad;
REVOKE UPDATE ON tarifas.evento_dominio FROM svc_tarifas;
GRANT UPDATE (publicado_en, estado, intentos) ON tarifas.evento_dominio TO svc_tarifas;
REVOKE UPDATE ON tarifas.evento_consumido FROM svc_tarifas;
REVOKE UPDATE ON transparencia.evento_dominio FROM svc_transparencia;
GRANT UPDATE (publicado_en, estado, intentos) ON transparencia.evento_dominio TO svc_transparencia;
REVOKE UPDATE ON transparencia.evento_consumido FROM svc_transparencia;

-- rol_auditor no escribe en ningun lado, nunca.
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA aportes FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA auditoria FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA cumplimiento FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA entregas FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA erp FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA garantia FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA grupos FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA identidad FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA notificaciones FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA nucleo_financiero FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA organizador FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA publicidad FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA tarifas FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA transparencia FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA catalogo FROM rol_auditor;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA comun FROM rol_auditor;
