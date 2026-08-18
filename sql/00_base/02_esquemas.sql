-- Esquemas y roles de servicio — un esquema y un rol por servicio.
-- Generado por scripts/generar_ddl.py — no editar a mano.
--
-- ADR-017: se parte el DESPLIEGUE, no el modelo. Las claves foraneas
-- entre esquemas se conservan porque todo vive en el mismo cluster.

-- 1) Esquemas
CREATE SCHEMA IF NOT EXISTS aportes;
CREATE SCHEMA IF NOT EXISTS auditoria;
CREATE SCHEMA IF NOT EXISTS cumplimiento;
CREATE SCHEMA IF NOT EXISTS entregas;
CREATE SCHEMA IF NOT EXISTS erp;
CREATE SCHEMA IF NOT EXISTS garantia;
CREATE SCHEMA IF NOT EXISTS grupos;
CREATE SCHEMA IF NOT EXISTS identidad;
CREATE SCHEMA IF NOT EXISTS notificaciones;
CREATE SCHEMA IF NOT EXISTS nucleo_financiero;
CREATE SCHEMA IF NOT EXISTS organizador;
CREATE SCHEMA IF NOT EXISTS publicidad;
CREATE SCHEMA IF NOT EXISTS tarifas;
CREATE SCHEMA IF NOT EXISTS transparencia;
CREATE SCHEMA IF NOT EXISTS catalogo;
CREATE SCHEMA IF NOT EXISTS comun;

-- 2) Un rol por servicio, sin login por defecto (lo da el despliegue)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_aportes') THEN
    CREATE ROLE svc_aportes NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_auditoria') THEN
    CREATE ROLE svc_auditoria NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_cumplimiento') THEN
    CREATE ROLE svc_cumplimiento NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_entregas') THEN
    CREATE ROLE svc_entregas NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_erp') THEN
    CREATE ROLE svc_erp NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_garantia') THEN
    CREATE ROLE svc_garantia NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_grupos') THEN
    CREATE ROLE svc_grupos NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_identidad') THEN
    CREATE ROLE svc_identidad NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_notificaciones') THEN
    CREATE ROLE svc_notificaciones NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_nucleo_financiero') THEN
    CREATE ROLE svc_nucleo_financiero NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_organizador') THEN
    CREATE ROLE svc_organizador NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_publicidad') THEN
    CREATE ROLE svc_publicidad NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_tarifas') THEN
    CREATE ROLE svc_tarifas NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_transparencia') THEN
    CREATE ROLE svc_transparencia NOLOGIN;
  END IF;
END $$;

-- 3) Cada rol ve SU esquema y el catalogo. Nada mas.
--    Un SELECT cruzado entre servicios devuelve permiso denegado,
--    y hay una prueba por par que lo comprueba (barrido 15).
GRANT USAGE ON SCHEMA aportes TO svc_aportes;
ALTER DEFAULT PRIVILEGES IN SCHEMA aportes
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_aportes;
GRANT USAGE ON SCHEMA catalogo TO svc_aportes;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_aportes;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_aportes;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_aportes;

GRANT USAGE ON SCHEMA auditoria TO svc_auditoria;
ALTER DEFAULT PRIVILEGES IN SCHEMA auditoria
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_auditoria;
GRANT USAGE ON SCHEMA catalogo TO svc_auditoria;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_auditoria;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_auditoria;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_auditoria;

GRANT USAGE ON SCHEMA cumplimiento TO svc_cumplimiento;
ALTER DEFAULT PRIVILEGES IN SCHEMA cumplimiento
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_cumplimiento;
GRANT USAGE ON SCHEMA catalogo TO svc_cumplimiento;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_cumplimiento;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_cumplimiento;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_cumplimiento;

GRANT USAGE ON SCHEMA entregas TO svc_entregas;
ALTER DEFAULT PRIVILEGES IN SCHEMA entregas
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_entregas;
GRANT USAGE ON SCHEMA catalogo TO svc_entregas;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_entregas;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_entregas;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_entregas;

