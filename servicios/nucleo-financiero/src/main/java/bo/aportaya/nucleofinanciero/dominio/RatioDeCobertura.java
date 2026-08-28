package bo.aportaya.nucleofinanciero.dominio;

import bo.aportaya.plataforma.dominio.Dinero;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CU-50 · Cuanto respalda la custodia al dinero electronico emitido. Puro.
 *
 * <p>El encaje es la promesa central del producto: por cada boliviano que figura en
 * una billetera hay un boliviano guardado en el banco. Si el ratio baja de uno, hay
 * mas dinero prometido que dinero real, y ahi es donde una corrida se lleva a los
 * ultimos que llegan.
 */
public final class RatioDeCobertura {

    /** Seis decimales: es la escala de {@code conciliacion_custodia.ratio_cobertura}. */
    private static final int ESCALA = 6;

    private RatioDeCobertura() {}

    public record Resultado(BigDecimal ratio, Dinero diferencia, boolean cumpleEncaje) {}

    /**
     * @param emitido lo que suman las billeteras
     * @param custodia lo que hay en el banco
     * @param enTransito lo que salio de un lado y todavia no llego al otro
     */
    public static Resultado calcular(Dinero emitido, Dinero custodia, Dinero enTransito) {
        Dinero respaldo = custodia.mas(enTransito);
        Dinero diferencia = respaldo.menos(emitido);

        if (emitido.esCero()) {
            // Sin dinero emitido no hay nada que respaldar, y el encaje se cumple
            // trivialmente. Dividir daria por cero; devolver uno seria inventar un
            // ratio que nadie calculo.
            return new Resultado(BigDecimal.ONE.setScale(ESCALA), diferencia, true);
        }

        BigDecimal ratio = respaldo.monto().divide(emitido.monto(), ESCALA, RoundingMode.DOWN);
        // DOWN y no HALF_UP: redondear hacia arriba podria mostrar 1,000000 cuando
        // en realidad falta un centavo, y ese centavo es exactamente lo que la regla
        // existe para detectar.
        return new Resultado(ratio, diferencia, ratio.compareTo(BigDecimal.ONE) >= 0);
    }
}
