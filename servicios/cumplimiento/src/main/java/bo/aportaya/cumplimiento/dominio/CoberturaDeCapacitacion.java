package bo.aportaya.cumplimiento.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Quien tiene la capacitacion del periodo al dia y quien no.
 *
 * <p>Con una regla que evita la injusticia mas comun de estos controles: **quien entro
 * en noviembre no figura como incumplido en diciembre**. Su plazo corre desde el alta,
 * no desde enero. Marcar a alguien por no haber hecho un curso que no existia cuando
 * llego es la clase de reporte que hace que nadie confie en el tablero.
 */
public final class CoberturaDeCapacitacion {

    private CoberturaDeCapacitacion() {}

    /**
     * @param plazoDesdeElAltaDias cuanto tiempo tiene alguien recien ingresado para
     *     capacitarse antes de contar como pendiente. Es politica, llega como dato
     */
    public static Cobertura calcular(
            String periodo,
            List<Empleado> personalActivo,
            List<UUID> aprobaronEnElPeriodo,
            LocalDate corte,
            int plazoDesdeElAltaDias) {

        var pendientes = new java.util.ArrayList<Empleado>();
        int enPlazo = 0;
        for (Empleado e : personalActivo) {
            if (aprobaronEnElPeriodo.contains(e.usuarioId())) {
                continue;
            }
            if (e.fechaDeAlta().plusDays(plazoDesdeElAltaDias).isAfter(corte)) {
                enPlazo++;
                continue;
            }
            pendientes.add(e);
        }
        return new Cobertura(
                periodo, personalActivo.size(), aprobaronEnElPeriodo.size(), enPlazo, List.copyOf(pendientes));
    }

    public record Empleado(UUID usuarioId, String nombre, LocalDate fechaDeAlta) {}

    /**
     * @param todaviaEnPlazo los que entraron hace poco. Se cuentan aparte porque no son
     *     un incumplimiento, y mezclarlos con los pendientes inflaria el numero que
     *     despues se le muestra al regulador
     */
    public record Cobertura(
            String periodo, int personalActivo, int aprobados, int todaviaEnPlazo, List<Empleado> pendientes) {}
}
