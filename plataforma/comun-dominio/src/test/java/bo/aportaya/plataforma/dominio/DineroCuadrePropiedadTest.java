package bo.aportaya.plataforma.dominio;

import static bo.aportaya.plataforma.dominio.Moneda.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Las mismas reglas de cuadre, pero contra mil casos generados en vez de tres
 * elegidos a mano. Es donde aparece el centavo que el ejemplo bonito no tiene.
 *
 * <p>Solo {@code @Property} en esta clase: jqwik y JUnit Jupiter no conviven bien en
 * un mismo archivo y el sintoma es silencioso — las propiedades salen SALTADAS y el
 * build sigue verde.
 */
class DineroCuadrePropiedadTest {

    @Property(tries = 1000)
    @Label("Mil repartos: la suma de las partes siempre iguala el total")
    void milRepartosSiempreCuadran(
            @ForAll @IntRange(min = 1, max = 999_999) int centavos, @ForAll @IntRange(min = 1, max = 12) int partes) {
        Dinero total = enBolivianos(centavos);

        assertThat(sumar(Prorrateo.enPartesIguales(total, partes))).isEqualTo(total);
    }

    @Property(tries = 1000)
    @Label("Mil idas y vueltas: sumar y despues restar devuelve el original")
    void milIdasYVueltasVuelvenAlOrigen(
            @ForAll @IntRange(min = -999_999, max = 999_999) int centavos,
            @ForAll @IntRange(min = 0, max = 999_999) int delta) {
        Dinero original = enBolivianos(centavos);
        Dinero movimiento = enBolivianos(delta);

        assertThat(original.mas(movimiento).menos(movimiento)).isEqualTo(original);
    }

    @Property(tries = 1000)
    @Label("Mil prorrateos por pesos: ninguna parte se pierde ni se inventa")
    void milProrrateosPorPesosCuadran(
            @ForAll @IntRange(min = 1, max = 999_999) int centavos,
            @ForAll @IntRange(min = 1, max = 50) int pesoA,
            @ForAll @IntRange(min = 1, max = 50) int pesoB,
            @ForAll @IntRange(min = 1, max = 50) int pesoC) {
        Dinero total = enBolivianos(centavos);
        List<BigDecimal> pesos =
                List.of(BigDecimal.valueOf(pesoA), BigDecimal.valueOf(pesoB), BigDecimal.valueOf(pesoC));

        assertThat(sumar(Prorrateo.porPesos(total, pesos))).isEqualTo(total);
    }

    private Dinero enBolivianos(int centavos) {
        return Dinero.de(BigDecimal.valueOf(centavos).movePointLeft(Dinero.ESCALA), BOB);
    }

    private Dinero sumar(List<Dinero> partes) {
        return partes.stream().reduce(Dinero.cero(BOB), Dinero::mas);
    }
}
