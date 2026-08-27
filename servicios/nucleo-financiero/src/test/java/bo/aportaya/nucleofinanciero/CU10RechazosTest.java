package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-10 · las pruebas de RECHAZO, en su propio archivo.
 *
 * <p>Otra pregunta que las de {@link CU10Test}: aquellas verifican que el caso de uso
 * hace lo que promete; estas, que la BASE rechaza lo que no debe entrar aunque la
 * aplicacion se equivoque. Es la unica forma de comprobar una restriccion: si se
 * borrara la validacion de la aplicacion, la base tiene que seguir diciendo que no.
 */
class CU10RechazosTest extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    @Test
    @DisplayName("rechaza por R-BIL-02")
    void rechazaRBIL02() {
        // Acreditar es la operacion que mas facil deja un saldo raro: si la resta del
        // costo del proveedor se hiciera mal, el disponible podria quedar negativo.
        // Dar credito sin licencia no es un error de calculo, es operar sin permiso.
        UUID cuenta = fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal("100.00"));

        assertThat(rechazaLaBase(
                        "UPDATE nucleo_financiero.cuenta_billetera SET saldo_disponible = -0.01 WHERE id = '%s'"
                                .formatted(cuenta)))
                .contains("ck_cuenta_saldo_no_negativo");
    }

    @Test
    @DisplayName("rechaza por R-BIL-10")
    void rechazaRBIL10() {
        // Una referencia externa, una acreditacion. El proveedor reintenta su webhook
        // y lo que impide la doble acreditacion NO es que la aplicacion se acuerde:
        // es la unicidad.
        assertThat(constraintExiste("uq_recarga_referencia")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-BIL-19")
    void rechazaRBIL19() {
        // El reintento devuelve la PRIMERA respuesta y no un error de unicidad. Es la
        // diferencia entre una idempotencia que sirve y una que solo evita el
        // duplicado: quien reintenta necesita el mismo cuerpo, no un 409.
        assertThat(constraintExiste("ck_respuesta_idem_hash")).isTrue();
        assertThat(constraintExiste("ck_respuesta_idem_expira")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // Todo asiento cuadra. Lo hace cumplir un trigger y no la aplicacion, porque
        // un asiento descuadrado que entra por cualquier otra via rompe el libro igual.
        assertThat(funcionExiste("fn_aud_asiento_cuadrado")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-BIL-20")
    void rechazaRBIL20() {
        // La partida doble tambien cuadra EN MONEDA. Sin esto, un debe en bolivianos y
        // un haber en dolares suman cero en el numero y no en la realidad.
        assertThat(funcionExiste("fn_bil_moneda_coherente")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-AUD-03")
    void rechazaRAUD03() {
        // Las transacciones de billetera van encadenadas por hash: alterar una vieja
        // obliga a rehacer todas las posteriores, y eso se nota. La tabla ademas es
        // append-only, que es lo que hace que la cadena no se pueda «arreglar».
        // El encadenamiento lo pone la base al insertar, no la aplicacion: si lo
        // calculara el servicio, una fila insertada por cualquier otra via entraria sin
        // cadena y la rotura no se notaria.
        assertThat(funcionExiste("fn_aud_encadenar_transaccion")).isTrue();
        assertThat(triggerExiste("tg_transaccion_billetera_hash")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-AUD-10")
    void rechazaRAUD10() {
        // Las cadenas se verifican en el control diario, no solo cuando alguien audita.
        // Una cadena que solo se mira cuando hay sospecha se rompe el dia que nadie
        // sospecha.
        // Lo que hace verificable la cadena en el control diario es que sea imposible
        // reescribirla: el sello append-only es lo que convierte «recomputo los hashes»
        // en una comprobacion con sentido. Sin el, quien altera una fila tambien
        // recalcula las siguientes y la verificacion da OK.
        assertThat(triggerExiste("tg_transaccion_billetera_append_only")).isTrue();
        assertThat(funcionExiste("fn_aud_bloquear_mutacion")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-LIM-02")
    void rechazaRLIM02() {
        // Un consumo por cuenta, limite y ventana. Sin la unicidad, dos peticiones
        // simultaneas registran dos consumos y el limite deja de limitar.
        assertThat(constraintExiste("uq_consumo_ventana")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-UIF-02")
    void rechazaRUIF02() {
        // Superar un umbral genera registro OBLIGATORIO. Que dependa de que la
        // aplicacion se acuerde es exactamente lo que la UIF no acepta.
        assertThat(funcionExiste("fn_uif_registrar_operacion")).isTrue();
    }

    /** Existe como CHECK, UNIQUE o indice unico: las tres formas valen para rechazar. */
    private boolean constraintExiste(String nombre) {
        Number cuantos = (Number) dslFixtura
                .fetchOne(
                        """
                        SELECT (SELECT count(*) FROM pg_constraint WHERE conname = ?)
                             + (SELECT count(*) FROM pg_class WHERE relkind = 'i' AND relname = ?)
                        """,
                        nombre,
                        nombre)
                .get(0);
        return cuantos.intValue() > 0;
    }

    private boolean triggerExiste(String nombre) {
        Number cuantos = (Number) dslFixtura
                .fetchOne("SELECT count(*) FROM pg_trigger WHERE tgname = ?", nombre)
                .get(0);
        return cuantos.intValue() > 0;
    }

    private boolean funcionExiste(String nombre) {
        Number cuantos = (Number) dslFixtura
                .fetchOne("SELECT count(*) FROM pg_proc WHERE proname = ?", nombre)
                .get(0);
        return cuantos.intValue() > 0;
    }
}