GRANT USAGE ON SCHEMA erp TO svc_erp;
ALTER DEFAULT PRIVILEGES IN SCHEMA erp
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_erp;
GRANT USAGE ON SCHEMA catalogo TO svc_erp;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_erp;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_erp;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_erp;

GRANT USAGE ON SCHEMA garantia TO svc_garantia;
ALTER DEFAULT PRIVILEGES IN SCHEMA garantia
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_garantia;
GRANT USAGE ON SCHEMA catalogo TO svc_garantia;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_garantia;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_garantia;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_garantia;

GRANT USAGE ON SCHEMA grupos TO svc_grupos;
ALTER DEFAULT PRIVILEGES IN SCHEMA grupos
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_grupos;
GRANT USAGE ON SCHEMA catalogo TO svc_grupos;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_grupos;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_grupos;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_grupos;

GRANT USAGE ON SCHEMA identidad TO svc_identidad;
ALTER DEFAULT PRIVILEGES IN SCHEMA identidad
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_identidad;
GRANT USAGE ON SCHEMA catalogo TO svc_identidad;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_identidad;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_identidad;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_identidad;

GRANT USAGE ON SCHEMA notificaciones TO svc_notificaciones;
ALTER DEFAULT PRIVILEGES IN SCHEMA notificaciones
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_notificaciones;
GRANT USAGE ON SCHEMA catalogo TO svc_notificaciones;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_notificaciones;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_notificaciones;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_notificaciones;

GRANT USAGE ON SCHEMA nucleo_financiero TO svc_nucleo_financiero;
ALTER DEFAULT PRIVILEGES IN SCHEMA nucleo_financiero
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_nucleo_financiero;
GRANT USAGE ON SCHEMA catalogo TO svc_nucleo_financiero;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_nucleo_financiero;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_nucleo_financiero;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_nucleo_financiero;

GRANT USAGE ON SCHEMA organizador TO svc_organizador;
ALTER DEFAULT PRIVILEGES IN SCHEMA organizador
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_organizador;
GRANT USAGE ON SCHEMA catalogo TO svc_organizador;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_organizador;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_organizador;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_organizador;

GRANT USAGE ON SCHEMA publicidad TO svc_publicidad;
ALTER DEFAULT PRIVILEGES IN SCHEMA publicidad
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_publicidad;
GRANT USAGE ON SCHEMA catalogo TO svc_publicidad;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_publicidad;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_publicidad;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_publicidad;

GRANT USAGE ON SCHEMA tarifas TO svc_tarifas;
ALTER DEFAULT PRIVILEGES IN SCHEMA tarifas
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_tarifas;
GRANT USAGE ON SCHEMA catalogo TO svc_tarifas;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_tarifas;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_tarifas;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_tarifas;

GRANT USAGE ON SCHEMA transparencia TO svc_transparencia;
ALTER DEFAULT PRIVILEGES IN SCHEMA transparencia
  GRANT SELECT, INSERT, UPDATE ON TABLES TO svc_transparencia;
GRANT USAGE ON SCHEMA catalogo TO svc_transparencia;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT SELECT ON TABLES TO svc_transparencia;
-- outbox y bitacoras: INSERTA, y nada mas. No lee el rastro ajeno.
GRANT USAGE ON SCHEMA comun TO svc_transparencia;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun
  GRANT INSERT ON TABLES TO svc_transparencia;

