package bo.aportaya.cumplimiento.dominio;

import java.time.LocalDate;

/**
 * Si una prueba de continuidad alcanzo lo que el plan prometio.
 *
 * <p>**El resultado no lo elige quien ejecuto la prueba**: sale de comparar el RTO y el
 * RPO obtenidos contra los comprometidos. Dejar que lo elija una persona convierte cada
 * prueba en EXITOSA y la continuidad en un papel.
 *
 * <p>Y una prueba que no alcanza los objetivos **exige plan de accion**. Registrar que
 * se tardo 95 minutos donde se prometieron 60, y no hacer nada, es documentar el
 * incumplimiento sin corregirlo.
 */
public final class ObjetivosDeContinuidad {

    private ObjetivosDeContinuidad() {}

    public static Resultado evaluar(int rtoComprometido, int rpoComprometido, int rtoObtenido, int rpoObtenido) {

        boolean rtoOk = rtoObtenido <= rtoComprometido;
        boolean rpoOk = rpoObtenido <= rpoComprometido;

        if (rtoOk && rpoOk) {
            return new Resultado("EXITOSA", false, null);
        }
        // PARCIAL cuando uno de los dos se cumplio: la diferencia importa para saber si
        // el problema es recuperar el servicio o recuperar los datos, que se arreglan
        // distinto.
        String resultado = rtoOk || rpoOk ? "PARCIAL" : "FALLIDA";
        var motivo = new StringBuilder();
        if (!rtoOk) {
            motivo.append("RTO obtenido ")
                    .append(rtoObtenido)
                    .append(" min supera el comprometido de ")
                    .append(rtoComprometido)
                    .append(" min. ");
        }
        if (!rpoOk) {
            motivo.append("RPO obtenido ")
                    .append(rpoObtenido)
                    .append(" min supera el comprometido de ")
                    .append(rpoComprometido)
                    .append(" min.");
        }
        return new Resultado(resultado, true, motivo.toString().trim());
    }

    /** Cuando toca la proxima prueba, desde la periodicidad del plan. */
    public static LocalDate proximaPrueba(LocalDate ultima, int periodicidadMeses) {
        return ultima.plusMonths(periodicidadMeses);
    }

    /**
     * @param exigePlanDeAccion cierto cuando no se alcanzaron los objetivos; el plan es
     *     la unica parte que efectivamente arregla algo
     */
    public record Resultado(String resultado, boolean exigePlanDeAccion, String motivo) {}
}
