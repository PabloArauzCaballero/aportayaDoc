package bo.aportaya.cumplimiento.dominio;

import bo.aportaya.plataforma.dominio.CalendarioHabil;
import bo.aportaya.plataforma.dominio.PlazoHabil;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * El plazo de un reclamo, **calculado al ingresar y guardado** (R-CON-01).
 *
 * <p>Recalcularlo al consultar es la forma en que un plazo se estira sin que nadie lo
 * decida: cambia el calendario de feriados, cambia la hora del servidor, y el reclamo
 * que vencia el viernes vence el lunes. Se calcula una vez, se persiste, y lo que se
 * lee despues es lo que se prometio.
 *
 * <p>La prorroga tiene dos limites y los dos importan: **se comunica al cliente antes
 * de que venza el plazo original** —avisar despues no es avisar—, y si supera el maximo
 * de la norma exige comunicacion escrita al organismo. Los dos los verifica la base
 * ({@code ck_reclamo_prorroga} y {@code ck_reclamo_prorroga_extendida}).
 */
public final class PlazoDelReclamo {

    private PlazoDelReclamo() {}

    /**
     * @param diasHabiles entre 1 y 5, lo que admite {@code ck_reclamo_dias}
     */
    public static OffsetDateTime vence(OffsetDateTime ingreso, int diasHabiles, CalendarioHabil calendario) {
        if (diasHabiles < 1 || diasHabiles > 5) {
            throw new IllegalArgumentException("El plazo de un reclamo va de 1 a 5 dias habiles (R-CON-01)");
        }
        LocalDate vencimiento = PlazoHabil.sumar(ingreso.toLocalDate(), diasHabiles, calendario);
        return ingreso.with(vencimiento);
    }

    /** La fecha hasta la que hay que conservar el reclamo: diez años (R-CON-05). */
    public static LocalDate conservarHasta(OffsetDateTime ingreso) {
        return ingreso.toLocalDate().plusYears(10);
    }

    /**
     * Si la prorroga se puede otorgar tal como viene.
     *
     * @param maximoDias el tope de la norma, medido desde el ingreso. Es catalogo
     */
    public static Veredicto revisarProrroga(
            OffsetDateTime ingreso,
            OffsetDateTime plazoOriginal,
            OffsetDateTime prorrogaHasta,
            OffsetDateTime comunicadaAlCliente,
            OffsetDateTime comunicadaAlOrganismo,
            String justificacion,
            int maximoDias) {

        if (!prorrogaHasta.isAfter(plazoOriginal)) {
            return new Veredicto(false, "Una prorroga que no extiende el plazo no es una prorroga.");
        }
        if (comunicadaAlCliente == null || comunicadaAlCliente.isAfter(plazoOriginal)) {
            // Avisar despues de vencido es contarle a alguien que ya lo hicimos
            // esperar de mas.
            return new Veredicto(false, "La prorroga se comunica al cliente antes de que venza el plazo original.");
        }
        if (prorrogaHasta.isAfter(ingreso.plusDays(maximoDias))
                && (comunicadaAlOrganismo == null || justificacion == null || justificacion.isBlank())) {
            return new Veredicto(
                    false,
                    "Pasar de " + maximoDias
                            + " dias exige comunicacion escrita al organismo y justificacion (R-CON-03).");
        }
        return new Veredicto(true, null);
    }

    public record Veredicto(boolean admisible, String motivo) {}
}