-- 4) search_path por rol: cada servicio ve SU esquema y el catalogo.
--    Refuerza el GRANT: una consulta a una tabla ajena no solo es
--    denegada, es que el nombre ni siquiera resuelve.
ALTER ROLE svc_aportes SET search_path TO aportes, catalogo, comun;
ALTER ROLE svc_auditoria SET search_path TO auditoria, catalogo, comun;
ALTER ROLE svc_cumplimiento SET search_path TO cumplimiento, catalogo, comun;
ALTER ROLE svc_entregas SET search_path TO entregas, catalogo, comun;
ALTER ROLE svc_erp SET search_path TO erp, catalogo, comun;
ALTER ROLE svc_garantia SET search_path TO garantia, catalogo, comun;
ALTER ROLE svc_grupos SET search_path TO grupos, catalogo, comun;
ALTER ROLE svc_identidad SET search_path TO identidad, catalogo, comun;
ALTER ROLE svc_notificaciones SET search_path TO notificaciones, catalogo, comun;
ALTER ROLE svc_nucleo_financiero SET search_path TO nucleo_financiero, catalogo, comun;
ALTER ROLE svc_organizador SET search_path TO organizador, catalogo, comun;
ALTER ROLE svc_publicidad SET search_path TO publicidad, catalogo, comun;
ALTER ROLE svc_tarifas SET search_path TO tarifas, catalogo, comun;
ALTER ROLE svc_transparencia SET search_path TO transparencia, catalogo, comun;

--    La migracion y la auditoria ven todo: aplican el esquema y
--    reportan sobre el sistema entero.
ALTER ROLE rol_migracion SET search_path TO aportes, auditoria, cumplimiento, entregas, erp, garantia, grupos, identidad, notificaciones, nucleo_financiero, organizador, publicidad, tarifas, transparencia, catalogo, comun, public;
ALTER ROLE rol_auditor   SET search_path TO aportes, auditoria, cumplimiento, entregas, erp, garantia, grupos, identidad, notificaciones, nucleo_financiero, organizador, publicidad, tarifas, transparencia, catalogo, comun, public;

-- 5) Migracion y auditoria
--    rol_migracion crea; rol_auditor lee todo pero NO escribe nada.
GRANT ALL ON SCHEMA aportes TO rol_migracion;
GRANT ALL ON SCHEMA auditoria TO rol_migracion;
GRANT ALL ON SCHEMA cumplimiento TO rol_migracion;
GRANT ALL ON SCHEMA entregas TO rol_migracion;
GRANT ALL ON SCHEMA erp TO rol_migracion;
GRANT ALL ON SCHEMA garantia TO rol_migracion;
GRANT ALL ON SCHEMA grupos TO rol_migracion;
GRANT ALL ON SCHEMA identidad TO rol_migracion;
GRANT ALL ON SCHEMA notificaciones TO rol_migracion;
GRANT ALL ON SCHEMA nucleo_financiero TO rol_migracion;
GRANT ALL ON SCHEMA organizador TO rol_migracion;
GRANT ALL ON SCHEMA publicidad TO rol_migracion;
GRANT ALL ON SCHEMA tarifas TO rol_migracion;
GRANT ALL ON SCHEMA transparencia TO rol_migracion;
GRANT ALL ON SCHEMA catalogo TO rol_migracion;
GRANT ALL ON SCHEMA comun TO rol_migracion;
GRANT USAGE ON SCHEMA aportes TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA aportes GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA auditoria TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA auditoria GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA cumplimiento TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA cumplimiento GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA entregas TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA entregas GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA erp TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA erp GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA garantia TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA garantia GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA grupos TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA grupos GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA identidad TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA identidad GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA notificaciones TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA notificaciones GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA nucleo_financiero TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA nucleo_financiero GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA organizador TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA organizador GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA publicidad TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA publicidad GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA tarifas TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA tarifas GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA transparencia TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA transparencia GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA catalogo TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo GRANT SELECT ON TABLES TO rol_auditor;
GRANT USAGE ON SCHEMA comun TO rol_auditor;
ALTER DEFAULT PRIVILEGES IN SCHEMA comun GRANT SELECT ON TABLES TO rol_auditor;

-- 6) El catalogo solo lo escribe la migracion, al sembrar.
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
  GRANT INSERT, UPDATE ON TABLES TO rol_migracion;
