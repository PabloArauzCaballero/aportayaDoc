-- Arranque de la base LOCAL de desarrollo. Corre una sola vez, cuando el volumen
-- esta vacio. No forma parte de sql/: sql/ es el esquema, esto es la maquina.

-- 1) search_path de la base.
--    El DDL califica cada tabla con su esquema, pero el SQL escrito a mano que
--    viene despues —restricciones, semillas, verificaciones, prueba de humo—
--    referencia las tablas por nombre simple. Los 307 nombres son unicos, asi
--    que resuelve sin ambiguedad.
--    Los roles de servicio NO se ven afectados: cada svc_* tiene su propio
--    search_path por ALTER ROLE, y el de rol gana sobre el de base. Es lo que
--    mantiene el invariante 11 en pie.
ALTER DATABASE pasanaku SET search_path TO
    aportes, auditoria, cumplimiento, entregas, erp, garantia, grupos, identidad,
    notificaciones, nucleo_financiero, organizador, publicidad, tarifas,
    transparencia, catalogo, comun, public;

-- 2) La marca de entorno que habilita seeders/dev.
--    La pone el ARRANQUE DE DESARROLLO y nunca un despliegue: en produccion esta
--    linea no existe, y por eso sembrar_dev.sql se niega a entrar.
ALTER DATABASE pasanaku SET app.entorno = 'dev';
