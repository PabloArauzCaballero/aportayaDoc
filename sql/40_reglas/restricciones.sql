-- =====================================================================
--  AportaYa — restricciones normativas
--  GENERADO desde docs/Restricciones.md por scripts/extraer_sql.py
--  No edite este archivo a mano: edite el documento y regenere.
--
--  Requisitos del motor: PostgreSQL 15+, extensiones pgcrypto y btree_gist
-- =====================================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;



-- ---------------------------------------------------------------------
-- R-AUD — Auditoría, inmutabilidad y conservación
-- ---------------------------------------------------------------------

-- R-AUD-01 · append-only por privilegios, no por convención
REVOKE UPDATE, DELETE ON
    transaccion_billetera, movimiento_billetera, movimiento_custodia,
    saldo_diario_billetera, devengo_comision, asiento_contable,
    movimiento_contable, bitacora_evento, evento_dominio,
    registro_acceso_datos, movimiento_fondo, abono_recuperacion,
    registro_operacion_relevante, evento_riesgo_operativo, acta_comite
FROM rol_aplicacion;

-- Refuerzo: incluso un superusuario distraído choca con el trigger
CREATE OR REPLACE FUNCTION fn_aud_bloquear_mutacion() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'R-AUD-01: % es append-only; corrija con el movimiento inverso',
        TG_TABLE_NAME;
END $$ LANGUAGE plpgsql;

-- El disparador se crea para CADA tabla de la lista. No se escribe a mano:
-- `scripts/generar_ddl.py` lo emite en sql/35_append_only/append_only.sql a
-- partir de la lista APPEND_ONLY del modelo, de modo que agregar una tabla a
-- esa lista alcanza para que quede sellada.

-- R-AUD-02 / R-AUD-03 · cadena de hash verificable
-- El bloqueo consultivo NO es opcional. Sin él, dos inserciones concurrentes
-- leen el mismo `hash_registro` predecesor y producen dos eslabones hermanos:
-- la cadena deja de ser una cadena y la verificación de CU-73 no puede
-- distinguir una bifurcación legítima de un registro eliminado. Además
-- `secuencia` es BIGSERIAL: los valores se asignan al pedirlos, pero los COMMIT
-- pueden ocurrir en otro orden, así que "el último por secuencia" no es
-- necesariamente el último confirmado. El bloqueo resuelve las dos cosas: sólo
-- un escritor de la cadena a la vez.
--
-- El hash cubre TODO lo que hay que poder probar, no un subconjunto cómodo. La
-- versión anterior dejaba fuera `estado`, `moneda` y `clave_idempotencia`, y
-- sobre todo dejaba fuera las patas: se podía cambiar a quién se le debitó sin
-- que el hash de la transacción cambiara. Las patas entran por su digest en el
-- sellado diferido de abajo, porque al momento del INSERT todavía no existen.
CREATE OR REPLACE FUNCTION fn_aud_encadenar_transaccion() RETURNS trigger AS $$
DECLARE v_anterior VARCHAR(64);
BEGIN
  PERFORM pg_advisory_xact_lock(hashtext('cadena_transaccion_billetera'));
  SELECT hash_registro INTO v_anterior
    FROM transaccion_billetera ORDER BY secuencia DESC LIMIT 1;
  NEW.hash_anterior := v_anterior;
  NEW.hash_registro := encode(digest(
      NEW.id::text || COALESCE(NEW.secuencia::text,'') || NEW.tipo ||
      NEW.estado || NEW.moneda || NEW.monto_total::text ||
      COALESCE(NEW.origen_tipo,'') || COALESCE(NEW.origen_id::text,'') ||
      NEW.clave_idempotencia || COALESCE(NEW.iniciada_por::text,'') ||
      NEW.canal || NEW.ocurrida_en::text ||
      COALESCE(v_anterior,''), 'sha256'), 'hex');
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_transaccion_billetera_hash
  BEFORE INSERT ON transaccion_billetera
  FOR EACH ROW EXECUTE FUNCTION fn_aud_encadenar_transaccion();

-- Las patas quedan fuera del hash del encabezado por una razón física: al
-- ejecutarse el BEFORE INSERT de la transacción, los movimientos todavía no
-- existen. Sellarlas después exigiría un UPDATE sobre `transaccion_billetera`,
-- que es append-only (R-AUD-01) y lo rechazaría —el sello se bloquearía a sí
-- mismo—. No hace falta: `movimiento_billetera` es append-only por derecho
-- propio, así que una pata no se puede alterar ni borrar después de escrita. Lo
-- que sí hay que poder detectar es una pata huérfana o un conjunto que no
-- cuadre, y de eso se ocupan las consultas de verificación (R-AUD-10).

-- R-AUD-09 · la bitácora se encadena sola: la aplicación no firma su propia huella
--
-- `hash_registro` y `hash_anterior` los escribía la aplicación. Una bitácora que
-- firma la aplicación no prueba nada contra la aplicación, que es exactamente el
-- adversario del que hay que defenderse ante un regulador. Ahora los calcula la
-- base y el rol de aplicación no puede alterarlos (la tabla es append-only).
CREATE OR REPLACE FUNCTION fn_aud_encadenar_bitacora() RETURNS trigger AS $$
DECLARE v_anterior VARCHAR(64);
BEGIN
  PERFORM pg_advisory_xact_lock(hashtext('cadena_bitacora_evento'));
  SELECT hash_registro INTO v_anterior
    FROM bitacora_evento ORDER BY secuencia DESC LIMIT 1;
  NEW.hash_anterior := COALESCE(v_anterior, repeat('0', 64));
  NEW.hash_registro := encode(digest(
      NEW.entidad || NEW.entidad_id::text || NEW.accion ||
      COALESCE(NEW.actor_usuario_id::text,'') || COALESCE(NEW.actor_rol,'') ||
      COALESCE(NEW.suplantando_a_usuario_id::text,'') || NEW.origen ||
      NEW.correlation_id::text || COALESCE(NEW.valor_anterior::text,'') ||
      COALESCE(NEW.valor_nuevo::text,'') || COALESCE(NEW.motivo,'') ||
      NEW.fecha_hora::text || NEW.hash_anterior, 'sha256'), 'hex');
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_bitacora_evento_hash
  BEFORE INSERT ON bitacora_evento
  FOR EACH ROW EXECUTE FUNCTION fn_aud_encadenar_bitacora();

-- R-AUD-05 · invariante de partida doble contable
CREATE OR REPLACE FUNCTION fn_aud_asiento_cuadrado() RETURNS trigger AS $$
DECLARE v_debe NUMERIC(16,2); v_haber NUMERIC(16,2);
BEGIN
  SELECT COALESCE(SUM(debe),0), COALESCE(SUM(haber),0)
    INTO v_debe, v_haber
    FROM movimiento_contable WHERE asiento_id = NEW.id;
  IF v_debe <> v_haber THEN
    RAISE EXCEPTION 'R-AUD-05: asiento % descuadrado (debe=%, haber=%)',
                    NEW.id, v_debe, v_haber;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

-- El cuadre se le exige a los DOS estados que dejan un asiento en firme. Con la
-- condición puesta solo en 'CONFIRMADO', un asiento marcado 'REVERSADO' entraba sin
-- que nadie verificara su partida doble: la corrección de un error contable era
-- justamente el único movimiento que podía descuadrar impunemente.
CREATE CONSTRAINT TRIGGER tg_asiento_cuadrado
  AFTER INSERT OR UPDATE ON asiento_contable
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW WHEN (NEW.estado IN ('CONFIRMADO', 'REVERSADO'))
  EXECUTE FUNCTION fn_aud_asiento_cuadrado();

-- R-AUD-06 · la reversa apunta a un asiento distinto y confirmado
ALTER TABLE asiento_contable
  ADD CONSTRAINT ck_asiento_reversa_distinta
  CHECK (asiento_reversa_id IS NULL OR asiento_reversa_id <> id);

-- R-AUD-11 · qué asiento lleva el estado REVERSADO
--
-- El CHECK de `estado` admitía 'REVERSADO' y ningún caso de uso decía a cuál de los
-- dos asientos le tocaba. La lectura natural —marcar el ORIGINAL— es imposible:
-- `asiento_contable` es append-only (R-AUD-01), así que su estado no se puede
-- cambiar después. De modo que 'REVERSADO' solo puede escribirse al insertar, y el
-- único asiento que se inserta sabiendo que es una corrección es el inverso.
--
-- Queda entonces una equivalencia, y se hace cumplir en las dos direcciones: un
-- asiento está REVERSADO si y solo si apunta al que corrige. Sin esto, "reversado"
-- era una palabra que cada carril iba a interpretar a su manera.
ALTER TABLE asiento_contable
  ADD CONSTRAINT ck_asiento_reversado_enlazado CHECK (
        (estado = 'REVERSADO') = (asiento_reversa_id IS NOT NULL));

-- R-AUD-07 · un cierre de saldo por cuenta y día
ALTER TABLE saldo_diario_billetera
  ADD CONSTRAINT uq_saldo_diario_cuenta_fecha UNIQUE (cuenta_billetera_id, fecha);

-- R-AUD-08 · no se depura antes de tiempo
ALTER TABLE expediente_cliente
  ADD CONSTRAINT ck_expediente_retencion_futura
  CHECK (retencion_hasta >= ultima_actualizacion::date);


-- ---------------------------------------------------------------------
-- R-BIL — Billetera, saldo y custodia
-- ---------------------------------------------------------------------

-- R-BIL-01 · partida doble interna (diferido: se valida al COMMIT)
CREATE OR REPLACE FUNCTION fn_bil_transaccion_cuadrada() RETURNS trigger AS $$
DECLARE v_debitos NUMERIC(16,2); v_creditos NUMERIC(16,2);
BEGIN
  SELECT COALESCE(SUM(monto) FILTER (WHERE sentido='DEBITO'),0),
         COALESCE(SUM(monto) FILTER (WHERE sentido='CREDITO'),0)
    INTO v_debitos, v_creditos
    FROM movimiento_billetera WHERE transaccion_id = NEW.id;
  IF v_debitos <> v_creditos THEN
    RAISE EXCEPTION 'R-BIL-01: transacción % descuadrada (D=%, C=%)',
                    NEW.id, v_debitos, v_creditos;
  END IF;
  IF v_debitos = 0 THEN
    RAISE EXCEPTION 'R-BIL-01: transacción % sin movimientos', NEW.id;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER tg_transaccion_cuadrada
  AFTER INSERT OR UPDATE ON transaccion_billetera
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW WHEN (NEW.estado = 'APLICADA')
  EXECUTE FUNCTION fn_bil_transaccion_cuadrada();

-- R-BIL-02 y R-BIL-03 · saldos coherentes y no negativos
ALTER TABLE cuenta_billetera
  ADD CONSTRAINT ck_cuenta_saldo_no_negativo
    CHECK (permite_saldo_negativo OR saldo_disponible >= 0),
  ADD CONSTRAINT ck_cuenta_retenido_no_negativo
    CHECK (saldo_retenido >= 0);
-- saldo_total es GENERATED ALWAYS AS (saldo_disponible + saldo_retenido) STORED

-- R-BIL-04 · unicidad por tipo de titular
CREATE UNIQUE INDEX uq_cuenta_usuario_moneda
  ON cuenta_billetera (usuario_id, moneda, tipo)
  WHERE tipo = 'USUARIO' AND estado <> 'CERRADA';
CREATE UNIQUE INDEX uq_cuenta_grupo_moneda
  ON cuenta_billetera (grupo_id, moneda)
  WHERE tipo = 'GRUPO' AND estado <> 'CERRADA';

-- R-BIL-05 · titularidad coherente con el tipo
ALTER TABLE cuenta_billetera
  ADD CONSTRAINT ck_cuenta_titularidad CHECK (
      (tipo = 'USUARIO' AND usuario_id IS NOT NULL AND grupo_id IS NULL)
   OR (tipo = 'GRUPO'   AND grupo_id  IS NOT NULL AND usuario_id IS NULL)
   OR (tipo NOT IN ('USUARIO','GRUPO') AND usuario_id IS NULL AND grupo_id IS NULL)
  );

-- R-BIL-06 · idempotencia extremo a extremo
--
-- La clave se ampara SIEMPRE en el titular de la operación, nunca sola. Una
-- unicidad global convierte la clave en un recurso compartido entre usuarios:
-- quien reutilice —por azar o a propósito— la clave de otro hace que la
-- operación legítima del otro sea rechazada. El espacio de claves es de cada
-- titular. Se usa el centinela en `COALESCE` para que la unicidad también
-- alcance a las operaciones sin usuario (lotes, sistema), donde `NULL` dejaría
-- pasar duplicados.
CREATE UNIQUE INDEX uq_tx_idem ON transaccion_billetera (
    COALESCE(iniciada_por, '00000000-0000-0000-0000-000000000000'::uuid),
    origen_tipo, clave_idempotencia);
CREATE UNIQUE INDEX uq_recarga_idem
  ON orden_recarga (cuenta_billetera_id, clave_idempotencia);
CREATE UNIQUE INDEX uq_retiro_idem
  ON orden_retiro (cuenta_billetera_id, clave_idempotencia);
CREATE UNIQUE INDEX uq_devengo_idem ON devengo_comision (
    COALESCE(grupo_id, '00000000-0000-0000-0000-000000000000'::uuid),
    clave_idempotencia);

-- La misma regla, en el resto del sistema. Cada clave se ampara en el objeto
-- del que depende la operación. `webhook_pasarela` se ampara en el proveedor
-- porque la clave la emite él: dos pasarelas distintas pueden mandar el mismo
-- identificador de evento sin que eso signifique que sea el mismo hecho.
CREATE UNIQUE INDEX uq_orden_cobro_idem
  ON orden_cobro (obligacion_id, clave_idempotencia);
CREATE UNIQUE INDEX uq_intento_pago_idem
  ON intento_pago (orden_cobro_id, clave_idempotencia);
CREATE UNIQUE INDEX uq_pago_idem
  ON pago (obligacion_id, clave_idempotencia);
CREATE UNIQUE INDEX uq_webhook_idem
  ON webhook_pasarela (proveedor_id, clave_idempotencia);
CREATE UNIQUE INDEX uq_cotizacion_idem
  ON cotizacion_comision (referencia_id, clave_idempotencia);
CREATE UNIQUE INDEX uq_token_verificacion_idem ON token_verificacion (
    COALESCE(usuario_id, '00000000-0000-0000-0000-000000000000'::uuid),
    clave_idempotencia);

-- R-BIL-19 · el reintento devuelve la primera respuesta, no un error
--
-- La unicidad sola no alcanza. El caso para el que existe la idempotencia es el
-- reintento tras un timeout: el cliente no sabe si la operación se aplicó. Con
-- sólo un UNIQUE, ese reintento choca contra la violación y devuelve un error,
-- que es indistinguible de "falló". Hay que poder devolver la respuesta original.
--
-- `hash_solicitud` cierra el hueco restante: la misma clave con otro cuerpo es un
-- conflicto (409), nunca una reejecución silenciosa con parámetros distintos.
ALTER TABLE respuesta_idempotente
  ADD CONSTRAINT ck_respuesta_idem_hash CHECK (length(hash_solicitud) = 64),
  ADD CONSTRAINT ck_respuesta_idem_expira CHECK (expira_en > registrada_en),
  ADD CONSTRAINT ck_respuesta_idem_http CHECK (codigo_http BETWEEN 100 AND 599);

CREATE INDEX ix_respuesta_idem_expiradas ON respuesta_idempotente (expira_en);

