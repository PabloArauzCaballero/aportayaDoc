package bo.aportaya.plataforma.dominio;

import static bo.aportaya.plataforma.dominio.Moneda.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las pruebas de cuadre de {@code dinero-decimal}. La pregunta que contestan no es
 * «funciona la suma» sino «se puede perder un centavo en algun lado».
 *
 * <p>Las de propiedad viven aparte, en {@link DineroCuadrePropiedadTest}: mezclar
 * {@code @Test} y {@code @Property} en una clase hace que jqwik reporte sus
 * propiedades como SALTADAS, y una prueba saltada con el build en verde es peor que
 * no tenerla.
 */
class DineroCuadreTest {

    @Test
    @DisplayName("Una transaccion equilibrada cuadra en 0.00")
    void laTransaccionEquilibradaCuadraEnCero() {
        Dinero debito = Dinero.de("500.00", BOB);
        Dinero credito = debito.negado();

        assertThat(debito.mas(credito)).isEqualTo(Dinero.cero(BOB));
    }

    @Test
    @DisplayName("El prorrateo asigna el residuo: la suma de las partes iguala el total")
    void elProrrateoNoPierdeElResiduo() {
        Dinero total = Dinero.de("100.00", BOB);

        List<Dinero> partes = Prorrateo.enPartesIguales(total, 3);

        assertThat(partes).containsExactly(Dinero.de("33.33", BOB), Dinero.de("33.34", BOB), Dinero.de("33.33", BOB));
        assertThat(sumar(partes)).isEqualTo(total);
    }

    private Dinero sumar(List<Dinero> partes) {
        return partes.stream().reduce(Dinero.cero(BOB), Dinero::mas);
    }
}
