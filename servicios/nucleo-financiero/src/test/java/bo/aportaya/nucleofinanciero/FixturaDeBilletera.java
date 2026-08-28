package bo.aportaya.nucleofinanciero;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;

/**
 * Filas minimas para probar la BILLETERA, separadas de las del libro contable.
 *
 * <p>Se partio de {@link FixturaDeNucleoFinanciero} cuando paso las 300 lineas: son
 * dos escenarios distintos —el asiento contable y el movimiento de saldo— y tenerlos
 * juntos obligaba a leer el doble para entender cualquiera de los dos.
 */
final class FixturaDeBilletera {

    /** Un telefono E.164 distinto por usuario: uq_usuario_telefono_e164 no perdona. */
    private static final AtomicInteger SECUENCIA = new AtomicInteger(60_000_000);

    private final DSLContext dsl;

    FixturaDeBilletera(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Un usuario real en identidad: la cuenta lo referencia por clave foranea. */
    UUID usuario() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Billetera', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'BASICO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "BIL-" + id.toString().substring(0, 8),
                "+591" + SECUENCIA.incrementAndGet());
        return id;
    }

    /** La politica por omision: sin ella la cuenta no se puede abrir. */
    UUID politica() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.politica_billetera
                    (id, codigo, moneda, dias_inactividad_para_limitar, permite_transferencia_p2p,
                     requiere_mfa_desde, ventana_enfriamiento_retiro_horas, dias_vigencia_retencion,
                     permite_saldo_negativo, vigente_desde)
                VALUES (?, ?, 'BOB', 365, true, 500.00, 24, 30, false, now() - interval '1 day')
                """,
                id,
                "POL-" + id.toString().substring(0, 8));
        return id;
    }

    /**
     * Una billetera de usuario con el saldo pedido.
     *
     * <p>El saldo se pone directo porque estas pruebas verifican los limites y las
     * reglas, no como se llego a ese saldo: llegar «bien» exigiria correr media
     * docena de casos de uso que todavia no existen.
     */
    UUID billetera(UUID usuarioId, String nivel, BigDecimal disponible) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_billetera
                    (id, numero_cuenta, tipo, usuario_id, politica_billetera_id, moneda, estado,
                     nivel_debida_diligencia, saldo_disponible, saldo_retenido,
                     permite_saldo_negativo, fecha_apertura, version)
                VALUES (?, ?, 'USUARIO', ?, ?, 'BOB', 'ACTIVA', ?, ?, 0, false, now(), 0)
                """,
                id,
                "BOB-" + id.toString().substring(0, 12),
                usuarioId,
                politica(),
                nivel,
                disponible);
        return id;
    }

    /** Un limite del catalogo para ese concepto, nivel y ventana. */
    UUID limite(String concepto, String nivel, String ventana, BigDecimal montoMaximo, Integer cantidadMaxima) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO catalogo.limite_operativo_billetera
                    (id, concepto, nivel_debida_diligencia, ventana, monto_maximo, cantidad_maxima,
                     moneda, base_normativa, vigente_desde, activo)
                VALUES (?, ?, ?, ?, ?, ?, 'BOB', 'ASFI 540/2025', current_date - 30, true)
                """,
                id,
                concepto,
                nivel,
                ventana,
                montoMaximo,
                cantidadMaxima);
        return id;
    }

    /**
     * La cuenta puente de custodia: el otro lado de todo ingreso.
     *
     * <p>Toda transaccion de billetera cuadra —debitos igual a creditos, y distinto de
     * cero— porque la plata viene de algun lado. El puente admite saldo negativo: es
     * una cuenta de sistema que representa lo que entro desde afuera, no el bolsillo
     * de nadie.
     */
    UUID puenteDeCustodia() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_billetera
                    (id, numero_cuenta, tipo, politica_billetera_id, moneda, estado,
                     nivel_debida_diligencia, saldo_disponible, saldo_retenido,
                     permite_saldo_negativo, fecha_apertura, version)
                VALUES (?, ?, 'PUENTE_CUSTODIA', ?, 'BOB', 'ACTIVA', 'SIMPLIFICADA', 0, 0,
                        true, now(), 0)
                """,
                id,
                "PUE-" + id.toString().substring(0, 12),
                politica());
        return id;
    }

    /**
     * Deja saldo en la billetera **por el camino real**: una transaccion cuadrada.
     *
     * <p>El trigger recalcula el disponible desde el libro, asi que escribir la
     * columna a mano se perderia en el primer recalculo. Y las tres sentencias van en
     * UNA transaccion porque {@code tg_transaccion_cuadrada} es DEFERRABLE: si la
     * cabecera se confirma sola, salta por no tener movimientos.
     */
    void acreditar(UUID cuentaId, BigDecimal monto) {
        tipoDeCambioDeHoy();
        UUID puente = puenteDeCustodia();
        dsl.transaction(config -> {
            var tx = org.jooq.impl.DSL.using(config);
            UUID transaccion = UUID.randomUUID();
            tx.execute(
                    """
                    INSERT INTO nucleo_financiero.transaccion_billetera
                        (id, tipo, estado, moneda, monto_total, origen_tipo, origen_id, canal,
                         clave_idempotencia, hash_registro, ocurrida_en, registrada_en)
                    VALUES (?, 'RECARGA', 'APLICADA', 'BOB', ?, 'ORDEN_RECARGA', gen_random_uuid(), 'API',
                            ?, repeat('a', 64), now(), now())
                    """,
                    transaccion,
                    monto,
                    "semilla-" + transaccion);
            tx.execute(
                    """
                    INSERT INTO nucleo_financiero.movimiento_billetera
                        (id, transaccion_id, cuenta_billetera_id, orden, sentido, monto,
                         saldo_disponible_posterior, saldo_retenido_posterior, glosa, registrado_en)
                    VALUES (gen_random_uuid(), ?, ?, 1, 'DEBITO', ?, 0, 0, 'Ingreso desde custodia', now())
                    """,
                    transaccion,
                    puente,
                    monto);
            tx.execute(
                    """
                    INSERT INTO nucleo_financiero.movimiento_billetera
                        (id, transaccion_id, cuenta_billetera_id, orden, sentido, monto,
                         saldo_disponible_posterior, saldo_retenido_posterior, glosa, registrado_en)
                    VALUES (gen_random_uuid(), ?, ?, 2, 'CREDITO', ?, ?, 0, 'Saldo de prueba', now())
                    """,
                    transaccion,
                    cuentaId,
                    monto,
                    monto);
        });
    }

    /**
     * El tipo de cambio del dia.
     *
     * <p>Una regla UIF convierte todo movimiento a dolares para compararlo con los
     * umbrales del instructivo, y sin cotizacion no puede: la operacion se rechaza
     * antes de escribirse. Es correcto —un umbral que no se puede evaluar no se
     * puede dar por cumplido— y por eso la fixtura la carga en vez de esquivarla.
     */
    void tipoDeCambioDeHoy() {
        dsl.execute(
                """
                INSERT INTO catalogo.tipo_cambio
                    (id, moneda_origen, moneda_destino, fecha, tipo_cambio, fuente, cargado_en)
                VALUES (gen_random_uuid(), 'BOB', 'USD', current_date, 6.96, 'BCB', now())
                ON CONFLICT DO NOTHING
                """);
    }

    /**
     * Lo unico que se puede borrar entre pruebas.
     *
     * <p>El libro **no se toca**: {@code movimiento_billetera} y
     * {@code transaccion_billetera} son append-only y R-AUD-01 lo hace cumplir con un
     * trigger. Tampoco hace falta — cada prueba abre sus propias cuentas, asi que lo
     * que quedo de la anterior no le suma ni le resta. Lo que si hay que limpiar es
     * lo unico por clave: los limites del catalogo chocarian entre pruebas.
     */
    /** Un cierre diario: es contra lo que el extracto y la conciliacion comparan. */
    void cierreDelDia(UUID cuentaId, java.time.LocalDate fecha, java.math.BigDecimal disponible, int movimientos) {
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.saldo_diario_billetera
                    (id, cuenta_billetera_id, fecha, saldo_disponible, saldo_retenido,
                     cantidad_movimientos, hash_registro, cerrado_en)
                VALUES (gen_random_uuid(), ?, ?, ?, 0, ?, repeat('c', 64), now())
                """,
                cuentaId,
                fecha,
                disponible,
                movimientos);
    }

    void limpiarBilleteras() {
        // `saldo_diario_billetera` y `certificado_saldo` NO se borran: son
        // append-only y R-AUD-01 lo hace cumplir. Tampoco hace falta — cada prueba
        // abre sus propias cuentas, asi que lo de la anterior no le suma ni le resta.
        dsl.execute("DELETE FROM nucleo_financiero.solicitud_cierre_billetera");
        dsl.execute("DELETE FROM nucleo_financiero.bloqueo_saldo");
        dsl.execute("DELETE FROM nucleo_financiero.transferencia_p2p");
        dsl.execute("DELETE FROM aportes.obligacion_aporte");
        dsl.execute("DELETE FROM grupos.periodo");
        dsl.execute("DELETE FROM grupos.cupo");
        dsl.execute("DELETE FROM grupos.participante");
        dsl.execute("DELETE FROM grupos.grupo");
        dsl.execute("DELETE FROM nucleo_financiero.orden_retiro");
        dsl.execute("DELETE FROM nucleo_financiero.instrumento_fondeo");
        dsl.execute("DELETE FROM nucleo_financiero.conciliacion_custodia");
        dsl.execute("DELETE FROM nucleo_financiero.cuenta_custodia");
        dsl.execute("DELETE FROM nucleo_financiero.consumo_limite");
        dsl.execute("DELETE FROM catalogo.limite_operativo_billetera");
        dsl.execute("DELETE FROM nucleo_financiero.evento_dominio");
    }
}
