package bo.aportaya.cumplimiento.dominio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Si una operacion cabe dentro de los limites de la cuenta.
 *
 * <p>**Denegar por omision** (R-LIM-01, invariante 9): un concepto sin limite
 * configurado para el nivel del usuario **se rechaza**, no se permite. Es la decision
 * incomoda y es la correcta: permitir lo no configurado significa que un olvido en el
 * catalogo abre la puerta de par en par, y ese olvido no se descubre hasta que alguien
 * lo aprovecha.
 *
 * <p>Y el mensaje dice **cuanto queda disponible**, no solo que no se puede. Alguien que
 * intenta retirar Bs 3.000 y tiene Bs 800 disponibles puede retirar 800 hoy; decirle
 * solo «excede el limite» lo deja sin saber que hacer.
 */
public final class EvaluacionDeLimites {

    private EvaluacionDeLimites() {}

    public static Veredicto evaluar(List<Tope> topes, BigDecimal monto) {
        if (topes.isEmpty()) {
            return new Veredicto(
                    false,
                    List.of(),
                    "No hay limite configurado para ese concepto y nivel: se deniega por omision (R-LIM-01).",
                    true);
        }

        var evaluados = new ArrayList<Evaluado>(topes.size());
        String motivo = null;
        for (Tope tope : topes) {
            BigDecimal disponible =
                    tope.montoMaximo() == null ? null : tope.montoMaximo().subtract(tope.consumido());
            evaluados.add(new Evaluado(tope.ventana(), tope.montoMaximo(), tope.consumido(), disponible));

            if (disponible != null && monto.compareTo(disponible) > 0 && motivo == null) {
                motivo = "El limite %s deja disponible %s y la operacion pide %s."
                        .formatted(tope.ventana(), disponible.toPlainString(), monto.toPlainString());
            }
            if (tope.cantidadMaxima() != null && tope.cantidadConsumida() >= tope.cantidadMaxima() && motivo == null) {
                motivo = "Se alcanzo el maximo de %d operaciones de la ventana %s."
                        .formatted(tope.cantidadMaxima(), tope.ventana());
            }
        }
        return new Veredicto(motivo == null, List.copyOf(evaluados), motivo, false);
    }

    /**
     * @param consumido lo ya consumido en la ventana vigente, **sin contar reversas**:
     *     un importe reversado no llego a ninguna parte, y contarlo contra el limite
     *     castigaria a alguien por un error nuestro
     */
    public record Tope(
            String concepto,
            String ventana,
            BigDecimal montoMaximo,
            Integer cantidadMaxima,
            BigDecimal consumido,
            int cantidadConsumida) {}

    public record Evaluado(String ventana, BigDecimal tope, BigDecimal consumido, BigDecimal disponible) {}

    /**
     * @param sinLimiteConfigurado separado de {@code permitido} porque tiene su propio
     *     codigo de error: no es «te pasaste», es «nadie configuro hasta donde podes»
     */
    public record Veredicto(
            boolean permitido, List<Evaluado> limitesEvaluados, String motivoRechazo, boolean sinLimiteConfigurado) {}
}
