package bo.aportaya.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Para que lado se cumple una meta. La mitad del tablero se pinta al reves sin esto. */
class SentidoDeMetaTest {

    @Test
    @DisplayName("Morosidad 7 % con meta 5 % NO cumple, aunque 7 sea mayor que 5")
    void menosEsMejor() {
        assertThat(SentidoDeMeta.MENOR_ES_MEJOR.cumple(new BigDecimal("7.00"), new BigDecimal("5.00")))
                .isFalse();
        assertThat(SentidoDeMeta.MENOR_ES_MEJOR.cumple(new BigDecimal("4.20"), new BigDecimal("5.00")))
                .isTrue();
    }

    @Test
    @DisplayName("Volumen aportado por encima de la meta cumple")
    void masEsMejor() {
        assertThat(SentidoDeMeta.MAYOR_ES_MEJOR.cumple(new BigDecimal("120"), new BigDecimal("100")))
                .isTrue();
        assertThat(SentidoDeMeta.MAYOR_ES_MEJOR.cumple(new BigDecimal("90"), new BigDecimal("100")))
                .isFalse();
    }

    @Test
    @DisplayName("Justo en la meta cumple, en los dos sentidos")
    void enLaMetaCumple() {
        assertThat(SentidoDeMeta.MAYOR_ES_MEJOR.cumple(new BigDecimal("100"), new BigDecimal("100")))
                .isTrue();
        assertThat(SentidoDeMeta.MENOR_ES_MEJOR.cumple(new BigDecimal("100"), new BigDecimal("100")))
                .isTrue();
    }

    @Test
    @DisplayName("Compara por valor y no por escala: 5 y 5.00 son la misma meta")
    void laEscalaNoDecide() {
        // Con `equals` esto fallaria: dos BigDecimal de distinta escala no son iguales
        // aunque valgan lo mismo (invariante 4).
        assertThat(SentidoDeMeta.MENOR_ES_MEJOR.cumple(new BigDecimal("5"), new BigDecimal("5.00")))
                .isTrue();
    }
}
