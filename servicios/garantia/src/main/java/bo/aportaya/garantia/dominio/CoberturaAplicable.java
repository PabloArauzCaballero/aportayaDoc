package bo.aportaya.garantia.dominio;

import bo.aportaya.plataforma.dominio.Dinero;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cuanto del incumplimiento cubre el fondo, y cuanto no.
 *
 * <p>Un fondo de garantia existe para que el incumplimiento de uno no se lleve puesto
 * al grupo entero. Pero **cubrir todo, siempre, lo vacia**: los topes no son mezquindad
 * — son lo que hace que el fondo siga estando cuando le toque al siguiente.
 *
 * <p>Cuatro limites, y gana el mas chico: el porcentaje del aporte, el tope por
 * participante descontando lo que ya se le cubrio, el tope del periodo, y el saldo
 * disponible. Aplicar solo uno deja los otros tres como decoracion.
 */
public final class CoberturaAplicable {

    private CoberturaAplicable() {}

    /** La politica del fondo, tal como la usa el calculo. Es catalogo, no constantes. */
    public record Politica(
            BigDecimal porcentajeMaximoPorAporte,
            Dinero topePorParticipante,
            Dinero topePorPeriodo,
            int maximoCoberturasPorParticipante,
            Dinero desdeCuantoExigeAprobacionManual,
            int diasMoraParaActivar) {}

    /** Lo que ya consumio este participante y este periodo. */
    public record Consumido(Dinero porParticipante, Dinero porPeriodo, int coberturasPrevias) {}

    public record Resultado(
            Dinero montoSolicitado,
            Dinero montoCubierto,
            BigDecimal porcentajeCobertura,
            boolean exigeAprobacionManual,
            String limiteQueMando) {

        public boolean cubreAlgo() {
            return montoCubierto.monto().signum() > 0;
        }
    }

    public static Resultado calcular(
            Dinero solicitado, Politica politica, Consumido consumido, Dinero saldoDisponible, int diasMora) {

        var moneda = solicitado.moneda();

        // Denegar por omision (invariante 9): antes del plazo el fondo no se toca. Si
        // cubriera el primer dia de atraso, dejaria de ser una garantia y pasaria a ser
        // un adelanto automatico, y nadie volveria a pagar a tiempo.
        if (diasMora < politica.diasMoraParaActivar()) {
            return new Resultado(solicitado, Dinero.cero(moneda), BigDecimal.ZERO, false, "DIAS_DE_MORA_INSUFICIENTES");
        }
        if (consumido.coberturasPrevias() >= politica.maximoCoberturasPorParticipante()) {
            return new Resultado(solicitado, Dinero.cero(moneda), BigDecimal.ZERO, false, "MAXIMO_DE_COBERTURAS");
        }

        Dinero porPorcentaje = Dinero.de(
                solicitado
                        .monto()
                        .multiply(politica.porcentajeMaximoPorAporte())
                        // Un porcentaje se pasa a fraccion corriendo la coma, no
                        // dividiendo por un cien escrito a mano: 100 no es un umbral.
                        .movePointLeft(2)
                        .setScale(2, RoundingMode.DOWN),
                moneda);
        Dinero restanteDelParticipante = restar(politica.topePorParticipante(), consumido.porParticipante());
        Dinero restanteDelPeriodo = restar(politica.topePorPeriodo(), consumido.porPeriodo());

        Dinero cubierto = porPorcentaje;
        String limite = "PORCENTAJE_DEL_APORTE";
        if (restanteDelParticipante.monto().compareTo(cubierto.monto()) < 0) {
            cubierto = restanteDelParticipante;
            limite = "TOPE_POR_PARTICIPANTE";
        }
        if (restanteDelPeriodo.monto().compareTo(cubierto.monto()) < 0) {
            cubierto = restanteDelPeriodo;
            limite = "TOPE_POR_PERIODO";
        }
        if (saldoDisponible.monto().compareTo(cubierto.monto()) < 0) {
            // El fondo no se descubre: cubrir mas de lo que tiene lo dejaria en
            // negativo, y el siguiente incumplimiento no encontraria nada.
            cubierto = saldoDisponible;
            limite = "SALDO_DEL_FONDO";
        }

        BigDecimal porcentaje = solicitado.monto().signum() == 0
                ? BigDecimal.ZERO
                : cubierto.monto().movePointRight(2).divide(solicitado.monto(), 2, RoundingMode.HALF_EVEN);

        return new Resultado(
                solicitado,
                cubierto,
                porcentaje,
                cubierto.monto()
                                .compareTo(politica.desdeCuantoExigeAprobacionManual()
                                        .monto())
                        >= 0,
                limite);
    }

    private static Dinero restar(Dinero tope, Dinero consumido) {
        BigDecimal restante = tope.monto().subtract(consumido.monto());
        return Dinero.de(restante.signum() < 0 ? BigDecimal.ZERO : restante, tope.moneda());
    }
}