-- R-BIL-07 y R-BIL-16 · los dos saldos se derivan, no se escriben
--
--   saldo_retenido   = SUM(retenciones VIGENTES)
--   saldo_disponible = SUM(movimientos) - saldo_retenido
--   saldo_total      = disponible + retenido  (columna generada)
--
-- Mantenerlos por trigger elimina toda una clase de defecto: una aplicación que
-- olvide actualizar la caché, un script suelto, o un reintento a medias.
--
-- El `FOR UPDATE` de la primera línea NO es decorativo: es la regla. Sin él, dos
-- transacciones concurrentes sobre la misma cuenta leen cada una un snapshot que
-- no contiene el movimiento de la otra, calculan el mismo saldo, y la segunda
-- pisa a la primera al despertar del bloqueo de fila. Se pierde un movimiento
-- del saldo, y como `ck_cuenta_saldo_no_negativo` se evalúa sobre esta columna,
-- el control de saldo no negativo pasa a evaluarse contra un saldo falso: dos
-- retiros simultáneos sobregiran la cuenta. Tomar el bloqueo ANTES de leer
-- obliga a la segunda transacción a releer el libro ya completo.
CREATE OR REPLACE FUNCTION fn_bil_recalcular_saldos(p_cuenta UUID) RETURNS VOID AS $$
DECLARE v_movimientos NUMERIC(16,2); v_retenido NUMERIC(16,2);
BEGIN
  PERFORM 1 FROM cuenta_billetera WHERE id = p_cuenta FOR UPDATE;

  SELECT COALESCE(SUM(CASE WHEN sentido = 'CREDITO' THEN monto ELSE -monto END), 0)
    INTO v_movimientos
    FROM movimiento_billetera WHERE cuenta_billetera_id = p_cuenta;
  SELECT COALESCE(SUM(monto), 0) INTO v_retenido
    FROM retencion_saldo
   WHERE cuenta_billetera_id = p_cuenta AND estado = 'VIGENTE';
  UPDATE cuenta_billetera
     SET saldo_retenido   = v_retenido,
         saldo_disponible = v_movimientos - v_retenido,
         version          = version + 1
   WHERE id = p_cuenta;
END $$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_bil_sincronizar_saldos() RETURNS trigger AS $$
BEGIN
  PERFORM fn_bil_recalcular_saldos(
    COALESCE(NEW.cuenta_billetera_id, OLD.cuenta_billetera_id));
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_retencion_sincroniza_saldo
  AFTER INSERT OR UPDATE OF estado, monto ON retencion_saldo
  FOR EACH ROW EXECUTE FUNCTION fn_bil_sincronizar_saldos();

CREATE TRIGGER tg_movimiento_sincroniza_saldo
  AFTER INSERT ON movimiento_billetera
  FOR EACH ROW EXECUTE FUNCTION fn_bil_sincronizar_saldos();

-- R-BIL-20 · la partida doble también cuadra en moneda
--
-- R-BIL-01 suma importes sin mirar la moneda: una transacción que debita 100 USD
-- y acredita 100 BOB cuadra numéricamente y descuadra económicamente. Mientras
-- el sistema sea de una sola moneda el defecto está latente; el día que entre la
-- segunda es una fuga de valor silenciosa. Se verifica al COMMIT porque las
-- patas no existen todavía al insertar el encabezado.
CREATE OR REPLACE FUNCTION fn_bil_moneda_coherente() RETURNS trigger AS $$
DECLARE v_distintas INT;
BEGIN
  SELECT count(*) INTO v_distintas
    FROM movimiento_billetera m
    JOIN cuenta_billetera c ON c.id = m.cuenta_billetera_id
   WHERE m.transaccion_id = NEW.id AND c.moneda <> NEW.moneda;
  IF v_distintas > 0 THEN
    RAISE EXCEPTION
      'R-BIL-20: la transacción % toca % cuenta(s) en moneda distinta de %',
      NEW.id, v_distintas, NEW.moneda;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER tg_transaccion_moneda
  AFTER INSERT OR UPDATE ON transaccion_billetera
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW WHEN (NEW.estado = 'APLICADA')
  EXECUTE FUNCTION fn_bil_moneda_coherente();

-- La orden y la cuenta que debita comparten moneda, o el neto no significa nada.
CREATE OR REPLACE FUNCTION fn_bil_moneda_orden() RETURNS trigger AS $$
DECLARE v_moneda CHAR(3);
BEGIN
  SELECT moneda INTO v_moneda FROM cuenta_billetera WHERE id = NEW.cuenta_billetera_id;
  IF v_moneda IS DISTINCT FROM NEW.moneda THEN
    RAISE EXCEPTION 'R-BIL-20: la orden está en % y la cuenta en %', NEW.moneda, v_moneda;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_retiro_moneda
  BEFORE INSERT OR UPDATE OF moneda ON orden_retiro
  FOR EACH ROW EXECUTE FUNCTION fn_bil_moneda_orden();

CREATE TRIGGER tg_recarga_moneda
  BEFORE INSERT OR UPDATE OF moneda ON orden_recarga
  FOR EACH ROW EXECUTE FUNCTION fn_bil_moneda_orden();

-- R-BIL-08 · toda retención expira salvo orden de autoridad
ALTER TABLE retencion_saldo
  ADD CONSTRAINT ck_retencion_expira CHECK (
      motivo = 'ORDEN_AUTORIDAD' OR expira_en IS NOT NULL
  );

-- R-BIL-09 · condiciones duras del retiro
ALTER TABLE orden_retiro
  ADD CONSTRAINT ck_retiro_mfa CHECK (
      estado IN ('BORRADOR','RECHAZADA') OR mfa_verificado = TRUE
  );

CREATE OR REPLACE FUNCTION fn_bil_validar_instrumento_retiro() RETURNS trigger AS $$
DECLARE v_ok BOOLEAN;
BEGIN
  SELECT (estado_verificacion = 'VERIFICADO'
          AND titular_coincide
          AND (bloqueado_hasta IS NULL OR bloqueado_hasta < now()))
    INTO v_ok FROM instrumento_fondeo WHERE id = NEW.instrumento_destino_id;
  IF NOT COALESCE(v_ok,FALSE) THEN
    RAISE EXCEPTION 'R-BIL-09: instrumento destino no habilitado para retiro';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_retiro_instrumento
  BEFORE INSERT ON orden_retiro
  FOR EACH ROW EXECUTE FUNCTION fn_bil_validar_instrumento_retiro();

-- R-BIL-10 · una referencia externa, una acreditación
ALTER TABLE orden_recarga
  ADD CONSTRAINT uq_recarga_referencia UNIQUE (referencia_externa);

-- R-BIL-11b · con el encaje roto no sale dinero
--
-- Registrar que el encaje no se cumple y seguir pagando retiros es el escenario
-- clásico de la corrida: se le paga a los primeros que llegan y no queda para
-- los demás. `AP-CU11-06` estaba declarado como error del caso de uso pero
-- ninguna regla lo aplicaba. El modo restringido lo aplica la base.
--
-- Se mira la última conciliación de cada cuenta de custodia de la moneda: si
-- alguna no cumple encaje, no se autorizan salidas nuevas. Las órdenes ya
-- autorizadas siguen su curso: frenar a mitad de camino dejaría dinero retenido
-- sin pagar ni devolver, que es peor.
CREATE OR REPLACE FUNCTION fn_bil_exigir_encaje() RETURNS trigger AS $$
DECLARE v_incumple INT;
BEGIN
  IF NEW.estado IN ('BORRADOR','RECHAZADA') THEN
    RETURN NEW;
  END IF;
  SELECT count(*) INTO v_incumple
    FROM (SELECT DISTINCT ON (cuenta_custodia_id) cumple_encaje
            FROM conciliacion_custodia
           ORDER BY cuenta_custodia_id, fecha DESC) ultima
   WHERE NOT ultima.cumple_encaje;
  IF v_incumple > 0 THEN
    RAISE EXCEPTION
      'R-BIL-11: encaje incumplido en % cuenta(s) de custodia; salidas suspendidas',
      v_incumple;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_retiro_encaje
  BEFORE INSERT OR UPDATE OF estado ON orden_retiro
  FOR EACH ROW EXECUTE FUNCTION fn_bil_exigir_encaje();

-- R-BIL-11 · encaje mínimo y unicidad diaria de la conciliación
ALTER TABLE conciliacion_custodia
  ADD CONSTRAINT uq_conciliacion_cuenta_fecha UNIQUE (cuenta_custodia_id, fecha),
  ADD CONSTRAINT ck_conciliacion_encaje
    CHECK (cumple_encaje = (ratio_cobertura >= 1.0));

-- R-BIL-12 · no se cierra el día con problemas abiertos
CREATE OR REPLACE FUNCTION fn_bil_validar_cierre_diario() RETURNS trigger AS $$
DECLARE v_excepciones INT; v_descuadres INT;
BEGIN
  SELECT count(*) INTO v_excepciones
    FROM excepcion_conciliacion e
    JOIN conciliacion c ON c.id = e.conciliacion_id
   WHERE e.estado <> 'RESUELTA' AND c.fecha_conciliacion::date = NEW.fecha;
  SELECT count(*) INTO v_descuadres
    FROM conciliacion_custodia WHERE fecha = NEW.fecha AND estado = 'DESCUADRADA';
  IF NEW.cuadrado AND (v_excepciones > 0 OR v_descuadres > 0) THEN
    RAISE EXCEPTION 'R-BIL-12: no se puede cuadrar el % (excepciones=%, descuadres=%)',
                    NEW.fecha, v_excepciones, v_descuadres;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_cierre_diario_valido
  BEFORE INSERT OR UPDATE ON cierre_diario
  FOR EACH ROW EXECUTE FUNCTION fn_bil_validar_cierre_diario();

-- R-BIL-13 · condiciones para cerrar una billetera
CREATE OR REPLACE FUNCTION fn_bil_validar_cierre_cuenta() RETURNS trigger AS $$
DECLARE v_bloqueos INT; v_retenciones INT; v_saldo NUMERIC(16,2);
BEGIN
  IF NEW.estado <> 'CERRADA' THEN RETURN NEW; END IF;
  SELECT count(*) INTO v_bloqueos FROM bloqueo_saldo
    WHERE cuenta_billetera_id = NEW.id AND estado = 'VIGENTE';
  SELECT count(*) INTO v_retenciones FROM retencion_saldo
    WHERE cuenta_billetera_id = NEW.id AND estado = 'VIGENTE';
  v_saldo := NEW.saldo_disponible + NEW.saldo_retenido;
  IF v_bloqueos > 0 OR v_retenciones > 0 OR v_saldo <> 0 THEN
    RAISE EXCEPTION 'R-BIL-13: cuenta % no cerrable (bloqueos=%, retenciones=%, saldo=%)',
                    NEW.id, v_bloqueos, v_retenciones, v_saldo;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_cuenta_cierre_valido
  BEFORE UPDATE OF estado ON cuenta_billetera
  FOR EACH ROW EXECUTE FUNCTION fn_bil_validar_cierre_cuenta();

-- R-BIL-14 · un oficio, un bloqueo
ALTER TABLE bloqueo_saldo
  ADD CONSTRAINT uq_bloqueo_oficio UNIQUE (numero_oficio);

-- R-BIL-15 · una transacción se reversa una sola vez
CREATE UNIQUE INDEX uq_reverso_original
  ON reverso_transaccion (transaccion_original_id)
  WHERE estado <> 'RECHAZADO';
-- R-BIL-17 · cuenta de destino: unicidad por hash y una sola principal
ALTER TABLE cuenta_bancaria_beneficiario
  ADD CONSTRAINT uq_cuenta_benef_hash UNIQUE (usuario_id, hash_numero_cuenta),
  ADD CONSTRAINT ck_cuenta_benef_hash_completo CHECK (length(hash_numero_cuenta) = 64),
  ADD CONSTRAINT ck_cuenta_benef_verificada CHECK (
        estado_verificacion <> 'VERIFICADA' OR verificada_en IS NOT NULL);

CREATE UNIQUE INDEX uq_cuenta_benef_principal
  ON cuenta_bancaria_beneficiario (usuario_id)
  WHERE (es_principal);


-- ---------------------------------------------------------------------
-- R-LIM — Límites operativos
-- ---------------------------------------------------------------------

-- R-LIM-01 · denegar por omisión
CREATE OR REPLACE FUNCTION fn_lim_evaluar(
    p_cuenta UUID, p_concepto TEXT, p_monto NUMERIC) RETURNS VOID AS $$
DECLARE v_nivel TEXT; v_lim RECORD; v_acumulado NUMERIC; v_hay BOOLEAN := FALSE;
BEGIN
  SELECT nivel_debida_diligencia INTO v_nivel
    FROM cuenta_billetera WHERE id = p_cuenta;

  FOR v_lim IN
      SELECT * FROM limite_operativo_billetera
       WHERE concepto = p_concepto AND nivel_debida_diligencia = v_nivel
         AND activo AND vigente_desde <= current_date
         AND (vigente_hasta IS NULL OR vigente_hasta >= current_date)
  LOOP
    v_hay := TRUE;
    -- Si no hay fila de consumo, la variable queda en NULL y la comparación
    -- devuelve NULL: el límite dejaría de aplicarse en la primera operación de
    -- la ventana. Se inicializa en cero de forma explícita.
    v_acumulado := 0;
    -- FOR UPDATE por el mismo motivo que en fn_bil_recalcular_saldos: leer el
    -- acumulado sin bloquear la fila permite que dos operaciones simultáneas
    -- lean el mismo valor y ambas pasen el tope diario. Un límite que se evade
    -- corriendo dos veces el mismo request no es un límite.
    SELECT COALESCE(monto_acumulado, 0) INTO v_acumulado
      FROM consumo_limite
     WHERE cuenta_billetera_id = p_cuenta AND limite_id = v_lim.id
       AND now() BETWEEN ventana_inicio AND ventana_fin
       FOR UPDATE;
    v_acumulado := COALESCE(v_acumulado, 0);
    IF v_lim.monto_maximo IS NOT NULL
       AND v_acumulado + p_monto > v_lim.monto_maximo THEN
      RAISE EXCEPTION 'R-LIM-01: límite % (%) superado: disponible %',
        v_lim.concepto, v_lim.ventana, v_lim.monto_maximo - v_acumulado;
    END IF;
  END LOOP;

  IF NOT v_hay THEN
    RAISE EXCEPTION 'R-LIM-01: no hay límite configurado para % en nivel %; se deniega por omisión',
                    p_concepto, v_nivel;
  END IF;
END $$ LANGUAGE plpgsql;

-- R-LIM-02 · un consumo por ventana
ALTER TABLE consumo_limite
  ADD CONSTRAINT uq_consumo_ventana
  UNIQUE (cuenta_billetera_id, limite_id, ventana_inicio);

-- R-LIM-03 · vigencias sin solape por concepto, nivel y ventana
ALTER TABLE limite_operativo_billetera
  ADD CONSTRAINT ex_limite_vigencia
  EXCLUDE USING gist (
    concepto WITH =, nivel_debida_diligencia WITH =, ventana WITH =,
    daterange(vigente_desde, vigente_hasta, '[]') WITH &&
  ) WHERE (activo);


-- ---------------------------------------------------------------------
-- R-TAR — Tarifas, comisiones y facturación
-- ---------------------------------------------------------------------

-- R-TAR-01 · un solo tarifario vigente por código y rango
ALTER TABLE tarifario
  ADD CONSTRAINT ex_tarifario_vigente
  EXCLUDE USING gist (
    codigo WITH =,
    tstzrange(vigente_desde, vigente_hasta, '[)') WITH &&
  ) WHERE (estado = 'VIGENTE');

-- R-TAR-02 · inmutabilidad del tarifario vigente y sus conceptos
CREATE OR REPLACE FUNCTION fn_tar_tarifario_inmutable() RETURNS trigger AS $$
DECLARE v_estado TEXT;
BEGIN
  SELECT estado INTO v_estado FROM tarifario
   WHERE id = COALESCE(NEW.tarifario_id, OLD.tarifario_id);
  IF v_estado IN ('VIGENTE','SUSTITUIDO') THEN
    RAISE EXCEPTION 'R-TAR-02: el tarifario % es inmutable; cree una versión nueva', v_estado;
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_concepto_tarifa_inmutable
  BEFORE UPDATE OR DELETE ON concepto_tarifa
  FOR EACH ROW EXECUTE FUNCTION fn_tar_tarifario_inmutable();

-- R-TAR-03 · coherencia del método de cálculo
ALTER TABLE concepto_tarifa
  ADD CONSTRAINT ck_concepto_metodo CHECK (
      (metodo_calculo = 'GRATUITO')
   OR (metodo_calculo = 'FIJO'        AND valor_fijo IS NOT NULL)
   OR (metodo_calculo = 'PORCENTUAL'  AND valor_porcentual IS NOT NULL)
   OR (metodo_calculo = 'MIXTO'       AND valor_fijo IS NOT NULL
                                      AND valor_porcentual IS NOT NULL)
   OR (metodo_calculo LIKE 'ESCALONADO%')
  ),
  ADD CONSTRAINT ck_concepto_piso_techo CHECK (
      monto_minimo IS NULL OR monto_maximo IS NULL OR monto_minimo <= monto_maximo
  );

-- R-TAR-04 y R-TAR-05 · un devengo por hecho
ALTER TABLE devengo_comision
  ADD CONSTRAINT uq_devengo_hecho
  UNIQUE (referencia_tipo, referencia_id, concepto_tarifa_id);

