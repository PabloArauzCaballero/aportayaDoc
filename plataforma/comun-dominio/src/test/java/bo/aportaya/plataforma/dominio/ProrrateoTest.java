package bo.aportaya.plataforma.dominio;

import static bo.aportaya.plataforma.dominio.Moneda.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProrrateoTest {

    @Test
    @DisplayName("El centavo cae donde lo pone el redondeo acumulado, y es determinista")
    void elCentavoCaeDondeLoPoneElRedondeoAcumulado() {
        List<Dinero> partes = Prorrateo.enPartesIguales(Dinero.de("10.00", BOB), 3);

        assertThat(partes).containsExactly(Dinero.de("3.33", BOB), Dinero.de("3.34", BOB), Dinero.de("3.33", BOB));
        assertThat(Prorrateo.enPartesIguales(Dinero.de("10.00", BOB), 3)).isEqualTo(partes);
    }

    @Test
    @DisplayName("Ninguna parte se aleja mas de un centavo de su proporcion justa")
    void ningunaParteSeAlejaMasDeUnCentavo() {
        List<Dinero> partes = Prorrateo.enPartesIguales(Dinero.de("90.00", BOB), 3);

        assertThat(partes).containsExactly(Dinero.de("30.00", BOB), Dinero.de("30.00", BOB), Dinero.de("30.00", BOB));
    }

    @Test
    @DisplayName("Reparte proporcional a los pesos")
    void reparteProporcionalALosPesos() {
        List<Dinero> partes = Prorrateo.porPesos(
                Dinero.de("100.00", BOB), List.of(new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("2")));

        assertThat(partes).containsExactly(Dinero.de("25.00", BOB), Dinero.de("25.00", BOB), Dinero.de("50.00", BOB));
    }

    @Test
    @DisplayName("Reparto exacto: sin residuo, la primera no recibe de mas")
    void repartoExactoNoAgregaResiduo() {
        assertThat(Prorrateo.enPartesIguales(Dinero.de("90.00", BOB), 3))
                .containsExactly(Dinero.de("30.00", BOB), Dinero.de("30.00", BOB), Dinero.de("30.00", BOB));
    }

    @Test
    @DisplayName("Un importe negativo tambien se reparte, y tambien cuadra")
    void repartoDeUnImporteNegativo() {
        List<Dinero> partes = Prorrateo.enPartesIguales(Dinero.de("-10.00", BOB), 3);

        assertThat(partes.stream().reduce(Dinero.cero(BOB), Dinero::mas)).isEqualTo(Dinero.de("-10.00", BOB));
    }

    @Test
    @DisplayName("No se reparte entre cero partes ni con pesos que suman cero")
    void repartosImposibles() {
        Dinero total = Dinero.de("10.00", BOB);

        assertThatThrownBy(() -> Prorrateo.enPartesIguales(total, 0)).isInstanceOf(ErrorDeDominio.class);
        assertThatThrownBy(() -> Prorrateo.porPesos(total, List.of())).isInstanceOf(ErrorDeDominio.class);
        assertThatThrownBy(() -> Prorrateo.porPesos(total, List.of(BigDecimal.ZERO)))
                .isInstanceOf(ErrorDeDominio.class);
    }
}
