-- =====================================================================
--  Prueba de humo de las restricciones críticas
--    psql -d pasanaku -f sql/50_verificacion/prueba_humo.sql
--
--  Cada línea debe empezar con OK. Una línea FALLA significa que una regla
--  que debería ser imposible de violar no está protegiendo nada.
--
--  Funciona igual con la base recién creada o ya sembrada: usa códigos y
--  monedas propios que no colisionan con los catálogos de seeders/.
--
--  Este archivo está escrito a mano (el resto de sql/ es generado).
-- =====================================================================
\set QUIET on
\set ON_ERROR_STOP off
\pset tuples_only on
\pset format unaligned

CREATE OR REPLACE FUNCTION pg_temp.debe_fallar(p_caso TEXT, p_sql TEXT)
RETURNS TEXT AS $$
BEGIN
  BEGIN
    EXECUTE p_sql;
  EXCEPTION WHEN others THEN
    RETURN 'OK    · ' || p_caso || ' → rechazado';
  END;
  RETURN 'FALLA · ' || p_caso || ' → la base lo permitió';
END $$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION pg_temp.debe_pasar(p_caso TEXT, p_sql TEXT)
RETURNS TEXT AS $$
BEGIN
  EXECUTE p_sql;
  RETURN 'OK    · ' || p_caso;
EXCEPTION WHEN others THEN
  RETURN 'FALLA · ' || p_caso || ' → ' || left(SQLERRM, 90);
END $$ LANGUAGE plpgsql;

-- --- preparación ------------------------------------------------------
SELECT pg_temp.debe_pasar('alta de cuenta de plataforma', $q$
  INSERT INTO cuenta_billetera (id, numero_cuenta, tipo, moneda, estado,
      nivel_debida_diligencia, fecha_apertura)
  VALUES ('11111111-1111-1111-1111-111111111111', 'PLT-0001',
          'PLATAFORMA_INGRESOS', 'BOB', 'ACTIVA', 'ESTANDAR', now())
$q$);

-- La cuenta puente es contrapartida del sistema: por diseño opera en negativo
SELECT pg_temp.debe_pasar('alta de cuenta técnica de custodia', $q$
  INSERT INTO cuenta_billetera (id, numero_cuenta, tipo, moneda, estado,
      nivel_debida_diligencia, fecha_apertura, permite_saldo_negativo)
  VALUES ('22222222-2222-2222-2222-222222222222', 'PUE-0001',
          'PUENTE_CUSTODIA', 'BOB', 'ACTIVA', 'ESTANDAR', now(), TRUE)
$q$);

-- --- R-BIL-05 · titularidad coherente con el tipo ---------------------
SELECT pg_temp.debe_fallar('R-BIL-05 cuenta USUARIO sin titular', $q$
  INSERT INTO cuenta_billetera (numero_cuenta, tipo, moneda, estado,
      nivel_debida_diligencia, fecha_apertura)
  VALUES ('USR-0001', 'USUARIO', 'BOB', 'ACTIVA', 'SIMPLIFICADA', now())
$q$);

-- --- R-BIL-02 · el saldo disponible no puede ser negativo -------------
SELECT pg_temp.debe_fallar('R-BIL-02 saldo negativo', $q$
  UPDATE cuenta_billetera SET saldo_disponible = -1
   WHERE numero_cuenta = 'PLT-0001'
$q$);

-- --- enumerado cerrado ------------------------------------------------
SELECT pg_temp.debe_fallar('CHECK de enumerado en estado', $q$
  UPDATE cuenta_billetera SET estado = 'INVENTADO'
   WHERE numero_cuenta = 'PLT-0001'
$q$);

-- --- R-BIL-03 · saldo_total es derivado, no se escribe ----------------
SELECT pg_temp.debe_fallar('R-BIL-03 escribir el saldo total', $q$
  UPDATE cuenta_billetera SET saldo_total = 999
   WHERE numero_cuenta = 'PLT-0001'
$q$);

-- --- R-BIL-01 · transacción sin contrapartida: se rechaza al COMMIT ---
BEGIN;
INSERT INTO transaccion_billetera (id, tipo, estado, moneda, monto_total,
    origen_tipo, origen_id, canal, clave_idempotencia, hash_registro,
    ocurrida_en, registrada_en)
VALUES ('33333333-3333-3333-3333-333333333333', 'RECARGA', 'APLICADA', 'BOB',
        100, 'ORDEN_RECARGA', gen_random_uuid(), 'APP', 'idem-descuadre',
        '', now(), now());
INSERT INTO movimiento_billetera (transaccion_id, cuenta_billetera_id, orden,
    sentido, monto, saldo_disponible_posterior, saldo_retenido_posterior, glosa)
VALUES ('33333333-3333-3333-3333-333333333333',
        '11111111-1111-1111-1111-111111111111', 1, 'CREDITO', 100, 100, 0,
        'solo el credito, sin debito');
COMMIT;
SELECT CASE WHEN count(*) = 0
            THEN 'OK    · R-BIL-01 transacción descuadrada → rechazada al COMMIT'
            ELSE 'FALLA · R-BIL-01 transacción descuadrada → quedó registrada' END
  FROM transaccion_billetera WHERE clave_idempotencia = 'idem-descuadre';

-- --- transacción cuadrada: debe pasar ---------------------------------
BEGIN;
INSERT INTO transaccion_billetera (id, tipo, estado, moneda, monto_total,
    origen_tipo, origen_id, canal, clave_idempotencia, hash_registro,
    ocurrida_en, registrada_en)
VALUES ('44444444-4444-4444-4444-444444444444', 'RECARGA', 'APLICADA', 'BOB',
        100, 'ORDEN_RECARGA', gen_random_uuid(), 'APP', 'idem-ok', '',
        now(), now());
INSERT INTO movimiento_billetera (transaccion_id, cuenta_billetera_id, orden,
    sentido, monto, saldo_disponible_posterior, saldo_retenido_posterior, glosa)
VALUES ('44444444-4444-4444-4444-444444444444',
        '11111111-1111-1111-1111-111111111111', 1, 'CREDITO', 100, 100, 0, 'credito'),
       ('44444444-4444-4444-4444-444444444444',
        '22222222-2222-2222-2222-222222222222', 2, 'DEBITO', 100, 0, 0, 'debito');
COMMIT;
SELECT CASE WHEN count(*) = 1
            THEN 'OK    · R-BIL-01 transacción cuadrada aceptada'
            ELSE 'FALLA · R-BIL-01 transacción cuadrada rechazada' END
  FROM transaccion_billetera WHERE clave_idempotencia = 'idem-ok';

-- --- R-AUD-03 · la cadena de hash la calcula la base ------------------
SELECT CASE WHEN length(hash_registro) = 64
            THEN 'OK    · R-AUD-03 hash encadenado calculado por la base'
            ELSE 'FALLA · R-AUD-03 hash no calculado' END
  FROM transaccion_billetera WHERE clave_idempotencia = 'idem-ok';