-- R-TAR-06 · una deducción respalda un solo cargo
CREATE UNIQUE INDEX uq_cargo_deduccion
  ON cargo_comision (deduccion_entrega_id)
  WHERE deduccion_entrega_id IS NOT NULL;

-- R-TAR-07 · una tarifa congelada por grupo
ALTER TABLE tarifa_congelada_grupo
  ADD CONSTRAINT uq_tarifa_congelada_grupo UNIQUE (grupo_id);

-- R-TAR-08 · preaviso cumplido antes de entrar en vigencia
CREATE OR REPLACE FUNCTION fn_tar_validar_preaviso() RETURNS trigger AS $$
DECLARE v_cambio RECORD;
BEGIN
  IF NEW.estado <> 'VIGENTE' OR OLD.estado = 'VIGENTE' THEN RETURN NEW; END IF;
  SELECT * INTO v_cambio FROM cambio_tarifario WHERE tarifario_nuevo_id = NEW.id;
  IF v_cambio.requiere_preaviso THEN
    IF v_cambio.fecha_aviso IS NULL
       OR now() < v_cambio.fecha_aviso + (v_cambio.dias_preaviso || ' days')::interval THEN
      RAISE EXCEPTION 'R-TAR-08: preaviso de % días no cumplido', v_cambio.dias_preaviso;
    END IF;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_tarifario_preaviso
  BEFORE UPDATE OF estado ON tarifario
  FOR EACH ROW EXECUTE FUNCTION fn_tar_validar_preaviso();

-- R-TAR-09 · unicidad fiscal
ALTER TABLE factura_electronica
  ADD CONSTRAINT uq_factura_cuf UNIQUE (cuf),
  ADD CONSTRAINT uq_factura_correlativo
    UNIQUE (nit_emisor, sucursal, punto_venta, numero_factura);
ALTER TABLE nota_credito_debito ADD CONSTRAINT uq_nota_cuf UNIQUE (cuf);

-- R-TAR-10 · factura validada inmutable
CREATE OR REPLACE FUNCTION fn_tar_factura_inmutable() RETURNS trigger AS $$
BEGIN
  IF OLD.estado_fiscal = 'VALIDADA'
     AND NEW.estado_fiscal NOT IN ('ANULADA','VALIDADA') THEN
    RAISE EXCEPTION 'R-TAR-10: una factura validada solo se anula; emita nota de crédito';
  END IF;
  IF OLD.estado_fiscal = 'VALIDADA' AND NEW.monto_total <> OLD.monto_total THEN
    RAISE EXCEPTION 'R-TAR-10: no se puede modificar el monto de una factura validada';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_factura_inmutable
  BEFORE UPDATE ON factura_electronica
  FOR EACH ROW EXECUTE FUNCTION fn_tar_factura_inmutable();

-- R-TAR-11 · no devolver más de lo cobrado
CREATE OR REPLACE FUNCTION fn_tar_validar_devolucion() RETURNS trigger AS $$
DECLARE v_cobrado NUMERIC(12,2); v_devuelto NUMERIC(12,2);
BEGIN
  SELECT COALESCE(SUM(monto_cobrado),0) INTO v_cobrado
    FROM cargo_comision WHERE devengo_id = NEW.devengo_id AND estado = 'COBRADO';
  SELECT COALESCE(SUM(monto_devuelto),0) INTO v_devuelto
    FROM devolucion_comision
   WHERE devengo_id = NEW.devengo_id AND estado = 'EJECUTADA' AND id <> NEW.id;
  IF v_devuelto + NEW.monto_devuelto > v_cobrado THEN
    RAISE EXCEPTION 'R-TAR-11: devolución (%) excede lo cobrado (%)',
                    v_devuelto + NEW.monto_devuelto, v_cobrado;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_devolucion_maxima
  BEFORE INSERT OR UPDATE ON devolucion_comision
  FOR EACH ROW EXECUTE FUNCTION fn_tar_validar_devolucion();

-- R-TAR-12 · consumidor final: precio con impuestos incluidos
ALTER TABLE concepto_tarifa
  ADD CONSTRAINT ck_concepto_precio_final CHECK (
      NOT (gravado_iva AND NOT precio_incluye_impuesto
           AND sujeto_obligado IN ('BENEFICIARIO_DEL_TURNO','PAGADOR_DE_LA_OPERACION'))
  );

-- R-TAR-13 · toda factura offline bajo un evento significativo
ALTER TABLE factura_electronica
  ADD CONSTRAINT ck_factura_offline_evento CHECK (
      estado_fiscal <> 'EMITIDA_OFFLINE' OR evento_significativo_id IS NOT NULL
  );


-- ---------------------------------------------------------------------
-- R-UIF — Prevención de LGI/FT y reportes
-- ---------------------------------------------------------------------

-- R-UIF-01 · umbrales versionados con su cita normativa
ALTER TABLE umbral_reporte_uif
  ADD CONSTRAINT ck_umbral_base_normativa CHECK (length(trim(base_normativa)) > 0),
  ADD CONSTRAINT ck_umbral_ventana CHECK (
      (es_acumulado AND ventana_dias_calendario IS NOT NULL)
   OR (NOT es_acumulado AND ventana_dias_calendario IS NULL)
  );

ALTER TABLE umbral_reporte_uif
  ADD CONSTRAINT ex_umbral_vigencia
  EXCLUDE USING gist (
    formulario WITH =, concepto_operacion WITH =, es_acumulado WITH =,
    daterange(vigente_desde, vigente_hasta, '[]') WITH &&
  ) WHERE (activo);

-- R-UIF-03 y R-UIF-04 · coherencia del registro por umbral
ALTER TABLE registro_operacion_relevante
  ADD CONSTRAINT ck_operelev_ventana CHECK (
      (es_acumulada AND ventana_desde IS NOT NULL AND ventana_hasta IS NOT NULL)
   OR (NOT es_acumulada AND ventana_desde IS NULL)
  ),
  ADD CONSTRAINT ck_operelev_tipo_cambio CHECK (
      moneda = 'USD' OR tipo_cambio_aplicado > 0
  ),
  ADD CONSTRAINT ck_operelev_declaracion CHECK (
      exento
   OR formulario <> 'PCC-01'
   OR (origen_declarado IS NOT NULL AND destino_declarado IS NOT NULL)
   OR motivo_exencion IS NOT NULL
  ),
  ADD CONSTRAINT ck_operelev_periodo CHECK (periodo_remision ~ '^\d{4}-\d{2}$');

-- R-UIF-04 · conversión reproducible a dólares
CREATE OR REPLACE FUNCTION fn_fx_a_usd(p_monto NUMERIC, p_moneda CHAR(3), p_fecha DATE,
                                       OUT monto_usd NUMERIC, OUT tipo_cambio NUMERIC)
AS $$
BEGIN
  IF p_moneda = 'USD' THEN
    monto_usd := p_monto; tipo_cambio := 1; RETURN;
  END IF;
  SELECT tc.tipo_cambio INTO tipo_cambio
    FROM tipo_cambio tc
   WHERE tc.moneda_origen = p_moneda AND tc.moneda_destino = 'USD'
     AND tc.fecha <= p_fecha
   ORDER BY tc.fecha DESC LIMIT 1;
  IF tipo_cambio IS NULL THEN
    RAISE EXCEPTION 'R-UIF-04: no hay tipo de cambio % -> USD al %', p_moneda, p_fecha;
  END IF;
  monto_usd := round(p_monto * tipo_cambio, 2);
END $$ LANGUAGE plpgsql STABLE;

-- Mapeo del tipo de transacción al concepto de operación del instructivo
CREATE OR REPLACE FUNCTION fn_uif_concepto(p_tipo TEXT) RETURNS TEXT AS $$
BEGIN
  RETURN CASE p_tipo
    WHEN 'RECARGA'           THEN 'CARGA_BILLETERA'
    WHEN 'RETIRO'            THEN 'RETIRO_BILLETERA'
    WHEN 'TRANSFERENCIA_P2P' THEN 'TRANSFERENCIA_BILLETERA'
    WHEN 'APORTE_A_GRUPO'    THEN 'TRANSFERENCIA_BILLETERA'
    ELSE 'ELECTRONICA'
  END;
END $$ LANGUAGE plpgsql IMMUTABLE;

-- Titular de la operación: dueño de la cuenta debitada
CREATE OR REPLACE FUNCTION fn_uif_titular(p_transaccion UUID) RETURNS UUID AS $$
DECLARE v_usuario UUID;
BEGIN
  SELECT c.usuario_id INTO v_usuario
    FROM movimiento_billetera m
    JOIN cuenta_billetera c ON c.id = m.cuenta_billetera_id
   WHERE m.transaccion_id = p_transaccion AND c.usuario_id IS NOT NULL
   ORDER BY (m.sentido = 'DEBITO') DESC, m.orden
   LIMIT 1;
  RETURN v_usuario;
END $$ LANGUAGE plpgsql STABLE;

-- R-UIF-03 · acumulado desde el reinicio de la ventana
CREATE OR REPLACE FUNCTION fn_uif_acumulado(
    p_usuario UUID, p_umbral UUID, p_fecha DATE,
    OUT monto NUMERIC, OUT desde DATE, OUT inicio_id UUID) AS $$
DECLARE v_u RECORD; v_ultimo TIMESTAMPTZ;
BEGIN
  SELECT * INTO v_u FROM umbral_reporte_uif WHERE id = p_umbral;
  -- la ventana arranca después de la última operación que superó el umbral
  SELECT max(r.fecha_operacion) INTO v_ultimo
    FROM registro_operacion_relevante r
   WHERE r.usuario_id = p_usuario AND r.umbral_reporte_id = p_umbral;
  desde := greatest(COALESCE(v_ultimo::date + 1, p_fecha - (v_u.ventana_dias_calendario - 1)),
                    p_fecha - (v_u.ventana_dias_calendario - 1));
  -- `inicio_id` es la PRIMERA operación de la ventana en el tiempo, que es lo
  -- que el formulario tiene que citar. No se puede resolver con un agregado
  -- sobre el UUID: PostgreSQL no define min() para uuid —el error delató el
  -- problema— y aunque lo definiera, el UUID menor no es el más antiguo.
  SELECT COALESCE(sum(x.usd), 0),
         (array_agg(x.id ORDER BY x.ocurrida_en, x.secuencia))[1]
    INTO monto, inicio_id
    FROM (SELECT t.id, t.ocurrida_en, t.secuencia,
                 (fn_fx_a_usd(t.monto_total, t.moneda, t.ocurrida_en::date)).monto_usd AS usd
            FROM transaccion_billetera t
           WHERE t.estado = 'APLICADA'
             AND fn_uif_concepto(t.tipo) = v_u.concepto_operacion
             AND fn_uif_titular(t.id) = p_usuario
             AND t.ocurrida_en::date BETWEEN desde AND p_fecha) x;
END $$ LANGUAGE plpgsql STABLE;

-- R-UIF-02 · registra la operación relevante en la misma transacción del hecho
CREATE OR REPLACE FUNCTION fn_uif_registrar_operacion(p_transaccion UUID)
RETURNS INTEGER AS $$
DECLARE
  v_tx RECORD; v_u RECORD; v_usuario UUID;
  v_usd NUMERIC; v_tc NUMERIC; v_acum NUMERIC; v_desde DATE; v_inicio UUID;
  v_creados INTEGER := 0;
BEGIN
  SELECT * INTO v_tx FROM transaccion_billetera WHERE id = p_transaccion;
  IF v_tx.estado <> 'APLICADA' THEN RETURN 0; END IF;

  v_usuario := fn_uif_titular(p_transaccion);
  IF v_usuario IS NULL THEN RETURN 0; END IF;   -- operativa propia: exenta

  SELECT monto_usd, tipo_cambio INTO v_usd, v_tc
    FROM fn_fx_a_usd(v_tx.monto_total, v_tx.moneda, v_tx.ocurrida_en::date);

  FOR v_u IN
      SELECT * FROM umbral_reporte_uif
       WHERE activo
         AND concepto_operacion = fn_uif_concepto(v_tx.tipo)
         AND vigente_desde <= v_tx.ocurrida_en::date
         AND (vigente_hasta IS NULL OR vigente_hasta >= v_tx.ocurrida_en::date)
  LOOP
    IF v_u.es_acumulado THEN
      SELECT monto, desde, inicio_id INTO v_acum, v_desde, v_inicio
        FROM fn_uif_acumulado(v_usuario, v_u.id, v_tx.ocurrida_en::date);
    ELSE
      v_acum := v_usd; v_desde := NULL; v_inicio := NULL;
    END IF;

    CONTINUE WHEN v_acum < v_u.umbral_usd;

    INSERT INTO registro_operacion_relevante (
        usuario_id, transaccion_id, umbral_reporte_id, operacion_inicio_ventana_id,
        formulario, concepto_operacion, es_acumulada, ventana_desde, ventana_hasta,
        monto, moneda, monto_acumulado_ventana, tipo_cambio_aplicado,
        monto_equivalente_usd, umbral_aplicado_usd, exento,
        periodo_remision, fecha_operacion, registrada_en)
    VALUES (
        v_usuario, p_transaccion, v_u.id, v_inicio,
        v_u.formulario, v_u.concepto_operacion, v_u.es_acumulado,
        v_desde, CASE WHEN v_u.es_acumulado THEN v_tx.ocurrida_en::date END,
        v_tx.monto_total, v_tx.moneda, v_acum, v_tc,
        v_usd, v_u.umbral_usd, FALSE,
        to_char(v_tx.ocurrida_en, 'YYYY-MM'), v_tx.ocurrida_en, now())
    ON CONFLICT (transaccion_id, umbral_reporte_id) DO NOTHING;

    v_creados := v_creados + 1;
  END LOOP;
  RETURN v_creados;
END $$ LANGUAGE plpgsql;

-- R-UIF-13 · un registro por transacción y umbral (hace idempotente el motor)
ALTER TABLE registro_operacion_relevante
  ADD CONSTRAINT uq_operelev_tx_umbral UNIQUE (transaccion_id, umbral_reporte_id);

-- El motor se invoca al aplicar la transacción, cuando ya existen sus movimientos
CREATE OR REPLACE FUNCTION fn_uif_disparar_registro() RETURNS trigger AS $$
BEGIN
  PERFORM fn_uif_registrar_operacion(NEW.transaccion_id);
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER tg_movimiento_umbrales_uif
  AFTER INSERT ON movimiento_billetera
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION fn_uif_disparar_registro();

-- R-UIF-06 · reporte en cero coherente
ALTER TABLE reporte_regulatorio
  ADD CONSTRAINT ck_reporte_en_cero CHECK (
      reporte_en_cero = (cantidad_registros = 0)
  ),
  ADD CONSTRAINT uq_reporte_catalogo_periodo UNIQUE (catalogo_reporte_id, periodo);

-- R-UIF-07 · no se cierra una alerta sin conclusión
ALTER TABLE alerta_monitoreo_lft
  ADD CONSTRAINT ck_alerta_conclusion CHECK (
      estado NOT IN ('DESCARTADA','ESCALADA')
   OR (conclusion IS NOT NULL AND length(trim(conclusion)) >= 20)
  );

-- R-UIF-08 · el caso tiene plazo y revisor distinto del analista
ALTER TABLE caso_investigacion_lft
  ADD CONSTRAINT ck_caso_plazo CHECK (plazo_limite > abierto_en),
  ADD CONSTRAINT ck_caso_revision CHECK (
      revisado_por IS NULL OR revisado_por <> analista_id
  ),
  ADD CONSTRAINT ck_caso_reporte CHECK (
      decision <> 'REPORTAR' OR reporte_operacion_sospechosa_id IS NOT NULL
  );

-- R-UIF-09 · no operar sin diligencia vigente
CREATE OR REPLACE FUNCTION fn_uif_exigir_ddd(p_usuario UUID) RETURNS VOID AS $$
DECLARE v_ok BOOLEAN;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM debida_diligencia
     WHERE usuario_id = p_usuario AND estado = 'COMPLETA'
       AND (vence_en IS NULL OR vence_en > now())
  ) INTO v_ok;
  IF NOT v_ok THEN
    RAISE EXCEPTION 'R-UIF-09: el cliente no tiene debida diligencia vigente';
  END IF;
END $$ LANGUAGE plpgsql;

