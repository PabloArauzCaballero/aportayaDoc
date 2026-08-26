package bo.aportaya.nucleofinanciero.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Mil asientos generados en vez de tres elegidos a mano — jqwik y JUnit Jupiter no
 * conviven en el mismo archivo (ver {@code DineroCuadrePropiedadTest}), por eso vive
 * separado de {@code CuadrarPartidasTest}.
 */
class CuadrarPartidasPropiedadTest {

    /** Con este piso y like máximo de 20 partes, cada línea recibe al menos 1 centavo: nunca hace falta rellenar. */
    private static final int CENTAVOS_TOTAL_MIN = 2_000;

    @Property(tries = 1000)
    @Label("Mil asientos: cualquier reparto del mismo total entre debe y haber cuadra")
    void cualquierRepartoCuadra(
            @ForAll @IntRange(min = CENTAVOS_TOTAL_MIN, max = 999_999) int centavosTotal,
            @ForAll @IntRange(min = 1, max = 20) int partesDebe,
            @ForAll @IntRange(min = 1, max = 20) int partesHaber) {
        BigDecimal total = BigDecimal.valueOf(centavosTotal).movePointLeft(2);

        List<Partida> partidas = new ArrayList<>();
        partidas.addAll(repartir(total, partesDebe, true));
        partidas.addAll(repartir(total, partesHaber, false));

        var totales = CuadrarPartidas.verificar(partidas);

        assertThat(totales.debe()).isEqualTo(totales.haber());
    }

    /**
     * Reparte {@code total} en {@code partes} líneas de un solo lado, sin perder ni
     * inventar centavos: el residuo entero de la división va todo a la primera línea,
     * así que ninguna línea queda en cero (el total mínimo lo garantiza).
     */
    private List<Partida> repartir(BigDecimal total, int partes, boolean esDebe) {
        BigDecimal porParte = total.divideToIntegralValue(BigDecimal.valueOf(partes));
        BigDecimal residuo = total.subtract(porParte.multiply(BigDecimal.valueOf(partes)));

        List<Partida> lineas = new ArrayList<>();
        for (int i = 0; i < partes; i++) {
            BigDecimal monto = porParte.add(i == 0 ? residuo : BigDecimal.ZERO);
            String codigo = (esDebe ? "1.1." : "2.1.") + String.format("%02d", i);
            lineas.add(
                    esDebe ? new Partida(codigo, monto, BigDecimal.ZERO) : new Partida(codigo, BigDecimal.ZERO, monto));
        }
        return lineas;
    }
}
