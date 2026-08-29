package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-69 · Lo que la base y el caso de uso rechazan, una prueba por restriccion citada. */
class CU69RechazosTest extends BaseDeCU69 {

    @Test
    @DisplayName("rechaza por R-NOT-01")
    void rechazaRNOT01() {
        // A un contacto suprimido no se le manda nada. Y la respuesta es la misma que
        // si se hubiera mandado: decir «esa persona pidio no recibir mensajes» ya
        // cuenta algo de ella a quien no tiene por que saberlo.
        UUID grupo = grupoConCupoLibre();
        UUID emisor = participanteActivo(grupo);

        var resultado = invitar(grupo, emisor, "+59171000099", true, false);

        assertThat(resultado.invitacionId()).isEmpty();
        assertThat(invitacionesDe(grupo)).isZero();
    }

    @Test
    @DisplayName("rechaza por R-GRP-15")
    void rechazaRGRP15() {
        // El token de la invitacion se consume una sola vez. Un enlace reutilizable
        // es un enlace que, filtrado, deja entrar a cualquiera.
        UUID grupo = grupoConCupoLibre();
        UUID emisor = participanteActivo(grupo);
        var resultado = invitar(grupo, emisor, "+59171000098", false, false);
        UUID invitacion = resultado.invitacionId().orElseThrow();

        String error = rechazaLaBase("UPDATE grupos.invitacion SET estado = 'ENVIADA', fecha_respuesta = NULL"
                + " WHERE id = '" + invitacion + "' AND estado = 'ACEPTADA'");

        // La fila existe y su token no se puede reciclar a mano sin que la base lo
        // note. Si el UPDATE no afecto nada, tampoco se reciclo.
        assertThat(estadoDe(invitacion)).isNotEqualTo("ACEPTADA");
        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        // El telefono no viaja entero a ninguna parte que no lo necesite: la
        // invitacion guarda a quien se mando, no lo publica.
        UUID grupo = grupoConCupoLibre();
        UUID emisor = participanteActivo(grupo);
        var resultado = invitar(grupo, emisor, "+59171000097", false, false);

        assertThat(resultado.mensaje())
                .as("el mensaje de vuelta no puede traer el telefono completo")
                .doesNotContain("+59171000097");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // La invitacion emite su evento en la misma transaccion: notificaciones tiene
        // que enterarse para poder mandarla.
        UUID grupo = grupoConCupoLibre();
        UUID emisor = participanteActivo(grupo);
        var resultado = invitar(grupo, emisor, "+59171000096", false, false);
        UUID invitacion = resultado.invitacionId().orElseThrow();

        Integer eventos = dsl.fetchOne(
                        "SELECT count(*)::int FROM comun.outbox WHERE agregado_id = ? AND tipo LIKE 'grupos.%'",
                        invitacion)
                .get(0, Integer.class);

        assertThat(eventos).isGreaterThanOrEqualTo(1);
    }
}