-- R-UIF-10 · PEP con diligencia reforzada y cuatro ojos
CREATE OR REPLACE FUNCTION fn_uif_validar_pep() RETURNS trigger AS $$
DECLARE v_pep BOOLEAN;
BEGIN
  SELECT COALESCE(bool_or(es_pep), FALSE) INTO v_pep
    FROM declaracion_pep
   WHERE usuario_id = NEW.usuario_id AND (hasta IS NULL OR hasta >= current_date);
  IF v_pep THEN
    IF NEW.tipo <> 'REFORZADA' THEN
      RAISE EXCEPTION 'R-UIF-10: un PEP exige debida diligencia REFORZADA';
    END IF;
    IF NEW.estado = 'COMPLETA'
       AND (NEW.segunda_revision_por IS NULL
            OR NEW.segunda_revision_por = NEW.aprobada_por) THEN
      RAISE EXCEPTION 'R-UIF-10: falta segunda revisión independiente';
    END IF;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_ddd_pep
  BEFORE INSERT OR UPDATE ON debida_diligencia
  FOR EACH ROW EXECUTE FUNCTION fn_uif_validar_pep();

-- R-UIF-11 · una calificación vigente por cliente
ALTER TABLE calificacion_riesgo_cliente
  ADD CONSTRAINT ex_calificacion_vigente
  EXCLUDE USING gist (
    usuario_id WITH =,
    tstzrange(vigente_desde, vigente_hasta, '[)') WITH &&
  );
-- R-UIF-12 · un titular activo por vez, y la baja exige fecha
CREATE UNIQUE INDEX uq_oficial_titular_activo
  ON oficial_cumplimiento ((tipo))
  WHERE (activo AND tipo = 'TITULAR');

ALTER TABLE oficial_cumplimiento
  ADD CONSTRAINT ck_oficial_baja_coherente CHECK (
        (activo AND fecha_baja IS NULL) OR (NOT activo AND fecha_baja IS NOT NULL)),
  ADD CONSTRAINT ck_oficial_baja_posterior CHECK (
        fecha_baja IS NULL OR fecha_baja >= fecha_designacion);


-- ---------------------------------------------------------------------
-- R-CON — Consumidor financiero
-- ---------------------------------------------------------------------

-- R-CON-01 · plazo guardado, nunca recalculado
ALTER TABLE reclamo_cliente
  ADD CONSTRAINT ck_reclamo_plazo CHECK (plazo_respuesta > fecha_ingreso),
  ADD CONSTRAINT ck_reclamo_dias CHECK (dias_habiles_plazo BETWEEN 1 AND 5),
  ADD CONSTRAINT uq_reclamo_codigo UNIQUE (codigo);

-- R-CON-02 y R-CON-03 · prórroga acotada y comunicada
ALTER TABLE reclamo_cliente
  ADD CONSTRAINT ck_reclamo_prorroga CHECK (
      plazo_prorrogado_hasta IS NULL
   OR (plazo_prorrogado_hasta > plazo_respuesta
       AND prorroga_comunicada_al_cliente_en IS NOT NULL
       AND prorroga_comunicada_al_cliente_en <= plazo_respuesta)
  ),
  ADD CONSTRAINT ck_reclamo_prorroga_extendida CHECK (
      plazo_prorrogado_hasta IS NULL
   OR plazo_prorrogado_hasta <= fecha_ingreso + interval '10 days'
   OR (prorroga_comunicada_al_organismo_en IS NOT NULL
       AND justificacion_prorroga IS NOT NULL)
  );

-- R-CON-04 · un reclamo favorable con monto exige reparación
ALTER TABLE reclamo_cliente
  ADD CONSTRAINT ck_reclamo_reparacion CHECK (
      estado <> 'CERRADO'
   OR resultado <> 'FAVORABLE'
   OR monto_reclamado IS NULL
   OR devolucion_comision_id IS NOT NULL
  );

-- R-CON-05 · conservación
ALTER TABLE reclamo_cliente
  ADD CONSTRAINT ck_reclamo_conservacion CHECK (
      conservar_hasta >= (fecha_ingreso + interval '10 years')::date
  );

-- R-CON-06 · no operar sin contrato aceptado
CREATE OR REPLACE FUNCTION fn_con_exigir_contrato(p_usuario UUID, p_tipo TEXT)
RETURNS VOID AS $$
DECLARE v_ok BOOLEAN;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM aceptacion_contrato a
      JOIN contrato_adhesion c ON c.id = a.contrato_adhesion_id
     WHERE a.usuario_id = p_usuario AND c.tipo = p_tipo
       AND a.version_aceptada = c.version AND c.estado = 'VIGENTE'
  ) INTO v_ok;
  IF NOT v_ok THEN
    RAISE EXCEPTION 'R-CON-06: falta aceptación vigente del contrato %', p_tipo;
  END IF;
END $$ LANGUAGE plpgsql;

-- R-CON-07 · tarifario publicado
ALTER TABLE tarifario
  ADD CONSTRAINT ck_tarifario_publicado CHECK (
      estado <> 'VIGENTE'
   OR (publicado_en IS NOT NULL AND url_publicacion IS NOT NULL
       AND hash_documento IS NOT NULL)
  );

-- R-CON-08 · extractos con integridad verificable
ALTER TABLE estado_cuenta_billetera
  ADD CONSTRAINT ck_extracto_hash CHECK (length(hash_archivo) = 64),
  ADD CONSTRAINT ck_extracto_cuadra CHECK (
      saldo_final = saldo_inicial + total_creditos - total_debitos
  );


-- ---------------------------------------------------------------------
-- R-SEG — Seguridad y datos personales
-- ---------------------------------------------------------------------

-- R-SEG-01 · solo hash, token y enmascarado
--
-- El largo 64 fija SHA-256 hexadecimal, pero el algoritmo no es lo que protege
-- aquí: lo que protege es que la entrada NO sea adivinable. Un CI boliviano son
-- ~10⁷ valores; un PAN con BIN conocido, ~10⁶; un número de cuenta, menos. La
-- tabla completa de digests se precalcula en segundos. Un `digest()` desnudo
-- sobre esos campos anula el cifrado de la columna de al lado, porque el hash
-- de búsqueda revela exactamente el dato que el cifrado protegía.
--
-- Por eso el hash de búsqueda es SIEMPRE un HMAC con una pimienta que vive
-- fuera de la base (KMS o variable de entorno del proceso), nunca un digest
-- directo. `fn_seg_hash_busqueda` es el único camino permitido, y falla si la
-- pimienta no está configurada: denegar por omisión también aquí.
CREATE OR REPLACE FUNCTION fn_seg_hash_busqueda(p_valor TEXT) RETURNS TEXT AS $$
DECLARE v_pimienta TEXT;
BEGIN
  v_pimienta := current_setting('app.pimienta_busqueda', true);
  IF v_pimienta IS NULL OR length(v_pimienta) < 32 THEN
    RAISE EXCEPTION
      'R-SEG-01: falta app.pimienta_busqueda; el hash de búsqueda sin pimienta es reversible';
  END IF;
  RETURN encode(hmac(p_valor, v_pimienta, 'sha256'), 'hex');
END $$ LANGUAGE plpgsql;

ALTER TABLE instrumento_fondeo
  ADD CONSTRAINT ck_instrumento_sin_pan CHECK (
      enmascarado !~ '[0-9]{9,}' AND length(hash_identificador) = 64
  );
ALTER TABLE cuenta_bancaria_beneficiario
  ADD CONSTRAINT ck_cuenta_bancaria_sin_claro CHECK (
      numero_enmascarado !~ '[0-9]{9,}' AND length(hash_numero_cuenta) = 64
  );
ALTER TABLE documento_identidad
  ADD CONSTRAINT ck_documento_hash_completo CHECK (length(hash_numero) = 64);

-- R-SEG-01b · todo texto cifrado dice con qué llave se cifró
--
-- Sin versión de llave, rotar exige descifrar y recifrar el universo entero en
-- una sola ventana atómica. En la práctica eso significa no rotar nunca, que es
-- el hallazgo estándar de toda auditoría. Con la versión al lado, conviven dos
-- generaciones y la rotación es incremental.
ALTER TABLE documento_identidad
  ADD CONSTRAINT ck_documento_version_llave CHECK (version_llave >= 1);
ALTER TABLE cuenta_bancaria_beneficiario
  ADD CONSTRAINT ck_cuenta_bancaria_version_llave CHECK (version_llave >= 1);
ALTER TABLE factor_mfa
  ADD CONSTRAINT ck_factor_mfa_version_llave CHECK (version_llave >= 1);
ALTER TABLE cuenta_custodia
  ADD CONSTRAINT ck_cuenta_custodia_version_llave CHECK (version_llave >= 1);
ALTER TABLE exportacion_reporte
  ADD CONSTRAINT ck_exportacion_version_llave CHECK (version_llave >= 1);

-- R-SEG-02 · el acceso a datos sensibles exige justificación
--
-- La columna es NOT NULL a propósito: un CHECK que sólo compara longitudes se
-- satisface con NULL, porque `length(trim(NULL)) >= 10` evalúa a NULL y un
-- CHECK que evalúa a NULL se acepta. La restricción se saltaba dejando el campo
-- vacío. El `IS NOT NULL` explícito es el que hace el trabajo.
ALTER TABLE registro_acceso_datos
  ADD CONSTRAINT ck_acceso_justificacion
  CHECK (justificacion IS NOT NULL AND length(btrim(justificacion)) >= 10);

-- R-SEG-03 · seguridad a nivel de fila
--
-- Tres condiciones tienen que cumplirse a la vez para que la RLS sirva de algo,
-- y las tres se olvidan seguido:
--
--   1) FORCE, no sólo ENABLE. El dueño de la tabla omite las políticas siempre.
--      Si la API se conecta como dueña del esquema, ENABLE no protege nada.
--   2) El juego completo de comandos. Una política sólo de SELECT deja la
--      escritura sobre filas ajenas exactamente igual de abierta que antes.
--   3) La app NO es dueña del esquema: se conecta como rol_aplicacion, que
--      recibe privilegios explícitos más abajo.
--
-- `app.usuario_id` y `app.rol` los fija la aplicación con SET LOCAL dentro de la
-- transacción. SET LOCAL y no SET: con pooling, un SET a secas sobrevive a la
-- devolución de la conexión y el siguiente request hereda la identidad del
-- anterior. Ver [[ADR-021 Sesión, RLS y pooling]].
CREATE OR REPLACE FUNCTION fn_seg_usuario_actual() RETURNS UUID AS $$
  SELECT NULLIF(current_setting('app.usuario_id', true), '')::uuid;
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION fn_seg_rol_privilegiado() RETURNS BOOLEAN AS $$
  SELECT COALESCE(current_setting('app.rol', true), '')
         IN ('BACKOFFICE','CUMPLIMIENTO','AUDITOR');
$$ LANGUAGE sql STABLE;

-- La cobertura NO se escribe a mano. Una lista de tablas escrita a mano se
-- desactualiza en el primer módulo nuevo, y una tabla olvidada no falla: queda
-- abierta en silencio, que es la peor forma de fallar. El recorrido va sobre el
-- catálogo: toda tabla que tenga `usuario_id` o `cuenta_billetera_id` recibe
-- política, hoy y cuando se agregue la próxima.
--
-- Hay dos regímenes, y la diferencia importa. El usuario ve sus datos de
-- identidad, su billetera y sus operaciones. Lo que NO puede ver jamás es lo que
-- el área de cumplimiento escribió sobre él: una alerta de monitoreo, un caso de
-- investigación, una coincidencia con lista restrictiva, su calificación de
-- riesgo. Darle acceso a su propia fila ahí no es una fuga de privacidad, es un
-- delito: se llama soplo, y la Ley 393 y la UIF lo prohíben expresamente. Por
-- eso el reparto es por lista blanca y todo lo demás cae en privilegiado.
CREATE OR REPLACE FUNCTION fn_seg_aplicar_rls() RETURNS VOID AS $$
DECLARE
  r RECORD;
  visibles_por_titular TEXT[] := ARRAY[
      'documento_identidad','direccion_usuario','perfil_financiero',
      'credencial_acceso','historial_credencial','factor_mfa','dispositivo',
      'sesion','token_verificacion','consentimiento','preferencia_notificacion',
      'referencia_personal','solicitud_baja','verificacion_kyc',
      'reputacion_usuario','cuenta_billetera','cuenta_bancaria_beneficiario',
      'instrumento_fondeo','respuesta_idempotente','reclamo_cliente',
      'solicitud_datos_personales','notificacion','aceptacion_contrato',
      'datos_facturacion','canal_vinculado','bandeja_entrada',
      'certificado_reputacion','insignia_otorgada','declaracion_origen_fondos'];
  cond TEXT;
BEGIN
  -- Se recorren TODOS los esquemas de servicio, no `public`. Decia
  -- `n.nspname = 'public'` y se escribio antes de que ADR-017 partiera el modelo en
  -- catorce esquemas: desde entonces no encontraba ni una tabla, y las 86 que
  -- llevan usuario_id o cuenta_billetera_id quedaban SIN politica de fila. No
  -- fallaba nada: el invariante 3 simplemente no estaba en vigor.
  FOR r IN
      SELECT n.nspname AS esq,
             c.relname AS t,
             EXISTS (SELECT 1 FROM pg_attribute a
                      WHERE a.attrelid = c.oid AND a.attname = 'usuario_id'
                        AND NOT a.attisdropped) AS por_usuario
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
       WHERE n.nspname NOT IN ('pg_catalog','information_schema','pg_toast')
         AND n.nspname NOT LIKE 'pg_temp%'
         AND c.relkind IN ('r','p')
         AND EXISTS (SELECT 1 FROM pg_attribute a
                      WHERE a.attrelid = c.oid AND NOT a.attisdropped
                        AND a.attname IN ('usuario_id','cuenta_billetera_id'))
  LOOP
    IF NOT (r.t = ANY (visibles_por_titular)) THEN
      cond := 'fn_seg_rol_privilegiado()';          -- denegar por omisión
    ELSIF r.por_usuario THEN
      cond := 'usuario_id = fn_seg_usuario_actual() OR fn_seg_rol_privilegiado()';
    ELSE
      -- La billetera vive en nucleo_financiero, no en el esquema de la tabla que la
      -- referencia: la subconsulta se califica o no resuelve.
      cond := format('fn_seg_rol_privilegiado() OR EXISTS ('
                     'SELECT 1 FROM nucleo_financiero.cuenta_billetera c '
                     'WHERE c.id = %I.%I.cuenta_billetera_id '
                     'AND c.usuario_id = fn_seg_usuario_actual())', r.esq, r.t);
    END IF;

    EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', r.esq, r.t);
    EXECUTE format('ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY', r.esq, r.t);
    EXECUTE format('DROP POLICY IF EXISTS pol_%s_titular ON %I.%I', r.t, r.esq, r.t);
    EXECUTE format(
      'CREATE POLICY pol_%s_titular ON %I.%I FOR ALL TO rol_aplicacion '
      'USING (%s) WITH CHECK (%s)', r.t, r.esq, r.t, cond, cond);
  END LOOP;
END $$ LANGUAGE plpgsql;

SELECT fn_seg_aplicar_rls();

-- La propia tabla de usuarios: acá el dueño es la clave primaria, no usuario_id.
ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario FORCE ROW LEVEL SECURITY;
CREATE POLICY pol_usuario_titular ON usuario
  FOR ALL TO rol_aplicacion
  USING (id = fn_seg_usuario_actual() OR fn_seg_rol_privilegiado())
  WITH CHECK (id = fn_seg_usuario_actual() OR fn_seg_rol_privilegiado());

-- Las tablas de cumplimiento que NO llevan usuario_id quedan igualmente fuera
-- del alcance de la aplicación: sólo cumplimiento y auditoría las leen.
DO $$
DECLARE t TEXT;
BEGIN
  -- `reporte_operacion_sospechosa` y `registro_operacion_relevante` NO van acá:
  -- llevan usuario_id, así que el recorrido de arriba ya les puso política
  -- privilegiada. Repetirlas crearía dos políticas permisivas sobre la misma
  -- tabla, que PostgreSQL combina con OR y sólo sirven para confundir a quien
  -- audite después.
  FOREACH t IN ARRAY ARRAY[
      'regla_monitoreo_lft','matriz_riesgo_lft','lista_restrictiva_externa',
      'registro_acceso_datos','requerimiento_autoridad'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY pol_%s_reservado ON %I FOR ALL TO rol_aplicacion '
      'USING (fn_seg_rol_privilegiado()) WITH CHECK (fn_seg_rol_privilegiado())',
      t, t);
  END LOOP;
END $$;

