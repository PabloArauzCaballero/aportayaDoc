package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-68 · Lo que la base y el caso de uso rechazan, una prueba por restriccion citada. */
class CU68RechazosTest extends BaseDeCU68 {

    @BeforeEach
    void criterio() {
        criterioVigente();
    }

    @Test
    @DisplayName("rechaza por R-UIF-09")
    void rechazaRUIF09() {
        // Sin el KYC que el grupo exige no se postula. Un cupo es una via de entrada
        // de dinero, y dejar pasar por debajo del nivel exigido es el hueco que la
        // norma cierra.
        UUID grupo = grupoConCupoLibre();

        assertThatThrownBy(() -> postular(grupo, false, false, 80, 0)).isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        // Quien esta restringido no entra a un grupo nuevo. La restriccion existe
        // justamente para que no siga sumando obligaciones que no puede pagar.
        UUID grupo = grupoConCupoLibre();

        assertThatThrownBy(() -> postular(grupo, true, true, 80, 0)).isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName("rechaza por R-GRP-14")
    void rechazaRGRP14() {
        // Una solicitud pendiente por usuario y grupo. Dos postulaciones vivas de la
        // misma persona al mismo grupo le dan dos oportunidades donde los demas
        // tienen una.
        UUID grupo = grupoConCupoLibre();
        var primera = postular(grupo, false, true, 80, 0);

        String error = rechazaLaBase("INSERT INTO grupos.solicitud_ingreso (id, grupo_id, usuario_id, estado,"
                + " puntaje_afinidad, solicitado_en) SELECT gen_random_uuid(), grupo_id, usuario_id, 'PENDIENTE',"
                + " puntaje_afinidad, now() FROM grupos.solicitud_ingreso WHERE id = '" + primera.solicitudId() + "'");

        assertThat(error).isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-GRP-15")
    void rechazaRGRP15() {
        // No hay postulacion a un grupo sin cupos libres. Aceptarla seria una lista de
        // espera que nadie prometio administrar.
        UUID lleno = grupoSinCuposLibres();

        assertThatThrownBy(() -> postular(lleno, false, true, 80, 0)).isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        dejarUnaFilaEnLaBitacora();

        String error = rechazaLaBase("DELETE FROM comun.bitacora_evento WHERE entidad LIKE 'prueba%'");

        assertThat(error).contains("R-AUD-01");
    }
}
