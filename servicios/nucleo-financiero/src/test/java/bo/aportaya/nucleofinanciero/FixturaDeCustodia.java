package bo.aportaya.nucleofinanciero;

import java.util.UUID;
import org.jooq.DSLContext;

/**
 * Lo que la billetera toca del mundo de afuera: la cuenta de destino y la custodia.
 *
 * <p>Separado de {@link FixturaDeBilletera} porque son otro asunto — el instrumento
 * es del titular y vive en el banco; la custodia es de la empresa y responde ante el
 * supervisor. Tenerlos juntos con el saldo obligaba a leer trescientas lineas para
 * entender de donde salia un retiro.
 */
final class FixturaDeCustodia {

    private final DSLContext dsl;

    FixturaDeCustodia(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Un instrumento de destino habilitado para retirar.
     *
     * <p>`bloqueadoHasta` es la ventana de enfriamiento: existe para que agregar una
     * cuenta ajena y vaciar la billetera no sea una sola maniobra.
     */
    UUID instrumentoDestino(UUID usuarioId, boolean verificado, boolean titularCoincide, Integer horasDeBloqueo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.instrumento_fondeo
                    (id, usuario_id, tipo, entidad_financiera, hash_identificador, enmascarado,
                     titular_nombre, titular_documento, titular_coincide, moneda, es_principal,
                     estado_verificacion, verificado_en, bloqueado_hasta)
                VALUES (?, ?, 'CUENTA_BANCARIA', 'BNB', ?, '****1234', 'Prueba Titular', 'CI-1234',
                        ?, 'BOB', true, ?, now(),
                        CASE WHEN CAST(? AS INTEGER) IS NULL THEN NULL
                             ELSE now() + (CAST(? AS INTEGER) || ' hours')::interval END)
                """,
                id,
                usuarioId,
                java.util
                        .UUID
                        .randomUUID()
                        .toString()
                        .replace("-", "")
                        .repeat(2)
                        .substring(0, 64),
                titularCoincide,
                verificado ? "VERIFICADO" : "PENDIENTE",
                horasDeBloqueo,
                horasDeBloqueo);
        return id;
    }

    /** Una custodia que cumple encaje: sin ella, R-BIL-11b frena todo retiro. */
    void cumpleEncaje() {
        UUID cuenta = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_custodia
                    (id, tipo, entidad_financiera, numero_cuenta_cifrado, version_llave,
                     numero_enmascarado, moneda, saldo_segun_banco, saldo_segun_libro, fecha_saldo,
                     contrato_referencia, es_principal, estado, abierta_en)
                VALUES (?, 'FIDEICOMISO', 'BNB', 'cifrado', 1, '****9999', 'BOB',
                        1000000.00, 1000000.00, current_date, 'CTR-1', true, 'ACTIVA', now())
                """,
                cuenta);
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.conciliacion_custodia
                    (id, cuenta_custodia_id, fecha, saldo_dinero_electronico, saldo_custodia,
                     saldo_en_transito, cumple_encaje, estado, ejecutada_en)
                VALUES (gen_random_uuid(), ?, current_date, 1000000.00, 1000000.00, 0,
                        true, 'CUADRADA', now())
                ON CONFLICT DO NOTHING
                """,
                cuenta);
    }

    /** Una custodia que NO cumple encaje: con eso R-BIL-11b frena los retiros. */
    void noCumpleEncaje() {
        UUID cuenta = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_custodia
                    (id, tipo, entidad_financiera, numero_cuenta_cifrado, version_llave,
                     numero_enmascarado, moneda, saldo_segun_banco, saldo_segun_libro, fecha_saldo,
                     contrato_referencia, es_principal, estado, abierta_en)
                VALUES (?, 'CUENTA_ENCAJE', 'BNB', 'cifrado', 1, '****0000', 'BOB',
                        1.00, 1000000.00, current_date, 'CTR-2', false, 'ACTIVA', now())
                """,
                cuenta);
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.conciliacion_custodia
                    (id, cuenta_custodia_id, fecha, saldo_dinero_electronico, saldo_custodia,
                     saldo_en_transito, cumple_encaje, estado, ejecutada_en)
                VALUES (gen_random_uuid(), ?, current_date, 1000000.00, 1.00, 0,
                        false, 'DESCUADRADA', now())
                """,
                cuenta);
    }
}