-- R-SEG-04 · cuatro ojos donde importa
ALTER TABLE entrega_fondo
  ADD CONSTRAINT ck_entrega_segregacion CHECK (
      autorizada_por IS NULL OR ejecutada_por IS NULL
   OR autorizada_por <> ejecutada_por
  );
ALTER TABLE reverso_transaccion
  ADD CONSTRAINT ck_reverso_segregacion CHECK (autorizada_por IS NOT NULL);
ALTER TABLE reporte_regulatorio
  ADD CONSTRAINT ck_reporte_segregacion CHECK (
      estado <> 'ENVIADO'
   OR (aprobado_por IS NOT NULL AND aprobado_por <> generado_por)
  );

-- El retiro es la salida de dinero de mayor riesgo del sistema y era la única
-- sin segregación exigible: `requiere_doble_aprobacion` existía sin ninguna
-- restricción que lo hiciera valer, y la tabla ni siquiera guardaba quién había
-- solicitado la orden, así que no había con qué comparar al aprobador.
ALTER TABLE orden_retiro
  ADD CONSTRAINT ck_retiro_doble_aprobacion CHECK (
      NOT requiere_doble_aprobacion
   OR estado IN ('BORRADOR','PENDIENTE','RECHAZADA')
   OR (aprobada_por IS NOT NULL AND aprobada_por <> solicitada_por)
  );

-- R-SEG-09 · el refresco se rota, y reusarlo revoca la familia entera
--
-- Un token de refresco consumido que vuelve a presentarse es la firma de un
-- robo: el legítimo y el ladrón tienen el mismo token y uno lo usó después del
-- otro. No se sabe cuál es cuál, así que no alcanza con rechazar el segundo
-- intento: hay que invalidar la familia completa y cortar las sesiones que
-- colgaban de ella. Sin esto, la rotación es decorativa.
--
-- El disparador no lanza excepción a propósito: si lo hiciera, la propia
-- revocación se iría en el ROLLBACK. Marca el token como INVALIDADO y propaga;
-- la aplicación ve que no obtuvo un token vivo y responde 401.
ALTER TABLE token_verificacion
  ADD CONSTRAINT ck_token_refresco_familia CHECK (
      tipo_token <> 'REFRESCO' OR familia_id IS NOT NULL);

CREATE UNIQUE INDEX uq_token_refresco_vivo
  ON token_verificacion (familia_id)
  WHERE (tipo_token = 'REFRESCO' AND estado = 'EMITIDO');

CREATE OR REPLACE FUNCTION fn_seg_detectar_reuso_refresco() RETURNS trigger AS $$
BEGIN
  IF NEW.tipo_token <> 'REFRESCO' OR NEW.estado <> 'CONSUMIDO' THEN
    RETURN NEW;
  END IF;
  IF OLD.estado = 'EMITIDO' THEN
    RETURN NEW;                      -- rotación normal
  END IF;

  NEW.estado := 'INVALIDADO';
  NEW.invalidado_en := now();
  NEW.motivo_invalidacion := 'R-SEG-09: reuso de token de refresco';

  UPDATE token_verificacion
     SET estado = 'INVALIDADO', invalidado_en = now(),
         motivo_invalidacion = 'R-SEG-09: reuso en la familia'
   WHERE familia_id = NEW.familia_id AND id <> NEW.id AND estado = 'EMITIDO';

  UPDATE sesion
     SET revocada_en = now(),
         motivo_revocacion = 'R-SEG-09: reuso de token de refresco'
   WHERE refresco_familia_id = NEW.familia_id AND revocada_en IS NULL;

  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_token_reuso_refresco
  BEFORE UPDATE OF estado ON token_verificacion
  FOR EACH ROW EXECUTE FUNCTION fn_seg_detectar_reuso_refresco();

-- R-SEG-05 · plazo de reporte guardado
ALTER TABLE incidente_seguridad
  ADD CONSTRAINT ck_incidente_plazo CHECK (plazo_reporte > detectado_en),
  ADD CONSTRAINT ck_incidente_notificacion CHECK (
      NOT datos_personales_afectados
   OR estado <> 'CERRADO'
   OR notificado_a_titulares_en IS NOT NULL
  );

-- R-SEG-06 · no anonimizar antes de tiempo
CREATE OR REPLACE FUNCTION fn_seg_validar_anonimizacion() RETURNS trigger AS $$
DECLARE v_retencion DATE;
BEGIN
  SELECT retencion_hasta INTO v_retencion
    FROM expediente_cliente WHERE usuario_id = NEW.usuario_id;
  IF NEW.estrategia = 'BORRADO_FISICO' AND v_retencion > current_date THEN
    RAISE EXCEPTION 'R-SEG-06: retención legal vigente hasta %; use seudonimización', v_retencion;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_anonimizacion_retencion
  BEFORE INSERT OR UPDATE ON proceso_anonimizacion
  FOR EACH ROW EXECUTE FUNCTION fn_seg_validar_anonimizacion();
-- R-SEG-07 · nadie se amplía sus propios permisos
ALTER TABLE asignacion_rol
  ADD CONSTRAINT ck_asignacion_no_autoasignada CHECK (usuario_id <> otorgada_por),
  ADD CONSTRAINT ck_asignacion_ambito_completo CHECK (
        (ambito = 'GRUPO' AND ambito_id IS NOT NULL)
     OR (ambito <> 'GRUPO' AND ambito_id IS NULL)),
  ADD CONSTRAINT ck_asignacion_revocacion_motivada CHECK (
        revocada_en IS NULL OR motivo_revocacion IS NOT NULL);

-- R-SEG-08 · una sola asignación viva por usuario, rol y ámbito
CREATE UNIQUE INDEX uq_asignacion_vigente
  ON asignacion_rol (usuario_id, rol_id, ambito, COALESCE(ambito_id, '00000000-0000-0000-0000-000000000000'::uuid))
  WHERE (revocada_en IS NULL);

CREATE INDEX ix_asignacion_por_vencer
  ON asignacion_rol (vigente_hasta)
  WHERE (revocada_en IS NULL AND vigente_hasta IS NOT NULL);

-- R-SEG-10 · el operador entra con dos factores, siempre, y el segundo no es un mensaje
--
-- CU-04 exime del segundo factor al dispositivo ya confiable. Para el participante
-- es una comodidad razonable: lo que arriesga es lo suyo. Para quien tiene un rol de
-- ámbito GLOBAL —cumplimiento, tesorería, contabilidad, soporte, administración— esa
-- exención convierte el robo del equipo en el robo del rol, y el rol da acceso a la
-- plata y a los datos de terceros. Por eso acá no hay dispositivo de confianza que
-- valga: sin factor confirmado, no hay sesión ([[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]]).
--
-- El criterio es el ámbito del ROL, no el de la asignación: una asignación mal
-- cargada no puede convertir a un participante en operador ni al revés.
--
-- Y el factor tiene que ser TOTP. SMS y WhatsApp son canales apagados
-- ([[ADR-035 Canales por defecto]]) y el intercambio de SIM es el ataque barato
-- contra una cuenta privilegiada; `RESPALDO` acompaña al TOTP, no lo reemplaza.
-- La condición «es operador» va copiada en los tres disparadores en vez de
-- factorizada en una función. No es descuido: una función auxiliar se crea en el
-- primer esquema del `search_path` de quien aplica el archivo, y el disparador la
-- resolvería en tiempo de ejecución contra el `search_path` del servicio que
-- escribe —que no tiene por qué incluir ese esquema—. Una restricción que depende
-- de la configuración de la conexión no es una restricción.
CREATE OR REPLACE FUNCTION fn_seg_sesion_operador_exige_mfa() RETURNS trigger AS $$
BEGIN
  IF NOT EXISTS (
        SELECT 1
          FROM asignacion_rol ar
          JOIN rol r ON r.id = ar.rol_id
         WHERE ar.usuario_id = NEW.usuario_id
           AND ar.revocada_en IS NULL
           AND (ar.vigente_hasta IS NULL OR ar.vigente_hasta > now())
           AND r.ambito = 'GLOBAL') THEN
    RETURN NEW;
  END IF;
  IF NOT EXISTS (
        SELECT 1 FROM factor_mfa f
         WHERE f.usuario_id = NEW.usuario_id
           AND f.tipo = 'TOTP'
           AND f.activo
           AND f.confirmado_en IS NOT NULL) THEN
    RAISE EXCEPTION
      'R-SEG-10: el usuario % tiene rol operativo y no tiene segundo factor TOTP confirmado; no se abre sesión',
      NEW.usuario_id;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_sesion_operador_mfa
  BEFORE INSERT ON sesion
  FOR EACH ROW EXECUTE FUNCTION fn_seg_sesion_operador_exige_mfa();

CREATE OR REPLACE FUNCTION fn_seg_factor_operador_valido() RETURNS trigger AS $$
BEGIN
  IF NEW.tipo IN ('SMS', 'WHATSAPP') AND EXISTS (
        SELECT 1
          FROM asignacion_rol ar
          JOIN rol r ON r.id = ar.rol_id
         WHERE ar.usuario_id = NEW.usuario_id
           AND ar.revocada_en IS NULL
           AND (ar.vigente_hasta IS NULL OR ar.vigente_hasta > now())
           AND r.ambito = 'GLOBAL') THEN
    RAISE EXCEPTION
      'R-SEG-10: % no es un segundo factor admisible para un usuario con rol operativo; use TOTP',
      NEW.tipo;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_factor_operador_valido
  BEFORE INSERT OR UPDATE ON factor_mfa
  FOR EACH ROW EXECUTE FUNCTION fn_seg_factor_operador_valido();

-- R-SEG-11 · cambiar la clave de un operador no deja nada vivo detrás
--
-- CU-09 conserva la sesión que hizo el cambio, y para el titular de una billetera
-- eso está bien: acaba de probar que es él. Para un operador no alcanza, porque el
-- caso que importa es el contrario —el atacante cambió la clave— y ahí la sesión
-- conservada es la del atacante. Se cae todo: sesiones, confianza de los
-- dispositivos y refrescos emitidos. Volver a entrar cuesta un TOTP; no volver a
-- entrar le cuesta a la plataforma la base de clientes.
CREATE OR REPLACE FUNCTION fn_seg_credencial_operador_corta_sesiones() RETURNS trigger AS $$
BEGIN
  IF NEW.hash_contrasena = OLD.hash_contrasena THEN
    RETURN NEW;
  END IF;
  IF NOT EXISTS (
        SELECT 1
          FROM asignacion_rol ar
          JOIN rol r ON r.id = ar.rol_id
         WHERE ar.usuario_id = NEW.usuario_id
           AND ar.revocada_en IS NULL
           AND (ar.vigente_hasta IS NULL OR ar.vigente_hasta > now())
           AND r.ambito = 'GLOBAL') THEN
    RETURN NEW;
  END IF;

  UPDATE sesion
     SET revocada_en = now(),
         motivo_revocacion = 'R-SEG-11: credencial de operador cambiada'
   WHERE usuario_id = NEW.usuario_id AND revocada_en IS NULL;

  UPDATE dispositivo
     SET es_confiable = false
   WHERE usuario_id = NEW.usuario_id AND es_confiable;

  UPDATE token_verificacion
     SET estado = 'INVALIDADO', invalidado_en = now(),
         motivo_invalidacion = 'R-SEG-11: credencial de operador cambiada'
   WHERE usuario_id = NEW.usuario_id
     AND tipo_token = 'REFRESCO' AND estado = 'EMITIDO';

  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_credencial_operador_corta_sesiones
  AFTER UPDATE OF hash_contrasena ON credencial_acceso
  FOR EACH ROW EXECUTE FUNCTION fn_seg_credencial_operador_corta_sesiones();

-- R-SEG-12 · lo irreversible se confirma con el segundo factor
--
-- `permiso.requiere_mfa` existía y nadie garantizaba que estuviera puesto donde
-- corresponde: aprobar una campaña publicitaria —que compromete gasto y publica
-- contenido— estaba marcado en `false`. La regla no exige factor para todo lo que
-- escribe: exigirlo en cada acción del día produce fatiga y la fatiga produce el
-- clic automático. Lo exige donde la decisión no se deshace o donde se leen datos
-- de un tercero.
ALTER TABLE permiso
  ADD CONSTRAINT ck_permiso_decision_exige_mfa CHECK (
      requiere_mfa
   OR accion NOT IN ('AUTORIZAR', 'APROBAR', 'EJECUTAR', 'REVERSAR',
                     'PUBLICAR', 'ENVIAR', 'CERRAR', 'LEER_TERCEROS')
  );


-- ---------------------------------------------------------------------
-- R-LIC — Licencia y gobierno
-- ---------------------------------------------------------------------

-- R-LIC-01 · alcance autorizado como condición de servicio
CREATE OR REPLACE FUNCTION fn_lic_servicio_habilitado(p_servicio TEXT)
RETURNS BOOLEAN AS $$
DECLARE v_ok BOOLEAN;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM licencia_regulatoria
     WHERE estado = 'OTORGADA'
       AND (vigente_hasta IS NULL OR vigente_hasta >= current_date)
       AND alcance_autorizado @> to_jsonb(ARRAY[p_servicio])
  ) OR EXISTS (
    SELECT 1 FROM entorno_prueba_regulado
     WHERE estado = 'ACTIVO' AND servicio_en_prueba = p_servicio
       AND current_date BETWEEN fecha_inicio AND fecha_fin
  ) INTO v_ok;
  RETURN v_ok;
END $$ LANGUAGE plpgsql;

-- R-LIC-02 · el sandbox tiene límites obligatorios
ALTER TABLE entorno_prueba_regulado
  ADD CONSTRAINT ck_sandbox_limites CHECK (
      estado <> 'ACTIVO'
   OR (limite_usuarios IS NOT NULL AND limite_monto_operacion IS NOT NULL
       AND fecha_fin > fecha_inicio)
  );

-- R-LIC-03 · política vigente exige acta
ALTER TABLE politica_interna
  ADD CONSTRAINT ck_politica_acta CHECK (
      estado <> 'VIGENTE'
   OR (aprobada_por_directorio AND acta_comite_id IS NOT NULL)
  ),
  ADD CONSTRAINT ck_politica_revision CHECK (proxima_revision > vigente_desde);
-- R-LIC-04 · no objeción previa cuando la norma la exige
ALTER TABLE evaluacion_riesgo_producto
  ADD CONSTRAINT uq_evaluacion_producto_version UNIQUE (producto, version),
  ADD CONSTRAINT ck_evaluacion_no_objecion CHECK (
        estado <> 'VIGENTE'
     OR NOT requiere_no_objecion
     OR (fecha_aprobacion IS NOT NULL AND aprobada_por IS NOT NULL)),
  ADD CONSTRAINT ck_evaluacion_vigente_aprobada CHECK (
        estado <> 'VIGENTE' OR fecha_aprobacion IS NOT NULL);


-- ---------------------------------------------------------------------
-- R-GRP — Circuito del pasanaku
-- ---------------------------------------------------------------------

-- R-GRP-01 · una entrega por turno y por período
ALTER TABLE entrega_fondo
  ADD CONSTRAINT uq_entrega_turno UNIQUE (turno_id),
  ADD CONSTRAINT uq_entrega_periodo UNIQUE (periodo_id);

-- R-GRP-02 · aritmética de la liquidación
ALTER TABLE entrega_fondo
  ADD CONSTRAINT ck_entrega_neto CHECK (
      monto_neto_a_entregar = monto_bolsa_bruto - total_deducciones
  ),
  ADD CONSTRAINT ck_entrega_neto_no_negativo CHECK (monto_neto_a_entregar >= 0);

CREATE OR REPLACE FUNCTION fn_grp_recalcular_deducciones() RETURNS trigger AS $$
DECLARE v_total NUMERIC(14,2); v_entrega UUID;
BEGIN
  v_entrega := COALESCE(NEW.entrega_id, OLD.entrega_id);
  SELECT COALESCE(SUM(monto),0) INTO v_total
    FROM deduccion_entrega
   WHERE entrega_id = v_entrega AND revertida_en IS NULL;
  UPDATE entrega_fondo
     SET total_deducciones = v_total,
         monto_neto_a_entregar = monto_bolsa_bruto - v_total
   WHERE id = v_entrega;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_deduccion_recalcula
  AFTER INSERT OR UPDATE OR DELETE ON deduccion_entrega
  FOR EACH ROW EXECUTE FUNCTION fn_grp_recalcular_deducciones();

