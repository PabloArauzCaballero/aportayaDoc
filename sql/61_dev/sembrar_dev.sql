-- Datos de desarrollo — NO aplicar en producción
--   psql -d pasanaku -v ON_ERROR_STOP=1 -f sql/61_dev/sembrar_dev.sql
-- GENERADO desde seeders/ — no editar a mano.

\set ON_ERROR_STOP on
BEGIN;

-- GUARDA — sin esto, estas semillas no entran a ninguna base.
-- La marca la pone el arranque de desarrollo, nunca un despliegue:
--   ALTER DATABASE pasanaku SET app.entorno = 'dev';
DO $$
BEGIN
  IF current_setting('app.entorno', true) IS DISTINCT FROM 'dev' THEN
    RAISE EXCEPTION
      'SEMILLAS DE DEV BLOQUEADAS: app.entorno = %, se exige ''dev''',
      coalesce(nullif(current_setting('app.entorno', true), ''), '<sin definir>');
  END IF;
END $$;

\ir 01-entorno-tecnico.sql
\ir 02-usuarios-y-billeteras.sql
\ir 03-grupo-demo.sql
\ir 04-fondo-y-cuenta-del-grupo.sql
\ir 05-personal-interno-y-gobierno.sql
\ir 06-instrumentos-y-recargas.sql
\ir 07-aportes-y-entrega.sql
\ir 08-cobros-qr-y-conciliacion.sql
\ir 09-mora-cobertura-y-cobranza.sql
\ir 10-notificaciones-cierre-y-reclamos.sql
\ir 11-contabilidad-y-saldos.sql
\ir 12-retiro-y-controles.sql
\ir 13-cumplimiento-uif.sql
\ir 14-identidad-y-sesiones.sql
\ir 15-usuarios-dev.sql

COMMIT;
