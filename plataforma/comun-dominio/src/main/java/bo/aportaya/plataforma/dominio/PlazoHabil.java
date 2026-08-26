package bo.aportaya.plataforma.dominio;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Suma dias habiles contra el calendario INYECTADO, nunca contra
 * {@code LocalDate.now()}.
 *
 * <p>El plazo se calcula UNA vez, al nacer la obligacion, y se persiste. Recalcularlo
 * al consultar significa que el vencimiento de un usuario cambia porque cambio la
 * tabla de feriados seis meses despues — y eso, en un plazo legal, es cambiarle las
 * reglas a alguien que ya cumplio.
 */
public final class PlazoHabil {

    /**
     * Hasta donde se busca un dia habil antes de dar por rota la tabla de feriados.
     * Se expresa como fecha y no como una cuenta de dias porque lo que se afirma es
     * de dominio: ningun plazo de este sistema se extiende diez anios.
     */
    private static LocalDate limiteDeBusqueda(LocalDate desde) {
        return desde.plusYears(10);
    }

    private PlazoHabil() {}

    /** El dia habil numero {@code dias} despues de {@code desde}, sin contar {@code desde}. */
    public static LocalDate sumar(LocalDate desde, int dias, CalendarioHabil calendario) {
        Objects.requireNonNull(desde, "fecha de inicio");
        Objects.requireNonNull(calendario, "calendario");
        if (dias < 0) {
            throw new ErrorDeDominio("Un plazo no se cuenta hacia atras: %d dias".formatted(dias));
        }
        LocalDate limite = limiteDeBusqueda(desde);
        LocalDate fecha = desde;
        int contados = 0;
        while (contados < dias) {
            fecha = fecha.plusDays(1);
            if (fecha.isAfter(limite)) {
                throw new ErrorDeDominio("El calendario no devuelve ningun dia habil: revisa los feriados cargados");
            }
            if (calendario.esHabil(fecha)) {
                contados++;
            }
        }
        return fecha;
    }

    /** Corre al siguiente habil. A favor del cliente: nunca hacia atras. */
    public static LocalDate siguienteHabil(LocalDate fecha, CalendarioHabil calendario) {
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(calendario, "calendario");
        LocalDate limite = limiteDeBusqueda(fecha);
        LocalDate corrida = fecha;
        while (calendario.esNoHabil(corrida)) {
            corrida = corrida.plusDays(1);
            if (corrida.isAfter(limite)) {
                throw new ErrorDeDominio("El calendario no devuelve ningun dia habil: revisa los feriados cargados");
            }
        }
        return corrida;
    }
}