-- R-GRP-03 · una obligación periódica por cupo
CREATE UNIQUE INDEX uq_obligacion_periodo_cupo
  ON obligacion_aporte (periodo_id, cupo_id)
  WHERE tipo = 'APORTE_PERIODICO' AND estado <> 'ANULADO';

-- R-GRP-05 · un solo sorteo por grupo; el compromiso es inmutable
ALTER TABLE sorteo_turnos
  ADD CONSTRAINT uq_sorteo_grupo UNIQUE (grupo_id),
  ADD CONSTRAINT ck_sorteo_revelado CHECK (
      estado <> 'REVELADO'
   OR (semilla_servidor IS NOT NULL AND fecha_ejecucion IS NOT NULL)
  ),
  ADD CONSTRAINT ck_sorteo_compromiso CHECK (
      length(hash_semilla_previo) = 64 AND fecha_compromiso IS NOT NULL
  );

-- El compromiso no se reescribe: cambiarlo después de publicarlo destruye toda
-- la garantía del esquema commit-reveal.
CREATE OR REPLACE FUNCTION fn_grp_compromiso_inmutable() RETURNS trigger AS $$
BEGIN
  IF NEW.hash_semilla_previo IS DISTINCT FROM OLD.hash_semilla_previo
     OR NEW.fecha_compromiso IS DISTINCT FROM OLD.fecha_compromiso THEN
    RAISE EXCEPTION 'R-GRP-05: el compromiso del sorteo es inmutable';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_sorteo_compromiso_inmutable
  BEFORE UPDATE ON sorteo_turnos
  FOR EACH ROW EXECUTE FUNCTION fn_grp_compromiso_inmutable();

-- R-GRP-06 · un turno por período y un orden único por grupo
ALTER TABLE turno
  ADD CONSTRAINT uq_turno_periodo UNIQUE (periodo_id),
  ADD CONSTRAINT uq_turno_orden UNIQUE (grupo_id, orden_asignado);

-- R-GRP-07 · lo ya cobrado no se reordena
CREATE OR REPLACE FUNCTION fn_grp_validar_permuta() RETURNS trigger AS $$
DECLARE v_estados TEXT[];
BEGIN
  SELECT array_agg(estado) INTO v_estados
    FROM turno WHERE id IN (NEW.turno_origen_id, NEW.turno_destino_id);
  IF v_estados && ARRAY['COBRADO','EN_CURSO','ANULADO'] THEN
    RAISE EXCEPTION 'R-GRP-07: solo se permutan turnos PROGRAMADOS';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_permuta_valida
  BEFORE INSERT ON solicitud_permuta
  FOR EACH ROW EXECUTE FUNCTION fn_grp_validar_permuta();

-- R-GRP-08 · un voto por participante y acuerdo, sin cambios
ALTER TABLE voto_participante
  ADD CONSTRAINT uq_voto_acuerdo_participante UNIQUE (acuerdo_id, participante_id);

CREATE OR REPLACE FUNCTION fn_grp_voto_inmutable() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'R-GRP-08: el voto emitido no se modifica ni se borra';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_voto_inmutable
  BEFORE UPDATE OR DELETE ON voto_participante
  FOR EACH ROW EXECUTE FUNCTION fn_grp_voto_inmutable();

-- R-GRP-09 · un acuerdo abierto por tipo y objeto
CREATE UNIQUE INDEX uq_acuerdo_abierto
  ON acuerdo (grupo_id, tipo, COALESCE(referencia_afectada_id, grupo_id))
  WHERE estado = 'EN_VOTACION';

-- R-GRP-12 · retiro deudor exige plan
ALTER TABLE solicitud_retiro
  ADD CONSTRAINT ck_retiro_deudor_con_plan CHECK (
      estado <> 'APROBADO'
   OR posicion <> 'DEUDORA'
   OR plan_regularizacion_id IS NOT NULL
  );

-- R-GRP-13 · un grupo disuelto cierra en cero
CREATE OR REPLACE FUNCTION fn_grp_validar_disolucion() RETURNS trigger AS $$
DECLARE v_saldo NUMERIC(16,2);
BEGIN
  IF NEW.estado <> 'CERRADA' THEN RETURN NEW; END IF;
  SELECT COALESCE(saldo_disponible + saldo_retenido, 0) INTO v_saldo
    FROM cuenta_billetera WHERE grupo_id = NEW.grupo_id AND tipo = 'GRUPO';
  IF v_saldo <> 0 THEN
    RAISE EXCEPTION 'R-GRP-13: la cuenta del grupo cierra con % y debe cerrar en cero', v_saldo;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_disolucion_cuadra
  BEFORE UPDATE OF estado ON disolucion_anticipada
  FOR EACH ROW EXECUTE FUNCTION fn_grp_validar_disolucion();

-- R-GRP-04 · el grupo es el titular, nunca una persona
--   (cubierto por ck_cuenta_titularidad de R-BIL-05; se refuerza el egreso)
CREATE OR REPLACE FUNCTION fn_grp_validar_retiro_grupo() RETURNS trigger AS $$
DECLARE v_tipo TEXT;
BEGIN
  SELECT tipo INTO v_tipo FROM cuenta_billetera WHERE id = NEW.cuenta_billetera_id;
  IF v_tipo = 'GRUPO' THEN
    RAISE EXCEPTION 'R-GRP-04: la cuenta de un grupo no admite retiros directos; use entrega_fondo';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_retiro_no_grupo
  BEFORE INSERT ON orden_retiro
  FOR EACH ROW EXECUTE FUNCTION fn_grp_validar_retiro_grupo();
-- R-GRP-14 · una postulación pendiente por usuario y grupo
CREATE UNIQUE INDEX uq_solicitud_ingreso_pendiente
  ON solicitud_ingreso (grupo_id, usuario_id)
  WHERE (estado = 'PENDIENTE');

ALTER TABLE solicitud_ingreso
  ADD CONSTRAINT ck_solicitud_ingreso_resuelta CHECK (
        estado = 'PENDIENTE'
     OR (fecha_resolucion IS NOT NULL AND revisada_por IS NOT NULL));

-- R-GRP-15 · invitación con vencimiento y token de un solo uso
ALTER TABLE invitacion
  ADD CONSTRAINT uq_invitacion_token UNIQUE (token_id),
  ADD CONSTRAINT ck_invitacion_expira CHECK (fecha_expiracion > fecha_envio),
  ADD CONSTRAINT ck_invitacion_respuesta CHECK (
        estado IN ('ENVIADA', 'EXPIRADA') OR fecha_respuesta IS NOT NULL);

CREATE UNIQUE INDEX uq_invitacion_activa
  ON invitacion (grupo_id, telefono_invitado)
  WHERE (estado = 'ENVIADA');

-- R-GRP-16 · calendario de días no hábiles sin duplicados ni ámbitos incompletos
ALTER TABLE dia_no_habil
  ADD CONSTRAINT ck_dia_no_habil_ambito CHECK (
        (alcance = 'GRUPO' AND grupo_id IS NOT NULL)
     OR (alcance <> 'GRUPO' AND grupo_id IS NULL));

CREATE UNIQUE INDEX uq_dia_no_habil
  ON dia_no_habil (fecha, alcance, COALESCE(grupo_id, '00000000-0000-0000-0000-000000000000'::uuid));


-- ---------------------------------------------------------------------
-- R-RIS — Riesgo operativo y continuidad
-- ---------------------------------------------------------------------

-- R-RIS-01 · taxonomía cerrada
ALTER TABLE evento_riesgo_operativo
  ADD CONSTRAINT ck_evento_categoria CHECK (categoria_evento IN (
      'FRAUDE_INTERNO','FRAUDE_EXTERNO','RELACIONES_LABORALES',
      'CLIENTES_PRODUCTOS_PRACTICAS','DANOS_ACTIVOS','FALLAS_SISTEMAS')),
  ADD CONSTRAINT ck_evento_factor CHECK (factor_riesgo IN (
      'PROCESOS_INTERNOS','PERSONAS','TECNOLOGIA_INFORMACION',
      'EVENTOS_EXTERNOS','INFRAESTRUCTURA')),
  ADD CONSTRAINT ck_evento_fechas CHECK (fecha_deteccion >= fecha_ocurrencia);

-- R-RIS-02 · pérdida neta derivada
--   perdida_neta GENERATED ALWAYS AS (perdida_bruta - recuperacion) STORED
ALTER TABLE evento_riesgo_operativo
  ADD CONSTRAINT ck_evento_recuperacion CHECK (recuperacion <= perdida_bruta);

-- R-RIS-03 · continuidad con objetivos y prueba
ALTER TABLE plan_continuidad
  ADD CONSTRAINT ck_plan_objetivos CHECK (rto_minutos > 0 AND rpo_minutos >= 0),
  ADD CONSTRAINT ck_plan_prueba CHECK (proxima_prueba > vigente_desde);

ALTER TABLE prueba_continuidad
  ADD CONSTRAINT ck_prueba_resultado CHECK (
      resultado <> 'EXITOSA'
   OR (rto_obtenido_minutos IS NOT NULL AND acta_comite_id IS NOT NULL)
  );


-- ---------------------------------------------------------------------
-- R-REP — Reputación y transparencia
-- ---------------------------------------------------------------------

-- R-REP-01 · un hecho puntúa una sola vez
ALTER TABLE evento_reputacion
  ADD CONSTRAINT uq_evento_reputacion_hecho
  UNIQUE (usuario_id, referencia_tipo, referencia_origen_id, tipo);

-- R-REP-02 · un solo puntaje vigente por usuario
ALTER TABLE puntaje_reputacion
  ADD CONSTRAINT ex_puntaje_vigente
  EXCLUDE USING gist (
    usuario_id WITH =,
    tstzrange(vigente_desde, vigente_hasta, '[)') WITH &&
  );

-- R-REP-03 · el total cuadra con sus componentes
CREATE OR REPLACE FUNCTION fn_rep_validar_componentes() RETURNS trigger AS $$
DECLARE v_suma NUMERIC(12,4);
BEGIN
  SELECT COALESCE(SUM(contribucion), 0) INTO v_suma
    FROM componente_score WHERE puntaje_id = NEW.id;
  IF round(v_suma, 2) <> round(NEW.puntaje, 2) THEN
    RAISE EXCEPTION 'R-REP-03: el puntaje % no cuadra con sus componentes (% vs %)',
                    NEW.id, NEW.puntaje, v_suma;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER tg_puntaje_cuadra
  AFTER INSERT OR UPDATE ON puntaje_reputacion
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION fn_rep_validar_componentes();

-- R-REP-04 · cadena de transparencia única y encadenada
ALTER TABLE bloque_transparencia
  ADD CONSTRAINT uq_bloque_grupo_numero UNIQUE (grupo_id, numero_bloque),
  ADD CONSTRAINT ck_bloque_genesis CHECK (
      (numero_bloque = 1 AND hash_bloque_anterior IS NULL)
   OR (numero_bloque > 1 AND hash_bloque_anterior IS NOT NULL)
  );

CREATE OR REPLACE FUNCTION fn_rep_encadenar_bloque() RETURNS trigger AS $$
DECLARE v_hash VARCHAR(64); v_numero INTEGER;
BEGIN
  SELECT hash_bloque, numero_bloque INTO v_hash, v_numero
    FROM bloque_transparencia
   WHERE grupo_id = NEW.grupo_id
   ORDER BY numero_bloque DESC LIMIT 1;
  IF v_numero IS NOT NULL AND NEW.numero_bloque <> v_numero + 1 THEN
    RAISE EXCEPTION 'R-REP-04: salto de numeración en la cadena (% tras %)',
                    NEW.numero_bloque, v_numero;
  END IF;
  IF v_hash IS NOT NULL AND NEW.hash_bloque_anterior IS DISTINCT FROM v_hash THEN
    RAISE EXCEPTION 'R-REP-04: hash_bloque_anterior no coincide con el bloque previo';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_bloque_encadenado
  BEFORE INSERT ON bloque_transparencia
  FOR EACH ROW EXECUTE FUNCTION fn_rep_encadenar_bloque();
-- R-REP-05 · una insignia por usuario; revocar no borra
ALTER TABLE insignia_otorgada
  ADD CONSTRAINT uq_insignia_usuario UNIQUE (usuario_id, insignia_id),
  ADD CONSTRAINT ck_insignia_revocacion_motivada CHECK (
        revocada_en IS NULL OR motivo_revocacion IS NOT NULL);

-- R-REP-06 · una reseña por autor, evaluado, grupo y dimensión
ALTER TABLE resena_participante
  ADD CONSTRAINT uq_resena_autor_evaluado
    UNIQUE (grupo_id, autor_participante_id, evaluado_usuario_id, dimension),
  ADD CONSTRAINT ck_resena_moderada CHECK (
        estado_moderacion = 'PENDIENTE' OR moderada_por IS NOT NULL);

CREATE OR REPLACE FUNCTION fn_rep_validar_resena() RETURNS trigger AS $$
DECLARE v_usuario_autor UUID;
BEGIN
  SELECT usuario_id INTO v_usuario_autor
    FROM participante WHERE id = NEW.autor_participante_id;
  IF v_usuario_autor = NEW.evaluado_usuario_id THEN
    RAISE EXCEPTION 'R-REP-06: nadie puede reseñarse a sí mismo';
  END IF;
  IF NOT EXISTS (
      SELECT 1 FROM participante p
       WHERE p.grupo_id = NEW.grupo_id AND p.usuario_id = NEW.evaluado_usuario_id) THEN
    RAISE EXCEPTION 'R-REP-06: el evaluado no participó del grupo %', NEW.grupo_id;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_resena_convivencia
  BEFORE INSERT ON resena_participante
  FOR EACH ROW EXECUTE FUNCTION fn_rep_validar_resena();


-- ---------------------------------------------------------------------
-- R-NOT — Notificaciones
-- ---------------------------------------------------------------------

-- R-NOT-01 · idempotencia del envío, amparada en la notificación (R-BIL-06)
CREATE UNIQUE INDEX uq_envio_idempotencia
  ON envio_notificacion (notificacion_id, clave_idempotencia);
CREATE UNIQUE INDEX uq_evento_entrega_idempotencia
  ON evento_entrega_mensaje (envio_id, clave_idempotencia);

-- R-NOT-02 · tope diario configurable, denegando por omisión
CREATE OR REPLACE FUNCTION fn_not_puede_enviar(
    p_destinatario UUID, p_es_obligatorio BOOLEAN) RETURNS VOID AS $$
DECLARE v_tope INTEGER; v_hoy INTEGER;
BEGIN
  IF p_es_obligatorio THEN RETURN; END IF;      -- los regulatorios no topean
  -- El tope se lee de la preferencia del usuario; sin preferencia, uno conservador.
  SELECT tope_diario_mensajes INTO v_tope
    FROM preferencia_notificacion WHERE usuario_id = p_destinatario;
  v_tope := COALESCE(v_tope, 3);                -- por omisión, conservador
  SELECT count(*) INTO v_hoy
    FROM envio_notificacion e
    JOIN notificacion n ON n.id = e.notificacion_id
   WHERE n.usuario_id = p_destinatario
     AND e.encolado_en >= date_trunc('day', now());
  IF v_hoy >= v_tope THEN
    RAISE EXCEPTION 'R-NOT-02: tope diario de % mensajes alcanzado', v_tope;
  END IF;
END $$ LANGUAGE plpgsql;

-- R-NOT-03 · la supresión vigente gana sobre cualquier campaña
-- La categoría y la obligatoriedad son del tipo de evento, no de cada aviso:
-- se resuelven por join contra el catálogo, sin duplicar el dato.
CREATE OR REPLACE FUNCTION fn_not_validar_supresion() RETURNS trigger AS $$
DECLARE v_suprimido BOOLEAN; v_categoria TEXT; v_obligatorio BOOLEAN;
BEGIN
  SELECT e.categoria, e.es_obligatorio INTO v_categoria, v_obligatorio
    FROM evento_notificable e WHERE e.id = NEW.evento_id;
  IF COALESCE(v_obligatorio, FALSE) THEN
    RETURN NEW;                       -- los avisos obligatorios no se suprimen
  END IF;
  SELECT EXISTS (
    SELECT 1 FROM lista_supresion s
      JOIN canal_vinculado c ON c.identificador = s.identificador
     WHERE c.usuario_id = NEW.usuario_id
       AND s.activa
       AND (s.categoria = v_categoria OR s.categoria = 'TODAS')
  ) INTO v_suprimido;
  IF v_suprimido THEN
    RAISE EXCEPTION 'R-NOT-03: destinatario suprimido para la categoría %', v_categoria;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_notificacion_supresion
  BEFORE INSERT ON notificacion
  FOR EACH ROW EXECUTE FUNCTION fn_not_validar_supresion();


