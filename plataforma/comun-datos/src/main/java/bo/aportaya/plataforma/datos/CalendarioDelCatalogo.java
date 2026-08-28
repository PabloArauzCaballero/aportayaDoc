package bo.aportaya.plataforma.datos;

import bo.aportaya.plataforma.dominio.CalendarioHabil;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/**
 * Que dias no cuentan para un plazo, leidos del catalogo compartido.
 *
 * <p>Sabado y domingo salen del calendario gregoriano; los feriados, de
 * {@code catalogo.dia_no_habil}. Ese esquema es **comun y de nadie en particular**, asi
 * que leerlo no cruza el invariante 11 — a diferencia de leer el esquema de otro
 * servicio, que si lo cruzaria.
 *
 * <p>Vive en la plataforma y no en un servicio porque los plazos habiles los cuentan
 * cuatro carriles distintos, y catorce copias de esta consulta divergen: el dia que una
 * agregue el alcance departamental y las otras no, el mismo feriado alargaria un plazo y
 * no otro.
 *
 * <p>Se consulta con alcance NACIONAL: un feriado departamental no alarga un plazo de
 * respuesta al cliente. Es la lectura estricta, la que no le corre la fecha en contra a
 * quien reclama.
 */
public class CalendarioDelCatalogo implements CalendarioHabil {

    private final DSLContext dsl;

    public CalendarioDelCatalogo(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean esNoHabil(LocalDate fecha) {
        if (fecha.getDayOfWeek() == DayOfWeek.SATURDAY || fecha.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return true;
        }
        return dsl.fetchExists(DSL.selectOne()
                .from(DSL.table(DSL.name("catalogo", "dia_no_habil")))
                .where(DSL.field("fecha", LocalDate.class).eq(fecha))
                .and(DSL.field("alcance", String.class).eq("NACIONAL")));
    }
}
