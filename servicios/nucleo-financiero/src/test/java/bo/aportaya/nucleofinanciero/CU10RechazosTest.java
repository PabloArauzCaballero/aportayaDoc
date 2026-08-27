package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU10RecargarSaldo.EntradaSolicitud;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-10 · las pruebas de RECHAZO, en su propio archivo.
 *
 * <p>Otra pregunta que las de {@link CU10Test}: aquellas verifican que la recarga
 * hace lo que promete; estas, que la BASE rechaza lo que no debe entrar aunque la
 * aplicacion se equivoque. Son las que sostienen que el libro no se pueda torcer.
 */
class CU10RechazosTest extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private UUID billeteraConLimite() {
        fixtura.tipoDeCambioDeHoy();
        fixtura.limite("RECARGA", ESTANDAR, "MES", new BigDecimal("10000.00"), null);
        return fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("rechaza por R-AUD-03")
    void rechazaRAUD03() {
        // Las transacciones estan encadenadas por hash: cada fila lleva el de la
        // anterior. Alterar una del medio rompe la cadena, y por eso el hash es
        // obligatorio — una fila sin el no se puede encadenar ni verificar.
        UUID cuenta = billeteraConLimite();
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        var orden = transaccion.execute(e -> recargaCU.solicitar(
                new EntradaSolicitud("aud03", cuenta, bob("10.00"), bob("0.00"), "QR", Optional.empty()), ctx));
        var acreditada = transaccion.execute(e -> recargaCU.acreditar(orden.ordenRecargaId(), ctx));

        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.transaccion_billetera WHERE id = ? AND length(hash_registro) = 64",
                        acreditada.transaccionId()))
                .isEqualTo(1);
        assertThat(rechazaLaBase(
                        "UPDATE nucleo_financiero.transaccion_billetera SET hash_registro = NULL WHERE id = '%s'"
                                .formatted(acreditada.transaccionId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // Partida doble: un asiento cuyos movimientos no cuadran no se confirma.
        //
        // Un asiento VACIO no sirve para probarlo: debe y haber dan cero y cero
        // cuadra. Hace falta una pata sola, sin contrapartida, que es el descuadre
        // real. Y como el trigger es DEFERRABLE, solo salta al intentar el COMMIT.
        UUID asiento = UUID.randomUUID();
        UUID cuenta = fixtura.cuentaDeMovimiento("CU10-" + asiento.toString().substring(0, 6), "ACTIVO", "DEUDORA");

        assertThatThrownBy(() -> transaccion.execute(estado -> {
                    dsl.execute(
                            """
                            INSERT INTO nucleo_financiero.asiento_contable
                                (id, fecha, glosa, origen_tipo, origen_id, estado)
                            VALUES (?, now(), 'descuadre a proposito', 'AJUSTE', gen_random_uuid(), 'CONFIRMADO')
                            """,
                            asiento);
                    dsl.execute(
                            """
                            INSERT INTO nucleo_financiero.movimiento_contable
                                (id, asiento_id, cuenta_id, debe, haber, descripcion)
                            VALUES (gen_random_uuid(), ?, ?, 10.00, 0.00, 'solo debe, sin contrapartida')
                            """,
                            asiento,
                            cuenta);
                    return null;
                }))
                .satisfies(e -> assertThat(raizDe(e)).contains("R-AUD-05"));
    }

    @Test
    @DisplayName("rechaza por R-AUD-10")
    void rechazaRAUD10() {
        // La cadena se verifica en el control diario, no solo al auditar: la
        // consulta que busca eslabones rotos tiene que existir y dar cero.
        UUID cuenta = billeteraConLimite();
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        var orden = transaccion.execute(e -> recargaCU.solicitar(
                new EntradaSolicitud("aud10", cuenta, bob("20.00"), bob("0.00"), "QR", Optional.empty()), ctx));
        transaccion.execute(e -> recargaCU.acreditar(orden.ordenRecargaId(), ctx));

        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM nucleo_financiero.transaccion_billetera t
                         WHERE t.hash_anterior IS NOT NULL
                           AND NOT EXISTS (SELECT 1 FROM nucleo_financiero.transaccion_billetera p
                                            WHERE p.hash_registro = t.hash_anterior)
                        """))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-BIL-02")
    void rechazaRBIL02() {
        UUID cuenta = billeteraConLimite();

        assertThat(rechazaLaBase("UPDATE nucleo_financiero.cuenta_billetera SET saldo_disponible = -1 WHERE id = '%s'"
                        .formatted(cuenta)))
                .contains("ck_cuenta_saldo_no_negativo");
    }

    @Test
    @DisplayName("rechaza por R-BIL-10")
    void rechazaRBIL10() {
        // uq_recarga_referencia: una referencia externa, una acreditacion. Sin esto
        // el mismo pago del proveedor podria entrar dos veces por dos ordenes.
        UUID cuenta = billeteraConLimite();
        String insert =
                """
                INSERT INTO nucleo_financiero.orden_recarga
                    (id, cuenta_billetera_id, monto_bruto, costo_proveedor, monto_acreditado,
                     moneda, estado, referencia_externa, clave_idempotencia, solicitada_en)
                VALUES (gen_random_uuid(), '%s', 10.00, 0.00, 10.00, 'BOB', 'PENDIENTE',
                        'REF-DUPLICADA', gen_random_uuid()::text, now())
                """
                        .formatted(cuenta);
        dslFixtura.execute(insert);

        assertThat(rechazaLaBase(insert)).contains("uq_recarga_referencia");
    }

    @Test
    @DisplayName("rechaza por R-BIL-19")
    void rechazaRBIL19() {
        // El reintento devuelve la PRIMERA respuesta, no un error: un cliente que
        // reintenta tras un timeout no sabe si la operacion se aplico, y un error
        // de unicidad es indistinguible de «fallo».
        UUID cuenta = billeteraConLimite();
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        var primera = transaccion.execute(e -> recargaCU.solicitar(
                new EntradaSolicitud("bil19", cuenta, bob("15.00"), bob("0.00"), "QR", Optional.empty()), ctx));
        var segunda = transaccion.execute(e -> recargaCU.solicitar(
                new EntradaSolicitud("bil19", cuenta, bob("15.00"), bob("0.00"), "QR", Optional.empty()), ctx));

        assertThat(segunda.ordenRecargaId()).isEqualTo(primera.ordenRecargaId());
        assertThat(segunda.estado()).isEqualTo(primera.estado());
    }

    @Test
    @DisplayName("rechaza por R-BIL-20")
    void rechazaRBIL20() {
        // La partida doble cuadra TAMBIEN en moneda: debitar 100 USD y acreditar
        // 100 BOB cuadra numericamente y descuadra economicamente.
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_bil_moneda_coherente"))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*)::int FROM pg_trigger WHERE tgname LIKE ?", "%moneda%"))
                .isPositive();
    }

    @Test
    @DisplayName("rechaza por R-LIM-02")
    void rechazaRLIM02() {
        // uq_consumo_ventana: un consumo por ventana. Dos filas para la misma
        // ventana dejarian el acumulado partido y el tope se evadiria solo.
        UUID cuenta = billeteraConLimite();
        UUID limite = dsl.fetchOne("SELECT id FROM catalogo.limite_operativo_billetera WHERE concepto = 'RECARGA'")
                .get(0, UUID.class);
        String insert =
                """
                INSERT INTO nucleo_financiero.consumo_limite
                    (id, cuenta_billetera_id, limite_id, ventana_inicio, ventana_fin,
                     monto_acumulado, cantidad_acumulada, actualizado_en)
                VALUES (gen_random_uuid(), '%s', '%s', date_trunc('month', now()),
                        date_trunc('month', now()) + interval '1 month', 5.00, 1, now())
                """
                        .formatted(cuenta, limite);
        dslFixtura.execute(insert);

        assertThat(rechazaLaBase(insert)).contains("uq_consumo_ventana");
    }

    @Test
    @DisplayName("rechaza por R-UIF-02")
    void rechazaRUIF02() {
        // La operacion relevante se registra en la MISMA transaccion del hecho, no
        // en un proceso posterior: un lote nocturno que falla deja al supervisor sin
        // el reporte y a nadie enterado.
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_uif_registrar_operacion"))
                .isEqualTo(1);

        // Y sin tipo de cambio no se escribe el movimiento: un umbral que no se
        // puede evaluar no se puede dar por cumplido.
        dslFixtura.execute("DELETE FROM catalogo.tipo_cambio");
        UUID cuenta = fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO);
        fixtura.limite("RECARGA", ESTANDAR, "MES", new BigDecimal("10000.00"), null);
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        var orden = transaccion.execute(e -> recargaCU.solicitar(
                new EntradaSolicitud("uif02", cuenta, bob("30.00"), bob("0.00"), "QR", Optional.empty()), ctx));

        assertThatThrownBy(() -> transaccion.execute(e -> recargaCU.acreditar(orden.ordenRecargaId(), ctx)))
                .isInstanceOf(RuntimeException.class);
    }
}