-- ---------------------------------------------------------------------
-- R-GAR — Garantía, incumplimiento y cobranza
-- ---------------------------------------------------------------------

-- R-GAR-01 · notificar es lo que hace correr el plazo, y el plazo se persiste
ALTER TABLE registro_incumplimiento
  ADD CONSTRAINT ck_incumplimiento_plazo_guardado CHECK (
        notificado_en IS NULL OR fecha_limite_subsanacion IS NOT NULL),
  ADD CONSTRAINT ck_incumplimiento_plazo_posterior CHECK (
        fecha_limite_subsanacion IS NULL
     OR fecha_limite_subsanacion > detectado_en),
  ADD CONSTRAINT ck_incumplimiento_cierre_motivado CHECK (
        cerrado_en IS NULL OR motivo_cierre IS NOT NULL);

-- R-GAR-02 · la evidencia no se toca
ALTER TABLE evidencia_incumplimiento
  ADD CONSTRAINT ck_evidencia_con_respaldo CHECK (
        url_archivo IS NULL OR hash_archivo IS NOT NULL);

CREATE OR REPLACE FUNCTION fn_gar_evidencia_inmutable() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'R-GAR-02: la evidencia de incumplimiento no admite % ', TG_OP;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_evidencia_inmutable
  BEFORE UPDATE OR DELETE ON evidencia_incumplimiento
  FOR EACH ROW EXECUTE FUNCTION fn_gar_evidencia_inmutable();

-- R-GAR-03 · una ejecución por aval y expediente
ALTER TABLE ejecucion_aval
  ADD CONSTRAINT uq_ejecucion_aval_registro UNIQUE (aval_id, registro_id),
  ADD CONSTRAINT ck_ejecucion_aval_monto CHECK (monto_ejecutado > 0),
  ADD CONSTRAINT ck_ejecucion_aval_plazo CHECK (plazo_respuesta > notificada_en);

-- R-GAR-04 · el tope firmado no se estira
CREATE OR REPLACE FUNCTION fn_gar_validar_tope_aval() RETURNS trigger AS $$
DECLARE v_tope NUMERIC(14,2); v_usado NUMERIC(14,2); v_estado VARCHAR(15);
BEGIN
  SELECT monto_maximo_avalado, estado INTO v_tope, v_estado
    FROM aval_participante WHERE id = NEW.aval_id FOR UPDATE;
  IF v_estado <> 'VIGENTE' THEN
    RAISE EXCEPTION 'R-GAR-04: el aval % no está vigente', NEW.aval_id;
  END IF;
  SELECT COALESCE(SUM(monto_ejecutado), 0) INTO v_usado
    FROM ejecucion_aval
   WHERE aval_id = NEW.aval_id AND estado <> 'ANULADA' AND id <> NEW.id;
  IF v_usado + NEW.monto_ejecutado > v_tope THEN
    RAISE EXCEPTION 'R-GAR-04: la ejecución (% + %) supera el tope avalado %',
                    v_usado, NEW.monto_ejecutado, v_tope;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_ejecucion_aval_tope
  BEFORE INSERT OR UPDATE ON ejecucion_aval
  FOR EACH ROW EXECUTE FUNCTION fn_gar_validar_tope_aval();

-- R-GAR-05 · una restricción viva por usuario y tipo; levantarla exige motivo
ALTER TABLE restriccion_usuario
  ADD CONSTRAINT ck_restriccion_levantamiento CHECK (
        (vigente_hasta IS NULL AND levantada_por IS NULL AND motivo_levantamiento IS NULL)
     OR (vigente_hasta IS NOT NULL)),
  ADD CONSTRAINT ck_restriccion_vigencia CHECK (
        vigente_hasta IS NULL OR vigente_hasta > vigente_desde);

CREATE UNIQUE INDEX uq_restriccion_usuario_vigente
  ON restriccion_usuario (usuario_id, tipo)
  WHERE (vigente_hasta IS NULL);

ALTER TABLE lista_restriccion_interna
  ADD CONSTRAINT ck_lista_retiro_motivado CHECK (
        retirado_en IS NULL OR (retirado_por IS NOT NULL AND motivo_retiro IS NOT NULL));

-- R-GAR-06 · la devolución del fondo no inventa dinero
ALTER TABLE devolucion_fondo
  ADD CONSTRAINT ck_devolucion_no_negativa CHECK (monto_a_devolver >= 0),
  ADD CONSTRAINT ck_devolucion_hasta_lo_aportado CHECK (
        monto_a_devolver <= monto_aportado),
  ADD CONSTRAINT ck_devolucion_cuadra CHECK (
        monto_a_devolver = GREATEST(monto_aportado - monto_consumido, 0)),
  ADD CONSTRAINT ck_devolucion_retencion_motivada CHECK (
        estado <> 'RETENIDA' OR motivo_retencion IS NOT NULL);

CREATE UNIQUE INDEX uq_devolucion_fondo_participante
  ON devolucion_fondo (fondo_id, participante_id);

-- R-GAR-07 · una alerta abierta por causa, y el cierre lleva desenlace
CREATE UNIQUE INDEX uq_alerta_temprana_abierta
  ON alerta_temprana (usuario_id, COALESCE(grupo_id, '00000000-0000-0000-0000-000000000000'::uuid), codigo)
  WHERE (estado = 'ABIERTA');

ALTER TABLE alerta_riesgo
  ADD CONSTRAINT ck_alerta_riesgo_cierre CHECK (
        estado <> 'CERRADA' OR cerrada_en IS NOT NULL);


-- ---------------------------------------------------------------------
-- R-DES — Desembolsos y entregas
-- ---------------------------------------------------------------------

-- R-DES-01 · una orden viva por entrega; la clave corta el doble pago
-- La clave se ampara en la entrega, no es global (R-BIL-06).
CREATE UNIQUE INDEX uq_orden_desembolso_clave
  ON orden_desembolso (entrega_id, clave_idempotencia);

ALTER TABLE orden_desembolso
  ADD CONSTRAINT ck_orden_desembolso_monto CHECK (monto > 0),
  ADD CONSTRAINT ck_orden_desembolso_acreditada CHECK (
        estado <> 'ACREDITADA'
     OR (acreditada_en IS NOT NULL AND referencia_proveedor IS NOT NULL));

CREATE UNIQUE INDEX uq_orden_desembolso_entrega_viva
  ON orden_desembolso (entrega_id)
  WHERE (estado NOT IN ('RECHAZADA', 'FALLIDA', 'REVERSADA'));

ALTER TABLE intento_desembolso
  ADD CONSTRAINT uq_intento_desembolso_numero UNIQUE (orden_desembolso_id, numero_intento),
  ADD CONSTRAINT ck_intento_desembolso_fallo CHECK (
        resultado <> 'FALLO' OR codigo_error IS NOT NULL);

-- R-DES-02 · la cuenta destino tiene que estar verificada y fuera de enfriamiento
CREATE OR REPLACE FUNCTION fn_des_validar_cuenta_destino() RETURNS trigger AS $$
DECLARE v_estado VARCHAR(15); v_bloqueada TIMESTAMPTZ;
BEGIN
  SELECT estado_verificacion, bloqueada_hasta INTO v_estado, v_bloqueada
    FROM cuenta_bancaria_beneficiario WHERE id = NEW.cuenta_destino_id;
  IF v_estado IS DISTINCT FROM 'VERIFICADA' THEN
    RAISE EXCEPTION 'R-DES-02: la cuenta destino % no está verificada (%)',
                    NEW.cuenta_destino_id, COALESCE(v_estado, 'inexistente');
  END IF;
  IF v_bloqueada IS NOT NULL AND v_bloqueada > now() THEN
    RAISE EXCEPTION 'R-DES-02: la cuenta destino está en enfriamiento hasta %', v_bloqueada;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_orden_desembolso_cuenta_verificada
  BEFORE INSERT ON orden_desembolso
  FOR EACH ROW EXECUTE FUNCTION fn_des_validar_cuenta_destino();


-- ---------------------------------------------------------------------
-- R-ORG — Organizador y automatización
-- ---------------------------------------------------------------------

-- R-ORG-01 · una postulación pendiente por usuario
CREATE UNIQUE INDEX uq_solicitud_organizador_pendiente
  ON solicitud_organizador (usuario_id)
  WHERE (estado = 'PENDIENTE');

ALTER TABLE solicitud_organizador
  ADD CONSTRAINT ck_solicitud_org_resuelta CHECK (
        estado = 'PENDIENTE' OR fecha_resolucion IS NOT NULL),
  ADD CONSTRAINT ck_solicitud_org_rechazo_motivado CHECK (
        estado <> 'RECHAZADA' OR motivo_rechazo IS NOT NULL);

-- R-ORG-02 · un contrato vigente por organizador, sin solaparse
ALTER TABLE contrato_organizador
  ADD CONSTRAINT ck_contrato_org_vigencia CHECK (
        vigente_hasta IS NULL OR vigente_hasta > vigente_desde),
  ADD CONSTRAINT ck_contrato_org_rescision CHECK (
        rescindido_en IS NULL OR motivo_rescision IS NOT NULL),
  ADD CONSTRAINT ck_contrato_org_firma CHECK (
        firmado_en IS NULL OR token_firma_id IS NOT NULL);

ALTER TABLE contrato_organizador
  ADD CONSTRAINT ex_contrato_org_vigente
  EXCLUDE USING gist (
    organizador_id WITH =,
    daterange(vigente_desde, vigente_hasta, '[)') WITH &&
  ) WHERE (firmado_en IS NOT NULL AND rescindido_en IS NULL);

-- sin contrato firmado y vigente no se crea un grupo con organizador
CREATE OR REPLACE FUNCTION fn_org_validar_contrato_grupo() RETURNS trigger AS $$
BEGIN
  IF NEW.organizador_id IS NULL THEN
    RETURN NEW;   -- grupo autogestionado: no hay organizador que deba contrato
  END IF;
  IF NOT EXISTS (
      SELECT 1 FROM contrato_organizador c
       WHERE c.organizador_id = NEW.organizador_id
         AND c.firmado_en IS NOT NULL
         AND c.rescindido_en IS NULL
         AND c.vigente_desde <= CURRENT_DATE
         AND (c.vigente_hasta IS NULL OR c.vigente_hasta > CURRENT_DATE)) THEN
    RAISE EXCEPTION 'R-ORG-02: el organizador % no tiene contrato firmado y vigente',
                    NEW.organizador_id;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_grupo_contrato_organizador
  BEFORE INSERT ON grupo
  FOR EACH ROW EXECUTE FUNCTION fn_org_validar_contrato_grupo();

-- R-ORG-03 · lo firmado no se reescribe
CREATE OR REPLACE FUNCTION fn_org_contrato_inmutable() RETURNS trigger AS $$
BEGIN
  IF OLD.firmado_en IS NOT NULL AND (
       NEW.contenido_hash IS DISTINCT FROM OLD.contenido_hash
    OR NEW.obligaciones   IS DISTINCT FROM OLD.obligaciones
    OR NEW.causales_rescision IS DISTINCT FROM OLD.causales_rescision
    OR NEW.version        IS DISTINCT FROM OLD.version) THEN
    RAISE EXCEPTION 'R-ORG-03: un contrato firmado no se modifica; emita una versión nueva';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_contrato_org_inmutable
  BEFORE UPDATE ON contrato_organizador
  FOR EACH ROW EXECUTE FUNCTION fn_org_contrato_inmutable();

-- R-ORG-04 · una evaluación por organizador y período
ALTER TABLE evaluacion_desempeno
  ADD CONSTRAINT uq_evaluacion_org_periodo UNIQUE (organizador_id, periodo_evaluado);

ALTER TABLE metrica_organizador
  ADD CONSTRAINT uq_metrica_org_codigo UNIQUE (evaluacion_id, codigo),
  ADD CONSTRAINT ck_metrica_org_peso CHECK (peso >= 0 AND peso <= 1);

-- R-ORG-05 · una apelación por sanción, y no la resuelve quien la aplicó
ALTER TABLE apelacion_sancion_org
  ADD CONSTRAINT uq_apelacion_por_sancion UNIQUE (sancion_organizador_id),
  ADD CONSTRAINT ck_apelacion_org_resuelta CHECK (
        estado = 'PENDIENTE'
     OR (resuelta_en IS NOT NULL AND resuelta_por IS NOT NULL AND resolucion IS NOT NULL));

CREATE OR REPLACE FUNCTION fn_org_validar_resolutor() RETURNS trigger AS $$
DECLARE v_aplicada_por UUID;
BEGIN
  IF NEW.resuelta_por IS NULL THEN RETURN NEW; END IF;
  SELECT aplicada_por INTO v_aplicada_por
    FROM sancion_organizador WHERE id = NEW.sancion_organizador_id;
  IF v_aplicada_por = NEW.resuelta_por THEN
    RAISE EXCEPTION 'R-ORG-05: quien aplicó la sanción no puede resolver su apelación';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_apelacion_org_resolutor
  BEFORE INSERT OR UPDATE ON apelacion_sancion_org
  FOR EACH ROW EXECUTE FUNCTION fn_org_validar_resolutor();

ALTER TABLE sancion_organizador
  ADD CONSTRAINT ck_sancion_org_vigencia CHECK (
        vigente_hasta IS NULL OR vigente_hasta > vigente_desde);

-- R-ORG-06 · lo que mueve dinero o afecta derechos no se automatiza solo
ALTER TABLE regla_automatizacion
  ADD CONSTRAINT ck_regla_confirmacion_humana CHECK (
        accion NOT IN ('PROPONER_COBRO', 'PROPONER_ENTREGA',
                       'PROPONER_SANCION', 'PROPONER_COBERTURA')
     OR requiere_confirmacion_humana),
  ADD CONSTRAINT ck_regla_prioridad CHECK (prioridad BETWEEN 1 AND 99);

CREATE UNIQUE INDEX uq_regla_automatizacion_prioridad
  ON regla_automatizacion (disparador, prioridad)
  WHERE (activa);

-- R-ORG-07 · una tarea por hecho disparador
-- La clave se ampara en la regla y el grupo, no es global (R-BIL-06).
CREATE UNIQUE INDEX uq_tarea_automatizada_clave
  ON tarea_automatizada (regla_id, grupo_id, clave_idempotencia);

ALTER TABLE tarea_automatizada
  ADD CONSTRAINT ck_tarea_intentos CHECK (intentos >= 0);

ALTER TABLE ejecucion_tarea
  ADD CONSTRAINT ck_ejecucion_tarea_error CHECK (
        resultado <> 'FALLO' OR mensaje_error IS NOT NULL),
  ADD CONSTRAINT ck_ejecucion_tarea_fin CHECK (
        finalizada_en IS NULL OR finalizada_en >= iniciada_en);


-- ---------------------------------------------------------------------
-- R-CTB — Contabilidad financiera y ERP
-- ---------------------------------------------------------------------

-- R-CTB-01 · un período por ejercicio y mes, y nada se asienta en uno cerrado
ALTER TABLE ejercicio_fiscal
  ADD CONSTRAINT ck_ejercicio_fiscal_rango CHECK (fecha_fin > fecha_inicio),
  ADD CONSTRAINT ck_ejercicio_fiscal_cierre CHECK (
        estado <> 'CERRADO' OR (cerrado_en IS NOT NULL AND cerrado_por IS NOT NULL));

ALTER TABLE periodo_contable
  ADD CONSTRAINT uq_periodo_contable_ejercicio_mes
    UNIQUE (ejercicio_fiscal_id, mes),
  ADD CONSTRAINT ck_periodo_contable_rango CHECK (fecha_fin > fecha_inicio);

-- El cierre guarda el cuadre del momento: si no cuadra, no es un cierre.
ALTER TABLE cierre_periodo_contable
  ADD CONSTRAINT ck_cierre_periodo_cuadrado CHECK (total_debe = total_haber);

CREATE OR REPLACE FUNCTION fn_ctb_periodo_abierto() RETURNS trigger AS $$
DECLARE
  v_estado TEXT;