-- --- R-BIL-06 · idempotencia ------------------------------------------
SELECT pg_temp.debe_fallar('R-BIL-06 clave de idempotencia repetida', $q$
  INSERT INTO transaccion_billetera (tipo, estado, moneda, monto_total,
      origen_tipo, origen_id, canal, clave_idempotencia, hash_registro,
      ocurrida_en, registrada_en)
  VALUES ('RECARGA', 'INICIADA', 'BOB', 50, 'ORDEN_RECARGA', gen_random_uuid(),
          'APP', 'idem-ok', '', now(), now())
$q$);

-- --- R-AUD-01 · append-only -------------------------------------------
SELECT pg_temp.debe_fallar('R-AUD-01 UPDATE sobre movimiento_billetera', $q$
  UPDATE movimiento_billetera SET monto = 1
   WHERE transaccion_id = '44444444-4444-4444-4444-444444444444'
$q$);

SELECT pg_temp.debe_fallar('R-AUD-01 DELETE sobre transaccion_billetera', $q$
  DELETE FROM transaccion_billetera WHERE clave_idempotencia = 'idem-ok'
$q$);

-- todas las tablas append-only del modelo tienen que estar selladas
-- El total es fijo a propósito: si baja, se perdió un sello; si sube sin que
-- alguien actualice esta cifra, se agregó una tabla append-only sin repasar la
-- prueba. Sube con APPEND_ONLY en scripts/modelo.py (19 → 30 al incorporar los
-- módulos 13 y 14; 30 → 29 al bajar el outbox a infraestructura por esquema con
-- UPDATE de estado, ADR-027: evento_dominio ya no está sellado; 29 → 30 al sellar
-- indicador_kpi, que pasó a append-only para que un indicador recalculado no pise
-- la serie anterior).
SELECT CASE WHEN count(*) = 30
            THEN 'OK    · R-AUD-01 las 30 tablas append-only están selladas'
            ELSE 'FALLA · R-AUD-01 ' || count(*) || ' de 30 tablas selladas' END
  FROM pg_trigger tg
  JOIN pg_class c ON c.oid = tg.tgrelid
 WHERE NOT tg.tgisinternal AND tg.tgname LIKE '%append\_only'
   -- Las particiones heredan el trigger de su tabla padre: contarlas infla el
   -- total (bitacora_evento tiene 75 particiones) y haría fallar la prueba sin
   -- que falte ningún sello. Se cuentan solo las tablas raíz.
   AND NOT c.relispartition;

-- --- R-BIL-08 · toda retención expira salvo orden de autoridad --------
SELECT pg_temp.debe_fallar('R-BIL-08 retención sin vencimiento', $q$
  INSERT INTO retencion_saldo (cuenta_billetera_id, motivo, monto, estado, creada_en)
  VALUES ('11111111-1111-1111-1111-111111111111', 'APORTE_PROGRAMADO', 10,
          'VIGENTE', now())
$q$);

SELECT pg_temp.debe_pasar('R-BIL-08 retención de autoridad sin vencimiento', $q$
  INSERT INTO retencion_saldo (cuenta_billetera_id, motivo, monto, estado, creada_en)
  VALUES ('11111111-1111-1111-1111-111111111111', 'ORDEN_AUTORIDAD', 10,
          'VIGENTE', now())
$q$);

-- --- R-BIL-07 y R-BIL-16 · los saldos se derivan del libro ------------
SELECT CASE WHEN saldo_retenido = 10 AND saldo_disponible = 90 AND saldo_total = 100
            THEN 'OK    · R-BIL-07/16 saldos derivados por trigger (100 = 90 + 10)'
            ELSE 'FALLA · R-BIL-07/16 disponible=' || saldo_disponible
                 || ' retenido=' || saldo_retenido || ' total=' || saldo_total END
  FROM cuenta_billetera WHERE numero_cuenta = 'PLT-0001';

SELECT CASE WHEN saldo_disponible = -100
            THEN 'OK    · R-BIL-16 la cuenta puente refleja el débito del libro'
            ELSE 'FALLA · R-BIL-16 cuenta puente con saldo ' || saldo_disponible END
  FROM cuenta_billetera WHERE numero_cuenta = 'PUE-0001';

SELECT CASE WHEN count(*) = 0
            THEN 'OK    · R-BIL-16 ninguna caché de saldo difiere del libro'
            ELSE 'FALLA · R-BIL-16 ' || count(*) || ' cuenta(s) con saldo divergente' END
  FROM (SELECT c.id FROM cuenta_billetera c
          LEFT JOIN movimiento_billetera m ON m.cuenta_billetera_id = c.id
         GROUP BY c.id, c.saldo_disponible, c.saldo_retenido
        HAVING c.saldo_disponible + c.saldo_retenido
             <> COALESCE(SUM(CASE WHEN m.sentido='CREDITO' THEN m.monto
                                  ELSE -m.monto END), 0)) x;

-- --- R-UIF-01 · umbrales como dato, con cita normativa y sin solape ---
-- Se usa PCC-01 + ELECTRONICA: combinación que los seeders no ocupan, para
-- que la prueba corra igual sobre una base ya sembrada.
SELECT pg_temp.debe_fallar('R-UIF-01 umbral sin cita normativa', $q$
  INSERT INTO umbral_reporte_uif (formulario, inciso, concepto_operacion,
      es_acumulado, umbral_usd, ventana_dias_calendario,
      exige_declaracion_origen_destino, reinicia_tras_superar, base_normativa,
      vigente_desde, activo)
  VALUES ('PCC-01', 'z', 'ELECTRONICA', TRUE, 1000, 3, TRUE, TRUE, '   ',
          current_date, TRUE)
$q$);

SELECT pg_temp.debe_pasar('R-UIF-01 umbral con cita normativa', $q$
  INSERT INTO umbral_reporte_uif (formulario, inciso, concepto_operacion,
      es_acumulado, umbral_usd, ventana_dias_calendario,
      exige_declaracion_origen_destino, reinicia_tras_superar, base_normativa,
      vigente_desde, activo)
  VALUES ('PCC-01', 'z', 'ELECTRONICA', TRUE, 1000, 3, TRUE, TRUE,
          'Prueba de humo — inciso ficticio', current_date, TRUE)
$q$);

SELECT pg_temp.debe_fallar('R-UIF-01 vigencias solapadas del mismo umbral', $q$
  INSERT INTO umbral_reporte_uif (formulario, inciso, concepto_operacion,
      es_acumulado, umbral_usd, ventana_dias_calendario,
      exige_declaracion_origen_destino, reinicia_tras_superar, base_normativa,
      vigente_desde, activo)
  VALUES ('PCC-01', 'z', 'ELECTRONICA', TRUE, 500, 3, TRUE, TRUE,
          'duplicado que se solapa', current_date, TRUE)
$q$);

