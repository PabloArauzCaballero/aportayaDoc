package bo.aportaya.aportes.dominio;

import bo.aportaya.plataforma.dominio.Dinero;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CU-21 · Cuanto recargo corresponde por atrasarse. Puro.
 *
 * <p>El recargo tiene **tope**, y no es un detalle: sin el, un atraso largo genera una
 * deuda que crece sola hasta volverse impagable, y una deuda impagable no la cobra
 * nadie — el grupo pierde igual y ademas pierde al participante.
 *
 * <p>Los dias de gracia se cuentan antes: dentro de ellos el recargo es cero. Cobrar
 * desde el primer dia castigaria a quien paga un dia tarde igual que a quien no paga.
 */
public final class RecargoDeMora {

    private RecargoDeMora() {}

    public enum Tipo {
        FIJO,
        PORCENTUAL,
        PORCENTUAL_DIARIO
    }

    /** La politica del grupo, tal como la guarda {@code politica_mora}. */
    public record Politica(
            int diasGracia,
            Tipo tipo,
            BigDecimal valor,
            Dinero tope,
            int diasParaMoraGrave,
            int diasParaIncumplimiento) {}

    public record Calculo(Dinero recargo, int diasDeMora, String severidad) {}

    /**
     * @param diasDeAtraso desde el vencimiento, ya calculados por quien llama
     */
    public static Calculo calcular(Dinero montoOriginal, int diasDeAtraso, Politica politica) {
        int mora = Math.max(0, diasDeAtraso - politica.diasGracia());
        if (mora == 0) {
            return new Calculo(Dinero.cero(montoOriginal.moneda()), 0, "AL_DIA");
        }

        Dinero bruto =
                switch (politica.tipo()) {
                    case FIJO -> Dinero.de(politica.valor(), montoOriginal.moneda());
                    case PORCENTUAL -> montoOriginal.por(politica.valor(), RoundingMode.HALF_UP);
                    case PORCENTUAL_DIARIO ->
                        montoOriginal.por(politica.valor().multiply(BigDecimal.valueOf(mora)), RoundingMode.HALF_UP);
                };

        // El tope manda siempre: una deuda que crece sola hasta ser impagable no la
        // cobra nadie.
        Dinero recargo = bruto.esMayorQue(politica.tope()) ? politica.tope() : bruto;
        return new Calculo(recargo, mora, severidad(mora, politica));
    }

    private static String severidad(int diasDeMora, Politica politica) {
        if (diasDeMora >= politica.diasParaIncumplimiento()) {
            return "INCUMPLIMIENTO";
        }
        return diasDeMora >= politica.diasParaMoraGrave() ? "MORA_GRAVE" : "MORA";
    }
}
