package bo.aportaya.cumplimiento.dominio;

import bo.aportaya.plataforma.dominio.ErrorDeDominio;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Los tres relojes de un incidente de seguridad: contener, reportar, notificar.
 *
 * <p>Corren en paralelo y son distintos. El de contencion lo manda la operacion; el de
 * reporte lo manda el supervisor; el de notificacion a titulares lo manda la ley de
 * datos personales. Tratarlos como uno solo es como se pierde el unico que tenia
 * consecuencia legal.
 *
 * <p><b>Se calculan una vez y SE GUARDAN</b> (invariante 8). Recalcularlos al consultar
 * los haria moverse solos: basta que alguien cambie la politica de plazos el mes que
 * viene para que un incidente de hace tres meses aparezca reportado en plazo cuando no
 * lo estuvo — o al reves. El plazo que rige es el que regia el dia del incidente.
 */
public record PlazosDelIncidente(Map<String, Duration> porSeveridad, Duration paraNotificarTitulares) {

    public PlazosDelIncidente {
        if (porSeveridad == null || porSeveridad.isEmpty()) {
            // Denegar por omision (invariante 9): sin politica de plazos legible, no se
            // inventa uno «razonable». Un plazo inventado es peor que ninguno, porque
            // el expediente diria que se cumplio algo que nadie fijo.
            throw new ErrorDeDominio("No hay politica de plazos de incidente configurada.");
        }
        porSeveridad = Map.copyOf(porSeveridad);
    }

    /**
     * Hasta cuando hay para reportar al organismo.
     *
     * <p>Se cuenta desde la <b>deteccion</b> y no desde la ocurrencia, que es lo que
     * hace la norma: no se le puede exigir a nadie reportar algo que todavia no sabia.
     */
    public OffsetDateTime plazoDeReporte(String severidad, OffsetDateTime detectadoEn) {
        Duration plazo = porSeveridad.get(normalizar(severidad));
        if (plazo == null) {
            throw new ErrorDeDominio("No hay plazo de reporte fijado para severidad " + severidad);
        }
        return detectadoEn.plus(plazo);
    }

    /** Hasta cuando hay para avisarle a la gente cuyos datos se vieron afectados. */
    public OffsetDateTime plazoDeNotificacion(OffsetDateTime detectadoEn) {
        return detectadoEn.plus(paraNotificarTitulares);
    }

    private static String normalizar(String severidad) {
        return severidad == null ? "" : severidad.trim().toUpperCase(Locale.ROOT);
    }
}
