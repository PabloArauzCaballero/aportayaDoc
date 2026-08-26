package bo.aportaya.plataforma.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogoDeErroresTest {

    private final CatalogoDeErrores catalogo = CatalogoDeErrores.cargar();

    @Test
    @DisplayName("El catalogo sale de sql/ y no esta vacio")
    void elCatalogoSaleDeSql() {
        assertThat(catalogo.tamano()).isGreaterThan(100);
    }

    @Test
    @DisplayName("Una restriccion conocida se traduce a su regla de negocio")
    void traduceUnaRestriccionConocida() {
        assertThat(catalogo.reglaDe("uq_tarea_automatizada_clave")).contains("R-ORG-07");
    }

    @Test
    @DisplayName("Una restriccion que el catalogo no conoce no inventa una regla")
    void noInventaReglas() {
        assertThat(catalogo.reglaDe("uq_algo_que_no_existe")).isEmpty();
        assertThat(catalogo.reglaDe(null)).isEmpty();
    }
}
