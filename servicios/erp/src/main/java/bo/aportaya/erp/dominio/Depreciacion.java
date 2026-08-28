package bo.aportaya.erp.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * La cuota de depreciacion de un periodo, por linea recta.
 *
 * <p>**Un activo nunca se deprecia por debajo de su valor residual.** La ultima cuota se
 * ajusta a lo que falta, aunque salga distinta de las anteriores: repetir la cuota
 * teorica hasta pasarse dejaria el activo con valor en libros negativo, y
 * {@code ck_activo_fijo_depreciacion} lo rechazaria — con razon, porque un activo que
 * vale menos que nada no existe.
 *
 * <p>Puro: la vida util y el metodo son catalogo ({@code categoria_activo_fijo}) y
 * llegan como datos.
 */
public final class Depreciacion {

    private Depreciacion() {}

    /**
     * @param acumulada lo ya depreciado
     * @param vidaUtilMeses de la categoria, siempre mayor que cero
     */
    public static Cuota cuotaMensual(
            BigDecimal costoAdquisicion, BigDecimal valorResidual, BigDecimal acumulada, int vidaUtilMeses) {

        BigDecimal depreciable = costoAdquisicion.subtract(valorResidual);
        BigDecimal pendiente = depreciable.subtract(acumulada);
        if (pendiente.signum() <= 0) {
            return new Cuota(BigDecimal.ZERO.setScale(2), true, acumulada);
        }
        BigDecimal teorica = depreciable.divide(BigDecimal.valueOf(vidaUtilMeses), 2, RoundingMode.HALF_EVEN);
        // La ultima cuota es lo que falta, no la teorica: es la diferencia entre cerrar
        // el activo en su valor residual exacto o pasarse por unos centavos y que la
        // base rechace la corrida entera.
        BigDecimal cuota = teorica.min(pendiente);
        BigDecimal nuevaAcumulada = acumulada.add(cuota);
        return new Cuota(cuota, nuevaAcumulada.compareTo(depreciable) >= 0, nuevaAcumulada);
    }

    /** El valor en libros: lo que el activo vale hoy segun los libros. */
    public static BigDecimal valorEnLibros(BigDecimal costoAdquisicion, BigDecimal acumulada) {
        return costoAdquisicion.subtract(acumulada).setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * @param totalmenteDepreciado cierto cuando el activo llego a su valor residual: de
     *     ahi en adelante no se deprecia mas, y las corridas siguientes no lo tocan
     */
    public record Cuota(BigDecimal monto, boolean totalmenteDepreciado, BigDecimal acumuladaNueva) {}
}
