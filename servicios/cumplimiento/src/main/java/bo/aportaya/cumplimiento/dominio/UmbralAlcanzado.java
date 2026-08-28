package bo.aportaya.cumplimiento.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Si una operacion alcanza un umbral, y con que monto.
 *
 * <p>Puro y compartido por CU-41 y CU-42: **una sola implementacion para el PCC-01 y
 * para los ROG**. Si cada formulario tuviera su propio calculo, el dia que difirieran
 * nadie podria decir cual de los dos esta bien, y los dos se reportan al mismo
 * organismo.
 *
 * <p>La conversion a dolares guarda el tipo de cambio aplicado (R-UIF-04): sin el, el
 * mismo registro releido el año que viene daria otro monto y el reporte dejaria de ser
 * reproducible.
 */
public final class UmbralAlcanzado {

    private UmbralAlcanzado() {}

    /**
     * @param acumuladoPrevio lo que ya suma la ventana SIN esta operacion
     * @return el monto medido contra el umbral, o vacio si no lo alcanza
     */
    public static Medicion medir(
            BigDecimal montoUsd, BigDecimal acumuladoPrevio, BigDecimal umbralUsd, boolean esAcumulado) {

        BigDecimal medido = esAcumulado ? acumuladoPrevio.add(montoUsd) : montoUsd;
        // Un umbral en cero significa «sin umbral»: la operacion se reporta siempre, sin
        // importar el monto (ROG-01 y ROG-02 son asi por norma). El >= lo cubre solo.
        return new Medicion(medido.compareTo(umbralUsd) >= 0, medido);
    }

    /** El equivalente en dolares, con el tipo de cambio que se va a guardar. */
    public static BigDecimal aUsd(BigDecimal monto, String moneda, BigDecimal tipoDeCambio) {
        if ("USD".equals(moneda)) {
            return monto.setScale(2, RoundingMode.HALF_EVEN);
        }
        if (tipoDeCambio == null || tipoDeCambio.signum() <= 0) {
            throw new IllegalArgumentException("Sin tipo de cambio no hay conversion reproducible (R-UIF-04)");
        }
        return monto.multiply(tipoDeCambio).setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * Desde cuando corre la ventana.
     *
     * <p>Empieza al dia siguiente de la ultima operacion que supero el umbral, o hace
     * {@code ventanaDias - 1} dias, lo que sea mas reciente. **El reinicio es la parte
     * que importa** (R-UIF-03): sin el, quien cruzo el umbral una vez lo cruzaria todos
     * los dias por arrastre y el formulario dejaria de señalar el hecho para señalar a
     * la persona.
     */
    public static LocalDate ventanaDesde(LocalDate hoy, int ventanaDias, LocalDate ultimaQueSupero) {
        LocalDate porVentana = hoy.minusDays(ventanaDias - 1L);
        if (ultimaQueSupero == null) {
            return porVentana;
        }
        LocalDate porReinicio = ultimaQueSupero.plusDays(1);
        return porReinicio.isAfter(porVentana) ? porReinicio : porVentana;
    }

    public record Medicion(boolean alcanza, BigDecimal montoMedido) {}
}
