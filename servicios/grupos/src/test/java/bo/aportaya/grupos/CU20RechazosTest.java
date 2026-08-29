package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-20 · Lo que la base y el caso de uso rechazan, una prueba por restriccion citada. */
class CU20RechazosTest extends BaseDeCU20 {

    @Test
    @DisplayName("rechaza por R-LIC-01")
    void rechazaRLIC01() {
        // Sin licencia vigente que cubra el servicio, no se crea el grupo. Denegar por
        // omision (invariante 9): operar sin licencia no es un riesgo de negocio, es
        // operar fuera de la norma.
        assertThatThrownBy(this::crearSinLicencia).isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName("rechaza por R-TAR-07")
    void rechazaRTAR07() {
        // Sin tarifario vigente no hay grupo. Crearlo sin tarifario congelado dejaria
        // el precio abierto a cambiar despues de que la gente ya entro, que es
        // exactamente lo que congelarlo evita.
        assertThatThrownBy(this::crearSinTarifario).isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName("rechaza por R-GRP-04")
    void rechazaRGRP04() {
        // La cuenta del grupo no tiene titular persona. Si lo tuviera, el fondo de
        // todos figuraria como plata de uno — y en un embargo o una sucesion, se la
        // llevaria.
        UUID tarifario = UUID.randomUUID();
        var salida = crear(tarifario);

        Integer conTitular = dsl.fetchOne(
                        "SELECT count(*)::int FROM grupos.grupo WHERE id = ? AND es_autogestionado IS NULL",
                        salida.grupoId())
                .get(0, Integer.class);

        assertThat(conTitular).isZero();
    }

    @Test
    @DisplayName("rechaza por R-CON-07")
    void rechazaRCON07() {
        // El tarifario queda congelado en el grupo: la fila guarda cual se aplico. Sin
        // eso, el precio de un pasanaku cambiaria al cambiar el tarifario general.
        UUID tarifario = UUID.randomUUID();
        var salida = crear(tarifario);

        Object congelado = dsl.fetchOne("SELECT tarifario_id FROM grupos.grupo WHERE id = ?", salida.grupoId())
                .get(0);

        assertThat(congelado).isEqualTo(tarifario);
    }
}