SELECT pg_temp.debe_fallar('R-UIF-01 umbral acumulado sin ventana', $q$
  INSERT INTO umbral_reporte_uif (formulario, inciso, concepto_operacion,
      es_acumulado, umbral_usd, ventana_dias_calendario,
      exige_declaracion_origen_destino, reinicia_tras_superar, base_normativa,
      vigente_desde, activo)
  VALUES ('ROG-03', 'z', 'ACTIVO_VIRTUAL', TRUE, 1000, NULL, FALSE,
          TRUE, 'Prueba de humo — acumulado sin ventana', current_date, TRUE)
$q$);

-- --- R-UIF-04 · sin tipo de cambio no hay conversión reproducible -----
-- XTS es el código ISO reservado para pruebas: nunca lo siembra un catálogo.
SELECT pg_temp.debe_fallar('R-UIF-04 conversión sin tipo de cambio cargado', $q$
  SELECT (fn_fx_a_usd(100, 'XTS', current_date)).monto_usd
$q$);

SELECT pg_temp.debe_pasar('R-UIF-04 conversión con tipo de cambio cargado', $q$
  INSERT INTO tipo_cambio (moneda_origen, moneda_destino, fecha, tipo_cambio,
      fuente, cargado_en)
  VALUES ('XTS', 'USD', current_date, 0.500000, 'MANUAL', now());
  SELECT (fn_fx_a_usd(100, 'XTS', current_date)).monto_usd;
$q$);

-- --- R-UIF-06 · el reporte en cero tiene que ser coherente ------------
SELECT pg_temp.debe_fallar('R-UIF-06 reporte en cero con registros', $q$
  INSERT INTO catalogo_reporte_regulatorio (id, codigo, organismo, nombre,
      periodicidad, formato, plazo_dias, base_normativa, obligatorio, activo)
  VALUES ('55555555-5555-5555-5555-555555555555', 'PCC-01', 'UIF',
          'Formularios PCC-01 del mes', 'MENSUAL', 'CSV', 15,
          'Instructivo EIF art. 52', TRUE, TRUE);
  INSERT INTO reporte_regulatorio (catalogo_reporte_id, periodo, fecha_corte,
      estado, cantidad_registros, reporte_en_cero, monto_total, fecha_limite)
  VALUES ('55555555-5555-5555-5555-555555555555', '2026-07', current_date,
          'GENERADO', 12, TRUE, 0, current_date)
$q$);

-- --- R-UIF-07 · una alerta no se cierra sin conclusión ----------------
SELECT pg_temp.debe_fallar('R-UIF-07 alerta descartada sin conclusión', $q$
  INSERT INTO alerta_monitoreo_lft (regla_monitoreo_id, usuario_id,
      monto_involucrado, detalle, severidad, estado, detectada_en)
  VALUES (NULL, NULL, 0, '{}'::jsonb, 'ALTA', 'DESCARTADA', now())
$q$);

-- --- R-LIM-01 · denegar por omisión y respeto del techo ---------------
SELECT pg_temp.debe_fallar('R-LIM-01 concepto sin límite configurado', $q$
  SELECT fn_lim_evaluar('11111111-1111-1111-1111-111111111111',
                        'CONCEPTO_SIN_LIMITE', 100)
$q$);

-- Con los catálogos sembrados, el techo tiene que hacerse cumplir; sin ellos,
-- la misma llamada se rechaza por omisión. Las dos respuestas son correctas.
SELECT CASE WHEN EXISTS (SELECT 1 FROM limite_operativo_billetera
                          WHERE concepto = 'RETIRO' AND activo)
            THEN pg_temp.debe_fallar('R-LIM-01 retiro que supera el techo del nivel', $q$
                   SELECT fn_lim_evaluar('11111111-1111-1111-1111-111111111111',
                                         'RETIRO', 9999999)
                 $q$)
            ELSE 'OK    · R-LIM-01 sin catálogo de límites: se deniega por omisión'
       END;

-- --- R-LIC-01 · servicio no autorizado --------------------------------
SELECT CASE WHEN fn_lic_servicio_habilitado('BILLETERA') = FALSE
            THEN 'OK    · R-LIC-01 sin licencia cargada, el servicio no se habilita'
            ELSE 'FALLA · R-LIC-01 habilitó un servicio sin licencia' END;

-- =====================================================================
--  M13 · Contabilidad financiera y ERP (R-CTB)
-- =====================================================================

