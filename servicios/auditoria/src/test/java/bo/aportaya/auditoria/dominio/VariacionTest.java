package bo.aportaya.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La variacion, probada donde duele: en el cero y en la ausencia.
 *
 * <p>Dividir por cero en un tablero es el error mas tonto y mas frecuente, y la
 * diferencia entre «no hay comparacion» y «no cambio» es la que decide si alguien se
 * preocupa.
 */
class VariacionTest {

    @Test
    @DisplayName("Un indicador nuevo no tiene variacion: no es cero, es que no hay con que comparar")
    void sinHistoriaNoHayVariacion() {
        assertThat(Variacion.entre(new BigDecimal("120"), Optional.empty())).isEmpty();
    }

    @Test
    @DisplayName("De cero a algo no es «infinito por ciento»: como porcentaje no significa nada")
    void desdeCeroNoHayPorcentaje() {
        assertThat(Variacion.entre(new BigDecimal("120"), Optional.of(BigDecimal.ZERO)))
                .isEmpty();
    }

    @Test
    @DisplayName("Subir de 100 a 120 es +20,00 %")
    void subida() {
        assertThat(Variacion.entre(new BigDecimal("120"), Optional.of(new BigDecimal("100"))))
                .contains(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("Bajar de 100 a 80 es -20,00 %: el signo se conserva")
    void bajada() {
        assertThat(Variacion.entre(new BigDecimal("80"), Optional.of(new BigDecimal("100"))))
                .contains(new BigDecimal("-20.00"));
    }

    @Test
    @DisplayName("Quedarse igual es cero, y eso SI se dice")
    void sinCambio() {
        assertThat(Variacion.entre(new BigDecimal("100"), Optional.of(new BigDecimal("100"))))
                .contains(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Un anterior negativo no da vuelta el signo de la variacion")
    void anteriorNegativo() {
        // De -100 a -50 el indicador MEJORO: la variacion es positiva. Dividir por el
        // valor con signo daria -50 %, que se leeria como que empeoro.
        assertThat(Variacion.entre(new BigDecimal("-50"), Optional.of(new BigDecimal("-100"))))
                .contains(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("La escala no cambia el resultado: 100 y 100.00 son el mismo valor")
    void laEscalaNoCuenta() {
        assertThat(Variacion.entre(new BigDecimal("110"), Optional.of(new BigDecimal("100.0000"))))
                .contains(new BigDecimal("10.00"));
    }
}
