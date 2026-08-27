package bo.aportaya.auditoria;

import bo.aportaya.plataforma.dominio.CalendarioHabil;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Un calendario sin feriados declarados: solo sabado y domingo son no habiles.
 *
 * <p>El calendario se INYECTA, y por eso una prueba puede fijarlo. Si estuviera
 * horneado, el resultado del plazo dependeria del ano en que corra la prueba.
 */
final class CalendarioVacio {

    static final CalendarioHabil SIN_FERIADOS =
            fecha -> fecha.getDayOfWeek() == DayOfWeek.SATURDAY || fecha.getDayOfWeek() == DayOfWeek.SUNDAY;

    private CalendarioVacio() {}

    static LocalDate hoy() {
        return LocalDate.now();
    }
}