-- Actores propios de la prueba: los seeders mínimos no siembran usuarios, y
-- una prueba que dependa de que exista alguno se vuelve vacía sin avisar
-- (INSERT ... SELECT de cero filas no falla). Dos usuarios distintos hacen
-- falta para probar la segregación de funciones de R-CTB-05.
SELECT pg_temp.debe_pasar('alta de usuarios de prueba', $q$
  INSERT INTO usuario (id, codigo_publico, nombres, apellidos, telefono_e164,
      fecha_nacimiento, estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
  VALUES ('cc000000-0000-0000-0000-00000000000a', 'ZZAPROB01', 'Aprobadora',
          'Prueba', '+59170000001', '1990-01-01', 'ACTIVO', 'COMPLETO',
          'es', 'America/La_Paz', now()),
         ('cc000000-0000-0000-0000-00000000000b', 'ZZPAGAD01', 'Pagador',
          'Prueba', '+59170000002', '1990-01-01', 'ACTIVO', 'COMPLETO',
          'es', 'America/La_Paz', now())
$q$);

-- La categoría necesita cuentas contables reales. Se crean acá y no se toman
-- del plan sembrado: aplicar.sql no carga seeders, así que depender de ellos
-- volvería la prueba vacía (INSERT ... SELECT de cero filas no falla) y
-- R-CTB-07 pasaría por violación de FK en vez de por el CHECK que se prueba.
SELECT pg_temp.debe_pasar('alta de cuenta contable de movimiento', $q$
  INSERT INTO cuenta_contable (id, codigo, nombre, tipo, naturaleza,
      nivel, es_cuenta_de_movimiento, saldo)
  VALUES ('cc000000-0000-0000-0000-00000000000d', 'ZZ-MOV', 'Movimiento prueba',
          'ACTIVO', 'DEUDORA', 2, TRUE, 0)
$q$);

SELECT pg_temp.debe_pasar('alta de categoría de activo fijo de prueba', $q$
  INSERT INTO categoria_activo_fijo (id, codigo, nombre, vida_util_meses,
      metodo_depreciacion, cuenta_activo_id, cuenta_depreciacion_id,
      cuenta_gasto_depreciacion_id)
  VALUES ('cc000000-0000-0000-0000-00000000000c', 'ZZ-CAT', 'Categoria prueba',
          12, 'LINEA_RECTA', 'cc000000-0000-0000-0000-00000000000d',
          'cc000000-0000-0000-0000-00000000000d',
          'cc000000-0000-0000-0000-00000000000d')
$q$);

-- Datos propios: ejercicio de un año que ningún seeder usa, y dos cuentas
-- contables con códigos fuera del plan sembrado.
SELECT pg_temp.debe_pasar('alta de ejercicio fiscal de prueba', $q$
  INSERT INTO ejercicio_fiscal (id, anio, fecha_inicio, fecha_fin, estado)
  VALUES ('aa000000-0000-0000-0000-000000000001', 2999,
          '2999-01-01', '2999-12-31', 'ABIERTO')
$q$);

SELECT pg_temp.debe_pasar('alta de período contable abierto', $q$
  INSERT INTO periodo_contable (id, ejercicio_fiscal_id, mes,
      fecha_inicio, fecha_fin, estado)
  VALUES ('aa000000-0000-0000-0000-000000000010',
          'aa000000-0000-0000-0000-000000000001', 1,
          '2999-01-01', '2999-01-31', 'ABIERTO')
$q$);

SELECT pg_temp.debe_pasar('alta de período contable cerrado', $q$
  INSERT INTO periodo_contable (id, ejercicio_fiscal_id, mes,
      fecha_inicio, fecha_fin, estado)
  VALUES ('aa000000-0000-0000-0000-000000000011',
          'aa000000-0000-0000-0000-000000000001', 2,
          '2999-02-01', '2999-02-28', 'CERRADO')
$q$);

-- R-CTB-01 · un solo período por ejercicio y mes
SELECT pg_temp.debe_fallar('R-CTB-01 período repetido en el mismo mes', $q$
  INSERT INTO periodo_contable (ejercicio_fiscal_id, mes,
      fecha_inicio, fecha_fin, estado)
  VALUES ('aa000000-0000-0000-0000-000000000001', 1,
          '2999-01-01', '2999-01-31', 'ABIERTO')
$q$);

-- R-CTB-01 · nada se asienta en un período cerrado
SELECT pg_temp.debe_fallar('R-CTB-01 asiento contra período cerrado', $q$
  INSERT INTO asiento_contable (fecha, glosa, origen_tipo, origen_id,
      periodo_contable_id, estado)
  VALUES (now(), 'asiento en periodo cerrado', 'AJUSTE', gen_random_uuid(),
          'aa000000-0000-0000-0000-000000000011', 'BORRADOR')
$q$);

SELECT pg_temp.debe_pasar('R-CTB-01 asiento contra período abierto aceptado', $q$
  INSERT INTO asiento_contable (id, fecha, glosa, origen_tipo, origen_id,
      periodo_contable_id, estado)
  VALUES ('aa000000-0000-0000-0000-000000000020', now(), 'asiento valido',
          'AJUSTE', gen_random_uuid(),
          'aa000000-0000-0000-0000-000000000010', 'BORRADOR')
$q$);

-- R-CTB-02 · una cuenta sumarizadora no recibe movimientos
SELECT pg_temp.debe_pasar('alta de cuenta contable sumarizadora', $q$
  INSERT INTO cuenta_contable (id, codigo, nombre, tipo, naturaleza,
      nivel, es_cuenta_de_movimiento, saldo)
  VALUES ('aa000000-0000-0000-0000-000000000030', 'ZZ-SUM', 'Sumarizadora prueba',
          'ACTIVO', 'DEUDORA', 1, FALSE, 0)
$q$);

SELECT pg_temp.debe_fallar('R-CTB-02 movimiento sobre cuenta sumarizadora', $q$
  INSERT INTO movimiento_contable (asiento_id, cuenta_id, debe, haber, descripcion)
  VALUES ('aa000000-0000-0000-0000-000000000020',
          'aa000000-0000-0000-0000-000000000030', 100, 0, 'no deberia entrar')
$q$);

SELECT pg_temp.debe_pasar('R-CTB-02 movimiento sobre cuenta de movimiento aceptado', $q$
  INSERT INTO movimiento_contable (asiento_id, cuenta_id, debe, haber, descripcion)
  VALUES ('aa000000-0000-0000-0000-000000000020',
          'cc000000-0000-0000-0000-00000000000d', 100, 0, 'movimiento valido')
$q$);

-- R-CTB-02 · una cuenta no es su propio padre
SELECT pg_temp.debe_fallar('R-CTB-02 cuenta contable padre de sí misma', $q$
  UPDATE cuenta_contable
     SET cuenta_padre_id = 'aa000000-0000-0000-0000-000000000030'
   WHERE id = 'aa000000-0000-0000-0000-000000000030'
$q$);

-- R-CTB-04 · el saldo pagado nunca supera el monto de la factura
SELECT pg_temp.debe_pasar('alta de tercero comercial de prueba', $q$
  INSERT INTO tercero_comercial (id, tipo, razon_social, numero_documento,
      estado, creado_en)
  VALUES ('aa000000-0000-0000-0000-000000000040', 'PROVEEDOR',
          'Proveedor de prueba', 'ZZ-999999', 'ACTIVO', now())
$q$);

SELECT pg_temp.debe_fallar('R-CTB-04 factura con pagado mayor al monto', $q$
  INSERT INTO factura_proveedor (tercero_comercial_id, numero_factura,
      fecha_emision, fecha_vencimiento, monto, moneda, monto_pagado, estado)
  VALUES ('aa000000-0000-0000-0000-000000000040', 'F-ZZ-1',
          '2999-01-05', '2999-02-05', 100, 'BOB', 500, 'REGISTRADA')
$q$);

SELECT pg_temp.debe_fallar('R-CTB-04 factura que vence antes de emitirse', $q$
  INSERT INTO factura_proveedor (tercero_comercial_id, numero_factura,
      fecha_emision, fecha_vencimiento, monto, moneda, monto_pagado, estado)
  VALUES ('aa000000-0000-0000-0000-000000000040', 'F-ZZ-2',
          '2999-03-05', '2999-01-05', 100, 'BOB', 0, 'REGISTRADA')
$q$);

-- R-CTB-05 · quien aprueba la factura no autoriza su pago
SELECT pg_temp.debe_pasar('alta de factura aprobada de prueba', $q$
  INSERT INTO factura_proveedor (id, tercero_comercial_id, numero_factura,
      fecha_emision, fecha_vencimiento, monto, moneda, monto_pagado,
      estado, aprobada_por)
  VALUES ('aa000000-0000-0000-0000-000000000050',
          'aa000000-0000-0000-0000-000000000040', 'F-ZZ-3',
          '2999-01-05', '2999-02-05', 100, 'BOB', 0, 'APROBADA',
          'cc000000-0000-0000-0000-00000000000a')
$q$);

SELECT pg_temp.debe_fallar('R-CTB-05 el aprobador autoriza su propio pago', $q$
  INSERT INTO pago_a_proveedor (factura_proveedor_id, monto, moneda,
      fecha_pago, forma_pago, autorizado_por)
  VALUES ('aa000000-0000-0000-0000-000000000050', 50, 'BOB', now(),
          'TRANSFERENCIA', 'cc000000-0000-0000-0000-00000000000a')
$q$);

SELECT pg_temp.debe_pasar('R-CTB-05 un pagador distinto sí puede pagar', $q$
  INSERT INTO pago_a_proveedor (factura_proveedor_id, monto, moneda,
      fecha_pago, forma_pago, autorizado_por)
  VALUES ('aa000000-0000-0000-0000-000000000050', 50, 'BOB', now(),
          'TRANSFERENCIA', 'cc000000-0000-0000-0000-00000000000b')
$q$);

-- R-CTB-06 · no se cobra más de lo que se debe
SELECT pg_temp.debe_fallar('R-CTB-06 cuenta por cobrar sobrecobrada', $q$
  INSERT INTO cuenta_por_cobrar (origen_tipo, origen_id, monto, moneda,
      monto_cobrado, fecha_vencimiento, estado)
  VALUES ('OTRO', gen_random_uuid(), 100, 'BOB', 500, '2999-03-01', 'PENDIENTE')
$q$);

-- R-CTB-07 · una depreciación por activo y período
SELECT pg_temp.debe_fallar('R-CTB-07 activo con residual mayor al costo', $q$
  INSERT INTO activo_fijo (categoria_activo_fijo_id, codigo_inventario,
      descripcion, fecha_adquisicion, costo_adquisicion, moneda,
      valor_residual, depreciacion_acumulada, estado)
  VALUES ('cc000000-0000-0000-0000-00000000000c', 'INV-ZZ-1',
          'activo de prueba', '2999-01-10', 100, 'BOB', 500, 0, 'ACTIVO')
$q$);

-- R-CTB-08 · el cierre guarda un cuadre que cuadra
SELECT pg_temp.debe_fallar('R-CTB-08 cierre de período descuadrado', $q$
  INSERT INTO cierre_periodo_contable (periodo_contable_id, cerrado_en,
      cerrado_por, total_debe, total_haber)
  VALUES ('aa000000-0000-0000-0000-000000000010', now(),
          'cc000000-0000-0000-0000-00000000000a', 100, 50)
$q$);

-- =====================================================================
--  M14 · Publicidad y campañas (R-PUB)
-- =====================================================================

-- R-PUB-01 · un anunciante es organizador o socio comercial, nunca ninguno
SELECT pg_temp.debe_fallar('R-PUB-01 anunciante sin ninguna referencia', $q$
  INSERT INTO anunciante (tipo, razon_social_facturacion, estado, creado_en)
  VALUES ('SOCIO_COMERCIAL', 'Sin referencia', 'ACTIVO', now())
$q$);

SELECT pg_temp.debe_pasar('alta de socio comercial de prueba', $q$
  INSERT INTO socio_comercial (id, razon_social, numero_documento,
      email_contacto, estado, creado_en)
  VALUES ('bb000000-0000-0000-0000-000000000001', 'Socio de prueba',
          'ZZ-888888', 'socio@prueba.test', 'ACTIVO', now())
$q$);

SELECT pg_temp.debe_fallar('R-PUB-01 anunciante tipo ORGANIZADOR con socio comercial', $q$
  INSERT INTO anunciante (tipo, socio_comercial_id,
      razon_social_facturacion, estado, creado_en)
  VALUES ('ORGANIZADOR', 'bb000000-0000-0000-0000-000000000001',
          'Tipo cruzado', 'ACTIVO', now())
$q$);

SELECT pg_temp.debe_pasar('R-PUB-01 anunciante socio comercial bien formado', $q$
  INSERT INTO anunciante (id, tipo, socio_comercial_id,
      razon_social_facturacion, estado, creado_en)
  VALUES ('bb000000-0000-0000-0000-000000000010', 'SOCIO_COMERCIAL',
          'bb000000-0000-0000-0000-000000000001', 'Socio de prueba',
          'ACTIVO', now())
$q$);

-- R-PUB-02 · una cuenta publicitaria por anunciante
SELECT pg_temp.debe_pasar('alta de cuenta publicitaria', $q$
  INSERT INTO cuenta_publicitaria (id, anunciante_id, limite_gasto_mensual,
      moneda, saldo_consumido_mes, estado, creada_en)
  VALUES ('bb000000-0000-0000-0000-000000000020',
          'bb000000-0000-0000-0000-000000000010', 1000, 'BOB', 0, 'ACTIVA', now())
$q$);

SELECT pg_temp.debe_fallar('R-PUB-02 segunda cuenta publicitaria del mismo anunciante', $q$
  INSERT INTO cuenta_publicitaria (anunciante_id, limite_gasto_mensual,
      moneda, saldo_consumido_mes, estado, creada_en)
  VALUES ('bb000000-0000-0000-0000-000000000010', 500, 'BOB', 0, 'ACTIVA', now())
$q$);

SELECT pg_temp.debe_fallar('R-PUB-02 consumo por encima del límite mensual', $q$
  UPDATE cuenta_publicitaria SET saldo_consumido_mes = 5000
   WHERE id = 'bb000000-0000-0000-0000-000000000020'
$q$);

-- R-PUB-03 · el consumo de una campaña no supera su presupuesto
SELECT pg_temp.debe_fallar('R-PUB-03 campaña con consumo sobre el presupuesto', $q$
  INSERT INTO campana_publicitaria (cuenta_publicitaria_id, nombre, objetivo,
      presupuesto_total, presupuesto_consumido, moneda, fecha_inicio, estado)
  VALUES ('bb000000-0000-0000-0000-000000000020', 'Campana mala', 'TRAFICO',
          100, 500, 'BOB', now(), 'BORRADOR')
$q$);

SELECT pg_temp.debe_fallar('R-PUB-03 campaña ACTIVA sin aprobador', $q$
  INSERT INTO campana_publicitaria (cuenta_publicitaria_id, nombre, objetivo,
      presupuesto_total, presupuesto_consumido, moneda, fecha_inicio, estado)
  VALUES ('bb000000-0000-0000-0000-000000000020', 'Campana sin aprobar',
          'TRAFICO', 100, 0, 'BOB', now(), 'ACTIVA')
$q$);

-- R-PUB-04 · ninguna pieza sin moderar llega a entregarse
SELECT pg_temp.debe_pasar('alta de pieza creativa pendiente', $q$
  INSERT INTO pieza_creativa (id, anunciante_id, titulo, url_recurso,
      tipo_recurso, estado_moderacion, creada_en)
  VALUES ('bb000000-0000-0000-0000-000000000030',
          'bb000000-0000-0000-0000-000000000010', 'Pieza pendiente',
          'https://ejemplo.test/a.png', 'IMAGEN', 'PENDIENTE', now())
$q$);

SELECT pg_temp.debe_pasar('alta de campaña y conjunto para la prueba', $q$
  INSERT INTO campana_publicitaria (id, cuenta_publicitaria_id, nombre,
      objetivo, presupuesto_total, presupuesto_consumido, moneda,
      fecha_inicio, estado)
  VALUES ('bb000000-0000-0000-0000-000000000040',
          'bb000000-0000-0000-0000-000000000020', 'Campana prueba', 'TRAFICO',
          1000, 0, 'BOB', now(), 'BORRADOR');
  INSERT INTO segmento_audiencia (id, nombre, criterios, reutilizable,
      creado_por, creado_en)
  VALUES ('bb000000-0000-0000-0000-000000000050', 'Segmento prueba',
          '{}'::jsonb, TRUE, 'cc000000-0000-0000-0000-00000000000a', now());
  INSERT INTO espacio_publicitario (id, codigo, nombre, tipo,
      capacidad_maxima_simultanea, activo)
  VALUES ('bb000000-0000-0000-0000-000000000060', 'ZZ-ESP', 'Espacio prueba',
          'BANNER_INICIO', 1, TRUE);
  INSERT INTO conjunto_anuncios (id, campana_publicitaria_id,
      segmento_audiencia_id, espacio_publicitario_id, nombre,
      presupuesto_diario, moneda, puja_maxima, modelo_puja, estado)
  VALUES ('bb000000-0000-0000-0000-000000000070',
          'bb000000-0000-0000-0000-000000000040',
          'bb000000-0000-0000-0000-000000000050',
          'bb000000-0000-0000-0000-000000000060', 'Conjunto prueba',
          100, 'BOB', 5, 'CPM', 'ACTIVO')
$q$);

SELECT pg_temp.debe_fallar('R-PUB-04 anuncio con pieza sin aprobar', $q$
  INSERT INTO anuncio (conjunto_anuncios_id, pieza_creativa_id, estado)
  VALUES ('bb000000-0000-0000-0000-000000000070',
          'bb000000-0000-0000-0000-000000000030', 'PROGRAMADO')
$q$);

-- R-PUB-05 · un rechazo sin motivo no es un rechazo
SELECT pg_temp.debe_fallar('R-PUB-05 revisión que rechaza sin motivo', $q$
  INSERT INTO revision_creativa (pieza_creativa_id, revisada_por, decision,
      revisada_en)
  VALUES ('bb000000-0000-0000-0000-000000000030',
          'cc000000-0000-0000-0000-00000000000a', 'RECHAZADA', now())
$q$);

-- R-PUB-06 · un período de facturación por cuenta publicitaria
SELECT pg_temp.debe_pasar('alta de factura de publicidad', $q$
  INSERT INTO factura_publicidad (id, cuenta_publicitaria_id, periodo,
      monto_total, moneda, estado, generada_en)
  VALUES ('bb000000-0000-0000-0000-000000000080',
          'bb000000-0000-0000-0000-000000000020', '2999-01', 100, 'BOB',
          'GENERADA', now())
$q$);

SELECT pg_temp.debe_fallar('R-PUB-06 segunda factura del mismo período', $q$
  INSERT INTO factura_publicidad (cuenta_publicitaria_id, periodo,
      monto_total, moneda, estado, generada_en)
  VALUES ('bb000000-0000-0000-0000-000000000020', '2999-01', 200, 'BOB',
          'GENERADA', now())
$q$);

-- =====================================================================
--  M1 · Acceso administrativo (R-SEG-10/11/12) — ADR-038
--
--  Se prueba el comportamiento, no la presencia: que un operador sin segundo
--  factor NO abra sesión, que su factor no pueda ser un mensaje, y que
--  cambiar su credencial no deje nada vivo detrás. Los actores son propios
--  de la prueba: depender de los seeders la volvería vacía sin avisar.
-- =====================================================================

SELECT pg_temp.debe_pasar('alta de actores de acceso administrativo', $q$
  INSERT INTO usuario (id, codigo_publico, nombres, apellidos, telefono_e164,
      fecha_nacimiento, estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
  VALUES ('cc000000-0000-0000-0000-0000000000f1', 'ZZOPERA01', 'Operadora',
          'Prueba', '+59170000011', '1990-01-01', 'ACTIVO', 'COMPLETO',
          'es', 'America/La_Paz', now()),
         ('cc000000-0000-0000-0000-0000000000f2', 'ZZPARTI01', 'Participante',
          'Prueba', '+59170000012', '1990-01-01', 'ACTIVO', 'COMPLETO',
          'es', 'America/La_Paz', now());
  INSERT INTO rol (id, codigo, nombre, ambito, es_sistema)
  VALUES ('cc000000-0000-0000-0000-0000000000f3', 'ZZ_OPERATIVO',
          'Rol operativo de prueba', 'GLOBAL', FALSE);
  INSERT INTO asignacion_rol (usuario_id, rol_id, ambito, ambito_id,
      otorgada_por, otorgada_en)
  VALUES ('cc000000-0000-0000-0000-0000000000f1',
          'cc000000-0000-0000-0000-0000000000f3', 'GLOBAL', NULL,
          'cc000000-0000-0000-0000-00000000000a', now());
  INSERT INTO dispositivo (id, usuario_id, huella, plataforma, modelo,
      version_app, es_confiable, autorizado_en, ultimo_uso_en)
  VALUES ('cc000000-0000-0000-0000-0000000000f4',
          'cc000000-0000-0000-0000-0000000000f1', 'ZZ-HUELLA-OPERADORA',
          'WEB', 'Escritorio', '1.0', TRUE, now(), now());
  INSERT INTO credencial_acceso (id, usuario_id, hash_contrasena, algoritmo,
      parametros_kdf, requiere_cambio, cambiada_en)
  VALUES ('cc000000-0000-0000-0000-0000000000f5',
          'cc000000-0000-0000-0000-0000000000f1', 'hash-viejo', 'ARGON2ID',
          '{}'::jsonb, FALSE, now())
$q$);

-- R-SEG-10 · sin TOTP confirmado no hay sesión de operador
SELECT pg_temp.debe_fallar('R-SEG-10 sesión de operador sin segundo factor', $q$
  INSERT INTO sesion (usuario_id, dispositivo_id, iniciada_en,
      ultima_actividad_en, expira_en, ip_origen)
  VALUES ('cc000000-0000-0000-0000-0000000000f1',
          'cc000000-0000-0000-0000-0000000000f4', now(), now(),
          now() + interval '1 hour', '190.129.0.99')
$q$);

-- R-SEG-10 · el segundo factor de un operador no puede ser un mensaje
SELECT pg_temp.debe_fallar('R-SEG-10 factor SMS para un operador', $q$
  INSERT INTO factor_mfa (usuario_id, tipo, secreto_cifrado, version_llave,
      activo, es_principal, confirmado_en)
  VALUES ('cc000000-0000-0000-0000-0000000000f1', 'SMS', 'x', 1, TRUE, TRUE, now())
$q$);

-- ... y para un participante el mismo factor sigue valiendo
SELECT pg_temp.debe_pasar('R-SEG-10 factor SMS para un participante', $q$
  INSERT INTO factor_mfa (usuario_id, tipo, secreto_cifrado, version_llave,
      activo, es_principal, confirmado_en)
  VALUES ('cc000000-0000-0000-0000-0000000000f2', 'SMS', 'x', 1, TRUE, TRUE, now())
$q$);

SELECT pg_temp.debe_pasar('alta del TOTP de la operadora', $q$
  INSERT INTO factor_mfa (usuario_id, tipo, secreto_cifrado, version_llave,
      activo, es_principal, confirmado_en)
  VALUES ('cc000000-0000-0000-0000-0000000000f1', 'TOTP', 'x', 1, TRUE, TRUE, now())
$q$);

SELECT pg_temp.debe_pasar('R-SEG-10 con TOTP confirmado la sesión abre', $q$
  INSERT INTO sesion (id, usuario_id, dispositivo_id, iniciada_en,
      ultima_actividad_en, expira_en, ip_origen)
  VALUES ('cc000000-0000-0000-0000-0000000000f6',
          'cc000000-0000-0000-0000-0000000000f1',
          'cc000000-0000-0000-0000-0000000000f4', now(), now(),
          now() + interval '1 hour', '190.129.0.99')
$q$);

-- R-SEG-11 · cambiar la credencial del operador no deja nada vivo detrás
SELECT pg_temp.debe_pasar('R-SEG-11 cambio de credencial de la operadora', $q$
  UPDATE credencial_acceso SET hash_contrasena = 'hash-nuevo', cambiada_en = now()
   WHERE id = 'cc000000-0000-0000-0000-0000000000f5'
$q$);

SELECT CASE WHEN NOT EXISTS (
         SELECT 1 FROM sesion
          WHERE usuario_id = 'cc000000-0000-0000-0000-0000000000f1'
            AND revocada_en IS NULL)
       THEN 'OK    · R-SEG-11 ninguna sesión del operador quedó viva'
       ELSE 'FALLA · R-SEG-11 quedó una sesión viva tras cambiar la credencial' END;

SELECT CASE WHEN NOT EXISTS (
         SELECT 1 FROM dispositivo
          WHERE usuario_id = 'cc000000-0000-0000-0000-0000000000f1'
            AND es_confiable)
       THEN 'OK    · R-SEG-11 ningún dispositivo del operador quedó confiable'
       ELSE 'FALLA · R-SEG-11 quedó un dispositivo confiable' END;

-- R-SEG-12 · lo irreversible se confirma con el segundo factor
SELECT pg_temp.debe_fallar('R-SEG-12 permiso APROBAR sin segundo factor', $q$
  INSERT INTO permiso (codigo, descripcion, recurso, accion, requiere_mfa)
  VALUES ('ZZ_APROBAR_SIN_MFA', 'prueba', 'zz', 'APROBAR', FALSE)
$q$);

SELECT pg_temp.debe_pasar('R-SEG-12 el mismo permiso con segundo factor entra', $q$
  INSERT INTO permiso (codigo, descripcion, recurso, accion, requiere_mfa)
  VALUES ('ZZ_APROBAR_CON_MFA', 'prueba', 'zz', 'APROBAR', TRUE)
$q$);

-- --- restricciones que exigen datos de varios módulos: se verifica que
--     existan y estén activas, sin simular el flujo completo ----------
WITH esperadas(codigo, objeto) AS (VALUES
      ('R-CON-01 ck_reclamo_plazo',              'ck_reclamo_plazo'),
      ('R-CON-02 ck_reclamo_prorroga',           'ck_reclamo_prorroga'),
      ('R-CON-03 ck_reclamo_prorroga_extendida', 'ck_reclamo_prorroga_extendida'),
      ('R-CON-04 ck_reclamo_reparacion',         'ck_reclamo_reparacion'),
      ('R-CON-05 ck_reclamo_conservacion',       'ck_reclamo_conservacion'),
      ('R-GRP-01 uq_entrega_turno',              'uq_entrega_turno'),
      ('R-GRP-02 ck_entrega_neto',               'ck_entrega_neto'),
      ('R-GRP-02 ck_entrega_neto_no_negativo',   'ck_entrega_neto_no_negativo'),
      ('R-GRP-03 uq_obligacion_periodo_cupo',    'uq_obligacion_periodo_cupo'),
      ('R-GRP-04 tg_retiro_no_grupo',            'tg_retiro_no_grupo'),
      ('R-TAR-01 ex_tarifario_vigente',          'ex_tarifario_vigente'),
      ('R-TAR-04 uq_devengo_hecho',              'uq_devengo_hecho'),
      ('R-TAR-06 uq_cargo_deduccion',            'uq_cargo_deduccion'),
      ('R-TAR-08 tg_tarifario_preaviso',         'tg_tarifario_preaviso'),
      ('R-TAR-09 uq_factura_cuf',                'uq_factura_cuf'),
      ('R-TAR-10 tg_factura_inmutable',          'tg_factura_inmutable'),
      ('R-TAR-11 tg_devolucion_maxima',          'tg_devolucion_maxima'),
      ('R-TAR-13 ck_factura_offline_evento',     'ck_factura_offline_evento'),
      ('R-UIF-10 tg_ddd_pep',                    'tg_ddd_pep'),
      ('R-UIF-11 ex_calificacion_vigente',       'ex_calificacion_vigente'),
      ('R-UIF-13 uq_operelev_tx_umbral',         'uq_operelev_tx_umbral'),
      ('R-SEG-01 ck_instrumento_sin_pan',        'ck_instrumento_sin_pan'),
      ('R-SEG-02 ck_acceso_justificacion',       'ck_acceso_justificacion'),
      ('R-SEG-04 ck_entrega_segregacion',        'ck_entrega_segregacion'),
      ('R-SEG-05 ck_incidente_plazo',            'ck_incidente_plazo'),
      ('R-SEG-06 tg_anonimizacion_retencion',    'tg_anonimizacion_retencion'),
      ('R-BIL-11 uq_conciliacion_cuenta_fecha',  'uq_conciliacion_cuenta_fecha'),
      ('R-BIL-12 tg_cierre_diario_valido',       'tg_cierre_diario_valido'),
      ('R-BIL-13 tg_cuenta_cierre_valido',       'tg_cuenta_cierre_valido'),
      ('R-BIL-14 uq_bloqueo_oficio',             'uq_bloqueo_oficio'),
      ('R-BIL-15 uq_reverso_original',           'uq_reverso_original'),
      ('R-LIM-02 uq_consumo_ventana',            'uq_consumo_ventana'),
      ('R-LIM-03 ex_limite_vigencia',            'ex_limite_vigencia'),
      ('R-RIS-01 ck_evento_categoria',           'ck_evento_categoria'),
      ('R-RIS-03 ck_plan_objetivos',             'ck_plan_objetivos'),
      ('R-LIC-02 ck_sandbox_limites',            'ck_sandbox_limites'),
      ('R-LIC-03 ck_politica_acta',              'ck_politica_acta'),
      ('R-AUD-05 tg_asiento_cuadrado',           'tg_asiento_cuadrado'),
      ('R-AUD-07 uq_saldo_diario_cuenta_fecha',  'uq_saldo_diario_cuenta_fecha'),
      ('R-BIL-17 uq_cuenta_benef_hash',          'uq_cuenta_benef_hash'),
      ('R-BIL-17 uq_cuenta_benef_principal',     'uq_cuenta_benef_principal'),
      ('R-SEG-07 ck_asignacion_no_autoasignada', 'ck_asignacion_no_autoasignada'),
      ('R-SEG-08 uq_asignacion_vigente',         'uq_asignacion_vigente'),
      ('R-SEG-10 tg_sesion_operador_mfa',        'tg_sesion_operador_mfa'),
      ('R-SEG-10 tg_factor_operador_valido',     'tg_factor_operador_valido'),
      ('R-SEG-11 tg_credencial_operador_corta_sesiones', 'tg_credencial_operador_corta_sesiones'),
      ('R-SEG-12 ck_permiso_decision_exige_mfa', 'ck_permiso_decision_exige_mfa'),
      ('R-UIF-12 uq_oficial_titular_activo',     'uq_oficial_titular_activo'),
      ('R-LIC-04 ck_evaluacion_no_objecion',     'ck_evaluacion_no_objecion'),
      ('R-GRP-14 uq_solicitud_ingreso_pendiente','uq_solicitud_ingreso_pendiente'),
      ('R-GRP-15 uq_invitacion_token',           'uq_invitacion_token'),
      ('R-GRP-16 uq_dia_no_habil',               'uq_dia_no_habil'),
      ('R-REP-05 uq_insignia_usuario',           'uq_insignia_usuario'),
      ('R-REP-06 uq_resena_autor_evaluado',      'uq_resena_autor_evaluado'),
      ('R-REP-06 tg_resena_convivencia',         'tg_resena_convivencia'),
      ('R-GAR-01 ck_incumplimiento_plazo_guardado','ck_incumplimiento_plazo_guardado'),
      ('R-GAR-02 tg_evidencia_inmutable',        'tg_evidencia_inmutable'),
      ('R-GAR-03 uq_ejecucion_aval_registro',    'uq_ejecucion_aval_registro'),
      ('R-GAR-04 tg_ejecucion_aval_tope',        'tg_ejecucion_aval_tope'),
      ('R-GAR-05 uq_restriccion_usuario_vigente','uq_restriccion_usuario_vigente'),
      ('R-GAR-06 ck_devolucion_cuadra',          'ck_devolucion_cuadra'),
      ('R-GAR-07 uq_alerta_temprana_abierta',    'uq_alerta_temprana_abierta'),
      ('R-DES-01 uq_orden_desembolso_clave',     'uq_orden_desembolso_clave'),
      ('R-DES-01 uq_orden_desembolso_entrega_viva','uq_orden_desembolso_entrega_viva'),
      ('R-DES-02 tg_orden_desembolso_cuenta_verificada','tg_orden_desembolso_cuenta_verificada'),
      ('R-ORG-01 uq_solicitud_organizador_pendiente','uq_solicitud_organizador_pendiente'),
      ('R-ORG-02 ex_contrato_org_vigente',       'ex_contrato_org_vigente'),
      ('R-ORG-02 tg_grupo_contrato_organizador', 'tg_grupo_contrato_organizador'),
      ('R-ORG-03 tg_contrato_org_inmutable',     'tg_contrato_org_inmutable'),
      ('R-ORG-04 uq_evaluacion_org_periodo',     'uq_evaluacion_org_periodo'),
      ('R-ORG-05 uq_apelacion_por_sancion',      'uq_apelacion_por_sancion'),
      ('R-ORG-05 tg_apelacion_org_resolutor',    'tg_apelacion_org_resolutor'),
      ('R-ORG-06 ck_regla_confirmacion_humana',  'ck_regla_confirmacion_humana'),
      ('R-ORG-07 uq_tarea_automatizada_clave',   'uq_tarea_automatizada_clave'),
      ('R-CTB-01 tg_asiento_periodo_abierto',    'tg_asiento_periodo_abierto'),
      ('R-CTB-02 tg_movimiento_cuenta_de_movimiento','tg_movimiento_cuenta_de_movimiento'),
      ('R-CTB-03 uq_presupuesto_centro_ejercicio','uq_presupuesto_centro_ejercicio'),
      ('R-CTB-04 uq_factura_proveedor_numero',   'uq_factura_proveedor_numero'),
      ('R-CTB-05 tg_pago_proveedor_segregacion', 'tg_pago_proveedor_segregacion'),
      ('R-CTB-06 ck_cxc_cobrado',                'ck_cxc_cobrado'),
      ('R-CTB-07 uq_depreciacion_activo_periodo','uq_depreciacion_activo_periodo'),
      ('R-CTB-08 uq_estado_financiero_periodo_tipo','uq_estado_financiero_periodo_tipo'),
      ('R-PUB-01 ck_anunciante_tipo_exclusivo',  'ck_anunciante_tipo_exclusivo'),
      ('R-PUB-02 uq_cuenta_publicitaria_anunciante','uq_cuenta_publicitaria_anunciante'),
      ('R-PUB-03 ck_campana_pub_consumo',        'ck_campana_pub_consumo'),
      ('R-PUB-04 tg_anuncio_creativa_aprobada',  'tg_anuncio_creativa_aprobada'),
      ('R-PUB-05 tg_revision_creativa_moderador','tg_revision_creativa_moderador'),
      ('R-PUB-06 uq_factura_publicidad_cuenta_periodo','uq_factura_publicidad_cuenta_periodo')
)
SELECT CASE WHEN EXISTS (SELECT 1 FROM pg_constraint WHERE conname = objeto)
              OR EXISTS (SELECT 1 FROM pg_class WHERE relname = objeto AND relkind = 'i')
              OR EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = objeto)
            THEN 'OK    · ' || codigo || ' presente'
            ELSE 'FALLA · ' || codigo || ' NO existe en la base' END
  FROM esperadas ORDER BY codigo;

\echo ''
\echo 'Prueba de humo terminada: toda línea debe empezar con OK.'
