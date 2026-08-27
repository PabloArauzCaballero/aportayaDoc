package bo.aportaya.nucleofinanciero.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * CU-40 · Resuelve el inicio y el fin de la ventana vigente. Puro.
 *
 * <p>Las cinco ventanas son las de {@code ck_limite_operativo_billetera_ventana}:
 * OPERACION, DIA, SEMANA, MES y ANIO. No hay una sexta, y la que no se reconoce
 * **falla en vez de asumir una**: tratar una ventana desconocida como diaria
 * inventaria un tope que nadie configuro.
 */
public final class VentanaDeLimite {

    private VentanaDeLimite() {}

    public record Rango(OffsetDateTime inicio, OffsetDateTime fin) {}

    public static Rango resolver(String ventana, OffsetDateTime ahora) {
        LocalDate hoy = ahora.toLocalDate();
        return switch (ventana) {
            // Una operacion es su propia ventana: el acumulado siempre arranca en
            // cero y el tope se compara contra el monto solo.
            case "OPERACION" -> new Rango(ahora, ahora);
            case "DIA" -> new Rango(inicioDe(hoy, ahora), finDe(hoy, ahora));
            case "SEMANA" ->
                new Rango(
                        inicioDe(hoy.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)), ahora),
                        finDe(hoy.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)), ahora));
            case "MES" ->
                new Rango(
                        inicioDe(hoy.withDayOfMonth(1), ahora),
                        finDe(hoy.with(TemporalAdjusters.lastDayOfMonth()), ahora));
            case "ANIO" ->
                new Rango(
                        inicioDe(hoy.withDayOfYear(1), ahora),
                        finDe(hoy.with(TemporalAdjusters.lastDayOfYear()), ahora));
            default ->
                throw new IllegalArgumentException(
                        "Ventana de limite desconocida: " + ventana + ". No se asume ninguna.");
        };
    }

    private static OffsetDateTime inicioDe(LocalDate dia, OffsetDateTime referencia) {
        return dia.atStartOfDay().atOffset(referencia.getOffset());
    }

    private static OffsetDateTime finDe(LocalDate dia, OffsetDateTime referencia) {
        return dia.plusDays(1).atStartOfDay().minusNanos(1).atOffset(referencia.getOffset());
    }
}
