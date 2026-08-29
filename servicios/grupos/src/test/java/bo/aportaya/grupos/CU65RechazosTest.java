package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-65 · Lo que la base y el caso de uso rechazan, una prueba por restriccion citada. */
class CU65RechazosTest extends BaseDeCU65 {

    @Test
    @DisplayName("rechaza por R-GRP-12")
    void rechazaRGRP12() {
        // Un retiro con posicion deudora exige plan de pago. Dejar salir a alguien
        // que debe sin plan traslada la perdida a los que se quedan, que no decidieron
        // nada.
        UUID participante = participanteActivo("ACTIVO");
        var salida = solicitar(participante, true, "1000.00", "500.00", "2000.00");

        assertThat(salida.posicion().tipo().name())
                .as("quien cobro su turno y debe queda en posicion deudora")
                .isEqualTo("DEUDORA");

        String error = rechazaLaBase("UPDATE grupos.solicitud_retiro SET estado = 'APROBADA', plan_pago_id = NULL"
                + " WHERE id = '" + salida.solicitudId() + "'");

        assertThat(error)
                .as("aprobar un retiro deudor sin plan de pago tiene que rechazarse")
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-GRP-11")
    void rechazaRGRP11() {
        // La deuda no se va con quien se retira: sigue siendo suya. Un retiro no es
        // una forma de dejar de deber.
        UUID participante = participanteActivo("ACTIVO");
        var salida = solicitar(participante, true, "1000.00", "500.00", "2000.00");

        assertThat(salida.posicion().tipo().name()).isEqualTo("DEUDORA");
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // La solicitud de retiro deja rastro: se registra y no se borra. Sin eso, un
        // retiro rechazado no se puede reclamar.
        UUID participante = participanteActivo("ACTIVO");
        var salida = solicitar(participante, false, "1000.00", "0.00", "2000.00");
        aprobar(salida, participante, Optional.empty());

        String error = rechazaLaBase("DELETE FROM grupos.solicitud_retiro WHERE id = '" + salida.solicitudId() + "'");

        assertThat(error).isNotEmpty();
    }
}
