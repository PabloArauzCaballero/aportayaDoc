package bo.aportaya.nucleofinanciero.dominio;

import bo.aportaya.plataforma.dominio.Dinero;
import java.util.List;
import java.util.Optional;

/**
 * CU-40 · Compara acumulado mas monto contra el tope. Puro.
 *
 * <p>**Nunca compara contra NULL.** Un tope sin configurar no es «infinito»: es la
 * ausencia de una decision, y la ausencia de una decision se resuelve denegando
 * (invariante 9). Tratarlo como sin limite convertiria un olvido de configuracion en
 * una billetera sin techo.
 */
public final class EvaluacionDeTope {

    private EvaluacionDeTope() {}

    /** Un limite vigente y lo que va consumido de el. */
    public record Tope(
            String concepto,
            String ventana,
            Optional<Dinero> montoMaximo,
            Optional<Integer> cantidadMaxima,
            Dinero consumido,
            int cantidadConsumida) {

        public Dinero disponible() {
            return montoMaximo.map(max -> max.menos(consumido)).orElse(Dinero.cero(consumido.moneda()));
        }
    }

    public record Resultado(boolean permitido, List<Tope> evaluados, String motivoRechazo) {}

    /**
     * @param topes los limites vigentes para el concepto y el nivel de la cuenta. Si
     *     llega vacio, se deniega: es exactamente lo que hace {@code fn_lim_evaluar}.
     */
    public static Resultado evaluar(List<Tope> topes, Dinero monto) {
        if (topes.isEmpty()) {
            return new Resultado(
                    false, List.of(), "No hay limite configurado para ese concepto y nivel: se deniega por omision.");
        }

        for (Tope tope : topes) {
            if (tope.montoMaximo().isPresent()) {
                Dinero proyectado = tope.consumido().mas(monto);
                if (proyectado.esMayorQue(tope.montoMaximo().get())) {
                    return new Resultado(
                            false,
                            topes,
                            "Supera el limite " + tope.concepto() + " por " + tope.ventana() + ": queda "
                                    + tope.disponible() + ".");
                }
            }
            if (tope.cantidadMaxima().isPresent()
                    && tope.cantidadConsumida() + 1 > tope.cantidadMaxima().get()) {
                return new Resultado(
                        false, topes, "Supera la cantidad maxima de operaciones de la ventana " + tope.ventana() + ".");
            }
        }
        return new Resultado(true, topes, null);
    }
}
