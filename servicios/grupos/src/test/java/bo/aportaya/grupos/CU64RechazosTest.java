package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-64 · Lo que la base y el caso de uso rechazan, una prueba por restriccion citada. */
class CU64RechazosTest extends BaseDeCU64 {

    @Test
    @DisplayName("rechaza por R-GRP-11")
    void rechazaRGRP11() {
        // La deuda no se traspasa con el cupo. Si se fuera con el cupo, retirarse
        // dejando deuda seria gratis: se le pasa el problema a quien entra, que ni
        // siquiera estaba cuando se genero.
        var escenario = escenarioConTurno();

        assertThatThrownBy(() -> traspasar(escenario, false, "BASICO", 0)).isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName("rechaza por R-UIF-09")
    void rechazaRUIF09() {
        // Quien entra tiene que llegar al KYC minimo del grupo. Un cupo es una via de
        // entrada de dinero: dejar pasar a alguien por debajo del nivel exigido es
        // exactamente el hueco que la norma cierra.
        var escenario = escenarioConTurno();
        exigirKyc(escenario.grupo(), "INTERMEDIO");

        assertThatThrownBy(() -> traspasar(escenario, true, "BASICO", 0)).isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // El traspaso emite su evento en la misma transaccion: cambia quien responde
        // por un cupo, y eso lo miran cobranza y reputacion.
        var escenario = escenarioConTurno();
        var traspasoId = traspasar(escenario, true, "COMPLETO", 0);

        Integer eventos = dsl.fetchOne(
                        "SELECT count(*)::int FROM comun.outbox WHERE agregado_id = ? AND tipo LIKE 'grupos.%'",
                        traspasoId)
                .get(0, Integer.class);

        assertThat(eventos).isGreaterThanOrEqualTo(1);
    }
}
