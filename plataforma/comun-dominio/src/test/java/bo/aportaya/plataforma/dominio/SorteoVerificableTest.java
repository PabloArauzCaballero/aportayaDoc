package bo.aportaya.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El sorteo, probado como lo probaria alguien que desconfia.
 *
 * <p>Es el punto de desconfianza numero uno del pasanaku: quien cobra primero recibe un
 * prestamo sin interes y quien cobra ultimo hace un ahorro forzado. Estas pruebas no
 * comprueban que el codigo corra, comprueban que la promesa de CU-61 se pueda cumplir.
 */
class SorteoVerificableTest {

    private static final List<String> ENTROPIAS = List.of("ana-7", "beto-13", "carla-42");
    private static final List<Integer> CUPOS =
            IntStream.rangeClosed(1, 12).boxed().toList();

    @Test
    @DisplayName("La semilla comprometida se recomputa igual, y una distinta no pasa")
    void elCompromisoSeVerifica() {
        String hash = SorteoVerificable.hashDelCompromiso("semilla-real", ENTROPIAS);

        assertThat(SorteoVerificable.verificarCompromiso("semilla-real", ENTROPIAS, hash))
                .isTrue();
        assertThat(SorteoVerificable.verificarCompromiso("semilla-cambiada", ENTROPIAS, hash))
                .isFalse();
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("Cambiar una entropia, agregarla o quitarla cambia el compromiso")
    void ningunaEntropiaEsDecorativa() {
        String original = SorteoVerificable.hashDelCompromiso("s", ENTROPIAS);

        assertThat(SorteoVerificable.hashDelCompromiso("s", List.of("ana-7", "beto-13", "carla-43")))
                .isNotEqualTo(original);
        assertThat(SorteoVerificable.hashDelCompromiso("s", List.of("ana-7", "beto-13")))
                .isNotEqualTo(original);
        assertThat(SorteoVerificable.hashDelCompromiso("s", List.of())).isNotEqualTo(original);
    }

    @Test
    @DisplayName("Dos paquetes distintos no pueden dar el mismo compromiso por como se concatenan")
    void laPreimagenEsCanonica() {
        // Sin separador, ("ab","c") y ("a","bc") producirian la misma preimagen: dos
        // repartos distintos de la misma cadena verificarian igual, y el sorteo tendria
        // una segunda semilla valida que nadie comprometio.
        assertThat(SorteoVerificable.hashDelCompromiso("s", List.of("ab", "c")))
                .isNotEqualTo(SorteoVerificable.hashDelCompromiso("s", List.of("a", "bc")));
    }

    @Test
    @DisplayName("El orden de las entropias forma parte del compromiso")
    void elOrdenDeLasEntropiasCuenta() {
        assertThat(SorteoVerificable.hashDelCompromiso("s", List.of("uno", "dos")))
                .isNotEqualTo(SorteoVerificable.hashDelCompromiso("s", List.of("dos", "uno")));
    }

    @Test
    @DisplayName("La misma semilla da siempre el mismo orden: el que verifica llega al mismo resultado")
    void elBarajadoEsDeterminista() {
        assertThat(SorteoVerificable.barajarDeterminista("semilla-real", CUPOS))
                .isEqualTo(SorteoVerificable.barajarDeterminista("semilla-real", CUPOS));
    }

    @Test
    @DisplayName("Otra semilla da otro orden")
    void otraSemillaOtroOrden() {
        assertThat(SorteoVerificable.barajarDeterminista("semilla-a", CUPOS))
                .isNotEqualTo(SorteoVerificable.barajarDeterminista("semilla-b", CUPOS));
    }

    @Test
    @DisplayName("El barajado es una permutacion: no pierde, no duplica y no inventa un cupo")
    void nadieSeQuedaSinTurno() {
        for (int i = 0; i < 200; i++) {
            List<Integer> orden = SorteoVerificable.barajarDeterminista("semilla-" + i, CUPOS);
            assertThat(orden).hasSameSizeAs(CUPOS).containsExactlyInAnyOrderElementsOf(CUPOS);
        }
    }

    @Test
    @DisplayName("No toca la lista que recibe ni devuelve una que se pueda editar despues")
    void elBarajadoEsPuro() {
        List<Integer> entrada = new java.util.ArrayList<>(CUPOS);
        List<Integer> orden = SorteoVerificable.barajarDeterminista("s", entrada);

        assertThat(entrada).isEqualTo(CUPOS);
        assertThatThrownBy(() -> orden.set(0, 99)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Un grupo de un solo cupo, o de ninguno, no rompe")
    void losBordesNoRompen() {
        assertThat(SorteoVerificable.barajarDeterminista("s", List.of())).isEmpty();
        assertThat(SorteoVerificable.barajarDeterminista("s", List.of(7))).containsExactly(7);
    }

    @Test
    @DisplayName("Sin semilla no hay sorteo ni compromiso: se rechaza, no se improvisa una")
    void sinSemillaNoHaySorteo() {
        assertThatThrownBy(() -> SorteoVerificable.barajarDeterminista(null, CUPOS))
                .isInstanceOf(ErrorDeDominio.class);
        assertThatThrownBy(() -> SorteoVerificable.hashDelCompromiso(null, ENTROPIAS))
                .isInstanceOf(ErrorDeDominio.class);
        assertThatThrownBy(() -> SorteoVerificable.barajarDeterminista("s", null))
                .isInstanceOf(ErrorDeDominio.class);
        assertThat(SorteoVerificable.verificarCompromiso(null, ENTROPIAS, "0")).isFalse();
        assertThat(SorteoVerificable.verificarCompromiso("s", ENTROPIAS, null)).isFalse();
    }

    @Test
    @DisplayName("Sin entropias tambien se puede comprometer: aportarlas es un derecho, no una obligacion")
    void sinEntropiasTambienSeSortea() {
        String hash = SorteoVerificable.hashDelCompromiso("s", null);

        assertThat(SorteoVerificable.verificarCompromiso("s", List.of(), hash)).isTrue();
    }

    @Test
    @DisplayName("Ningun turno queda cautivo de una posicion: en muchas semillas, cada cupo pasa por todas")
    void elRepartoNoTieneUnFavorito() {
        // No es una prueba de uniformidad estadistica, es la comprobacion de que no hay
        // un indice pegado. Un sorteo donde el cupo 1 nunca sale ultimo seria uniforme en
        // el papel y arreglado en la practica.
        int cupos = 6;
        List<Integer> lista = IntStream.rangeClosed(1, cupos).boxed().toList();
        boolean[][] visto = new boolean[cupos][cupos];

        for (int i = 0; i < 2_000; i++) {
            List<Integer> orden = SorteoVerificable.barajarDeterminista("semilla-" + i, lista);
            for (int posicion = 0; posicion < cupos; posicion++) {
                visto[orden.get(posicion) - 1][posicion] = true;
            }
        }

        for (int cupo = 0; cupo < cupos; cupo++) {
            assertThat(visto[cupo])
                    .as("el cupo %d no pasó por todas las posiciones", cupo + 1)
                    .containsOnly(true);
        }
    }
}