BEGIN
  IF NEW.periodo_contable_id IS NULL THEN
    -- Asientos anteriores a M13 no llevan período: se aceptan sin validar.
    RETURN NEW;
  END IF;
  SELECT estado INTO v_estado
    FROM periodo_contable WHERE id = NEW.periodo_contable_id;
  IF v_estado IS NULL THEN
    RAISE EXCEPTION 'R-CTB-01: el período contable % no existe',
                    NEW.periodo_contable_id;
  END IF;
  IF v_estado <> 'ABIERTO' THEN
    RAISE EXCEPTION 'R-CTB-01: el período contable % está %; no admite asientos',
                    NEW.periodo_contable_id, v_estado;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_asiento_periodo_abierto
  BEFORE INSERT ON asiento_contable
  FOR EACH ROW EXECUTE FUNCTION fn_ctb_periodo_abierto();

-- R-CTB-02 · una cuenta sumarizadora es un total, no un destino de asiento
CREATE OR REPLACE FUNCTION fn_ctb_cuenta_de_movimiento() RETURNS trigger AS $$
DECLARE
  v_movimiento BOOLEAN;
BEGIN
  SELECT es_cuenta_de_movimiento INTO v_movimiento
    FROM cuenta_contable WHERE id = NEW.cuenta_id;
  IF v_movimiento IS DISTINCT FROM TRUE THEN
    RAISE EXCEPTION
      'R-CTB-02: la cuenta % es sumarizadora; no recibe movimientos directos',
      NEW.cuenta_id;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_movimiento_cuenta_de_movimiento
  BEFORE INSERT ON movimiento_contable
  FOR EACH ROW EXECUTE FUNCTION fn_ctb_cuenta_de_movimiento();

-- Una cuenta no puede ser su propio padre.
ALTER TABLE cuenta_contable
  ADD CONSTRAINT ck_cuenta_contable_padre_distinto CHECK (
        cuenta_padre_id IS NULL OR cuenta_padre_id <> id),
  ADD CONSTRAINT ck_cuenta_contable_nivel CHECK (nivel >= 1);

-- R-CTB-03 · un presupuesto por centro de costo y ejercicio
ALTER TABLE presupuesto
  ADD CONSTRAINT uq_presupuesto_centro_ejercicio
    UNIQUE (centro_costo_id, ejercicio_fiscal_id),
  ADD CONSTRAINT ck_presupuesto_aprobacion CHECK (
        estado <> 'APROBADO' OR (aprobado_por IS NOT NULL AND aprobado_en IS NOT NULL));

ALTER TABLE partida_presupuestaria
  ADD CONSTRAINT uq_partida_presupuesto_cuenta_periodo
    UNIQUE (presupuesto_id, cuenta_contable_id, periodo_contable_id);

-- R-CTB-04 · una factura por proveedor y número, con saldo coherente
ALTER TABLE factura_proveedor
  ADD CONSTRAINT uq_factura_proveedor_numero
    UNIQUE (tercero_comercial_id, numero_factura),
  ADD CONSTRAINT ck_factura_proveedor_pagado CHECK (
        monto_pagado >= 0 AND monto_pagado <= monto),
  ADD CONSTRAINT ck_factura_proveedor_vencimiento CHECK (
        fecha_vencimiento >= fecha_emision),
  ADD CONSTRAINT ck_factura_proveedor_aprobacion CHECK (
        estado = 'REGISTRADA' OR estado = 'ANULADA' OR aprobada_por IS NOT NULL);

ALTER TABLE orden_compra
  ADD CONSTRAINT ck_orden_compra_aprobacion CHECK (
        estado = 'BORRADOR' OR estado = 'CANCELADA' OR aprobada_por IS NOT NULL);

-- R-CTB-05 · cuatro ojos sobre el egreso: quien aprueba no paga
CREATE OR REPLACE FUNCTION fn_ctb_segregacion_pago() RETURNS trigger AS $$
DECLARE
  v_aprobador UUID;
BEGIN
  SELECT aprobada_por INTO v_aprobador
    FROM factura_proveedor WHERE id = NEW.factura_proveedor_id;
  IF v_aprobador IS NULL THEN
    RAISE EXCEPTION
      'R-CTB-05: la factura % no está aprobada; no se puede pagar',
      NEW.factura_proveedor_id;
  END IF;
  IF v_aprobador = NEW.autorizado_por THEN
    RAISE EXCEPTION
      'R-CTB-05: % aprobó la factura %; no puede además autorizar su pago',
      NEW.autorizado_por, NEW.factura_proveedor_id;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_pago_proveedor_segregacion
  BEFORE INSERT ON pago_a_proveedor
  FOR EACH ROW EXECUTE FUNCTION fn_ctb_segregacion_pago();

-- R-CTB-06 · no se cobra más de lo que se debe
ALTER TABLE cuenta_por_cobrar
  ADD CONSTRAINT ck_cxc_cobrado CHECK (
        monto_cobrado >= 0 AND monto_cobrado <= monto);

-- R-CTB-07 · una corrida de depreciación por activo y período
ALTER TABLE depreciacion_activo
  ADD CONSTRAINT uq_depreciacion_activo_periodo
    UNIQUE (activo_fijo_id, periodo_contable_id);

ALTER TABLE activo_fijo
  ADD CONSTRAINT ck_activo_fijo_depreciacion CHECK (
        depreciacion_acumulada >= 0
    AND depreciacion_acumulada <= costo_adquisicion - valor_residual),
  ADD CONSTRAINT ck_activo_fijo_residual CHECK (
        valor_residual >= 0 AND valor_residual <= costo_adquisicion);

-- R-CTB-08 · un estado financiero por período y tipo
ALTER TABLE estado_financiero_generado
  ADD CONSTRAINT uq_estado_financiero_periodo_tipo
    UNIQUE (periodo_contable_id, tipo);

ALTER TABLE linea_plantilla_asiento
  ADD CONSTRAINT uq_linea_plantilla_orden UNIQUE (plantilla_id, orden);

-- R-CTB-09 · el saldo contable se deriva del libro, igual que el de billetera
--
-- `cuenta_billetera.saldo_*` lo deriva el motor desde R-BIL-16; `cuenta_contable.saldo`
-- no tenía equivalente, y quedaba en manos de la aplicación hacer el
-- `UPDATE ... SET saldo = saldo + delta` en la misma transacción. Eso convierte en
-- promesa lo que en la billetera es garantía: basta un caso de uso nuevo que inserte
-- en `movimiento_contable` y se olvide del saldo para que el mayor deje de reflejar
-- la posición, y nada lo impida.
--
-- El signo lo da la NATURALEZA de la cuenta y no el lado del movimiento: en una
-- cuenta deudora (activo, egreso) el debe suma; en una acreedora (pasivo,
-- patrimonio, ingreso) suma el haber. Escribirlo en la base es lo que evita que
-- catorce servicios repitan esa tabla de signos, cada uno con su criterio.
--
-- El bloqueo de fila se toma ANTES de leer, por el mismo motivo que
-- `fn_bil_recalcular_saldos`: dos asientos simultáneos sobre la misma cuenta que
-- leyeran el libro a la vez calcularían ambos sobre un mayor incompleto.
CREATE OR REPLACE FUNCTION fn_ctb_recalcular_saldo(p_cuenta UUID) RETURNS VOID AS $$
DECLARE v_saldo NUMERIC(16,2); v_naturaleza TEXT;
BEGIN
  SELECT naturaleza INTO v_naturaleza
    FROM cuenta_contable WHERE id = p_cuenta FOR UPDATE;

  SELECT COALESCE(SUM(
           CASE WHEN v_naturaleza = 'DEUDORA' THEN debe - haber
                ELSE haber - debe END), 0)
    INTO v_saldo
    FROM movimiento_contable WHERE cuenta_id = p_cuenta;

  UPDATE cuenta_contable SET saldo = v_saldo WHERE id = p_cuenta;
END $$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_ctb_sincronizar_saldo() RETURNS trigger AS $$
BEGIN
  PERFORM fn_ctb_recalcular_saldo(NEW.cuenta_id);
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_movimiento_contable_sincroniza_saldo
  AFTER INSERT ON movimiento_contable
  FOR EACH ROW EXECUTE FUNCTION fn_ctb_sincronizar_saldo();


-- ---------------------------------------------------------------------
-- R-PUB — Publicidad y campañas
-- ---------------------------------------------------------------------

-- R-PUB-01 · exactamente una referencia según el tipo de anunciante
ALTER TABLE anunciante
  ADD CONSTRAINT ck_anunciante_tipo_exclusivo CHECK (
        (tipo = 'ORGANIZADOR'
           AND organizador_id IS NOT NULL AND socio_comercial_id IS NULL)
     OR (tipo = 'SOCIO_COMERCIAL'
           AND socio_comercial_id IS NOT NULL AND organizador_id IS NULL));

-- R-PUB-02 · una cuenta publicitaria por anunciante
ALTER TABLE cuenta_publicitaria
  ADD CONSTRAINT uq_cuenta_publicitaria_anunciante UNIQUE (anunciante_id),
  ADD CONSTRAINT ck_cuenta_publicitaria_consumo CHECK (
        saldo_consumido_mes >= 0
    AND (limite_gasto_mensual IS NULL
         OR saldo_consumido_mes <= limite_gasto_mensual));

-- R-PUB-03 · presupuesto consumido acotado, y aprobación con responsable
ALTER TABLE campana_publicitaria
  ADD CONSTRAINT ck_campana_pub_consumo CHECK (
        presupuesto_consumido >= 0
    AND presupuesto_consumido <= presupuesto_total),
  ADD CONSTRAINT ck_campana_pub_aprobacion CHECK (
        estado IN ('BORRADOR', 'EN_REVISION', 'RECHAZADA')
     OR aprobada_por IS NOT NULL),
  ADD CONSTRAINT ck_campana_pub_vigencia CHECK (
        fecha_fin IS NULL OR fecha_fin > fecha_inicio);

-- R-PUB-04 · moderación previa: sin pieza aprobada no hay anuncio
CREATE OR REPLACE FUNCTION fn_pub_creativa_aprobada() RETURNS trigger AS $$
DECLARE
  v_estado TEXT;
BEGIN
  SELECT estado_moderacion INTO v_estado
    FROM pieza_creativa WHERE id = NEW.pieza_creativa_id;
  IF v_estado IS DISTINCT FROM 'APROBADA' THEN
    RAISE EXCEPTION
      'R-PUB-04: la pieza creativa % está %; no puede entregarse',
      NEW.pieza_creativa_id, coalesce(v_estado, 'inexistente');
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_anuncio_creativa_aprobada
  BEFORE INSERT ON anuncio
  FOR EACH ROW EXECUTE FUNCTION fn_pub_creativa_aprobada();

-- R-PUB-05 · quien sube no se autoaprueba
ALTER TABLE revision_creativa
  ADD CONSTRAINT ck_revision_creativa_motivo CHECK (
        decision <> 'RECHAZADA' OR motivo IS NOT NULL);

CREATE OR REPLACE FUNCTION fn_pub_moderador_distinto() RETURNS trigger AS $$
DECLARE
  v_anunciante UUID;
  v_organizador_usuario UUID;
BEGIN
  SELECT p.anunciante_id INTO v_anunciante
    FROM pieza_creativa p WHERE p.id = NEW.pieza_creativa_id;

  -- Si el anunciante es un organizador, su usuario no puede moderar su pieza.
  SELECT o.usuario_id INTO v_organizador_usuario
    FROM anunciante a
    JOIN organizador o ON o.id = a.organizador_id
   WHERE a.id = v_anunciante;

  IF v_organizador_usuario IS NOT NULL
     AND v_organizador_usuario = NEW.revisada_por THEN
    RAISE EXCEPTION
      'R-PUB-05: % es el anunciante de la pieza %; no puede moderarla',
      NEW.revisada_por, NEW.pieza_creativa_id;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_revision_creativa_moderador
  BEFORE INSERT ON revision_creativa
  FOR EACH ROW EXECUTE FUNCTION fn_pub_moderador_distinto();

-- R-PUB-06 · un período de facturación por cuenta publicitaria
ALTER TABLE factura_publicidad
  ADD CONSTRAINT uq_factura_publicidad_cuenta_periodo
    UNIQUE (cuenta_publicitaria_id, periodo);

ALTER TABLE espacio_publicitario
  ADD CONSTRAINT ck_espacio_pub_capacidad CHECK (capacidad_maxima_simultanea > 0);


-- ---------------------------------------------------------------------
-- Roles de base de datos
-- ---------------------------------------------------------------------

-- rol_aplicacion   : la API
-- rol_backoffice   : soporte y operaciones
-- rol_cumplimiento : oficial de cumplimiento y analistas
-- rol_auditor      : solo lectura, incluso de tablas selladas
-- rol_migracion    : DDL, sin acceso a datos de producción
DO $$
DECLARE r TEXT;
BEGIN
  FOREACH r IN ARRAY ARRAY['rol_aplicacion','rol_backoffice','rol_cumplimiento',
                           'rol_auditor','rol_migracion'] LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
      EXECUTE format('CREATE ROLE %I NOLOGIN', r);
    END IF;
  END LOOP;
END $$;

-- La aplicación se conecta como rol_aplicacion y NUNCA como dueña del esquema.
-- No es un detalle de higiene: el dueño de una tabla omite sus políticas RLS
-- siempre, incluso con FORCE activado sobre otros roles. Una API que se conecta
-- como dueña convierte toda la sección R-SEG-03 en decoración.
GRANT USAGE ON SCHEMA public TO
  rol_aplicacion, rol_backoffice, rol_cumplimiento, rol_auditor;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO rol_aplicacion;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO rol_aplicacion;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO
  rol_auditor, rol_backoffice, rol_cumplimiento;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public FROM rol_auditor;
REVOKE DELETE ON ALL TABLES IN SCHEMA public FROM rol_aplicacion;

-- `ALL TABLES` sólo alcanza a las que existen en este momento. Sin privilegios
-- por omisión, cada tabla nueva nace invisible para el auditor y sin permisos
-- para la aplicación, y nadie se entera hasta que algo falla en producción.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE ON TABLES TO rol_aplicacion;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT ON TABLES TO rol_auditor, rol_backoffice, rol_cumplimiento;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO rol_aplicacion;

-- El rol de aplicación no puede tocar catálogos regulatorios
REVOKE INSERT, UPDATE, DELETE ON
    umbral_reporte_uif, limite_operativo_billetera, catalogo_reporte_regulatorio,
    impuesto, tarifario, concepto_tarifa, regla_tarifa, licencia_regulatoria,
    politica_interna, matriz_riesgo_lft, regla_monitoreo_lft
FROM rol_aplicacion;

-- El auditor lee todo, incluidos datos personales, y por eso su lectura también
-- deja huella: es un rol privilegiado a efectos de RLS (fn_seg_rol_privilegiado)
-- y toda consulta suya sobre datos sensibles debe registrarse en
-- registro_acceso_datos con justificación (R-SEG-02). El privilegio de ver todo
-- y la obligación de explicar por qué van juntos.
COMMENT ON ROLE rol_auditor IS
  'Sólo lectura sobre todo el esquema. Su acceso a datos personales exige '
  'registro en registro_acceso_datos con justificación (R-SEG-02).';


-- ---------------------------------------------------------------------
-- Índices que sostienen los controles
-- ---------------------------------------------------------------------

-- Vencimientos: tableros de control diario
CREATE INDEX ix_reclamo_vencidos ON reclamo_cliente (plazo_respuesta)
  WHERE estado IN ('INGRESADO','EN_ANALISIS');
CREATE INDEX ix_caso_vencidos ON caso_investigacion_lft (plazo_limite)
  WHERE estado <> 'CERRADO';
CREATE INDEX ix_requerimiento_vencidos ON requerimiento_autoridad (plazo_respuesta)
  WHERE estado <> 'RESPONDIDO';
CREATE INDEX ix_reporte_vencidos ON reporte_regulatorio (fecha_limite)
  WHERE estado <> 'ENVIADO';
CREATE INDEX ix_revision_kyc_vencidas ON revision_periodica_kyc (fecha_programada)
  WHERE estado <> 'EJECUTADA';
CREATE INDEX ix_ddd_por_vencer ON debida_diligencia (vence_en)
  WHERE estado = 'COMPLETA';

-- Extracto y auditoría de dinero
CREATE INDEX ix_movimiento_cuenta_fecha
  ON movimiento_billetera (cuenta_billetera_id, registrado_en DESC);
CREATE INDEX ix_transaccion_ocurrida ON transaccion_billetera USING brin (ocurrida_en);

-- Motor de umbrales
CREATE INDEX ix_operelev_periodo ON registro_operacion_relevante (periodo_remision, formulario)
  WHERE NOT exento;
CREATE INDEX ix_operelev_usuario_fecha
  ON registro_operacion_relevante (usuario_id, fecha_operacion DESC);

