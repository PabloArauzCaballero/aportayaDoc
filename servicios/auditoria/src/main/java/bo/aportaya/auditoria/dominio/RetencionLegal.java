package bo.aportaya.auditoria.dominio;

import java.time.LocalDate;
import java.util.List;

/**
 * Que se puede borrar y que hay que conservar aunque el titular lo pida.
 *
 * <p>Es la tension entera de CU-07: el derecho de supresion es real y la obligacion
 * de conservar informacion financiera por diez anos tambien. **No se elige una**: se
 * borra lo que se puede y se seudonimiza lo que la ley obliga a guardar, y se le dice
 * al titular exactamente que quedo y por que.
 *
 * <p>Responder «no se puede borrar nada» seria mentirle; borrar todo seria un
 * incumplimiento. La unica respuesta honesta es la lista.
 */
public final class RetencionLegal {

    private RetencionLegal() {}

    /** Una politica de retencion, tal como la declara el catalogo. */
    public record Politica(
            String entidad, int mesesActiva, int mesesHistorica, String accionAlVencer, String baseLegal) {

        /** La fecha desde la cual esta entidad ya se puede tocar. */
        public LocalDate venceEl(LocalDate desde) {
            return desde.plusMonths((long) mesesActiva + mesesHistorica);
        }
    }

    public record Desenlace(
            EstrategiaDeAnonimizacion estrategia, List<String> retenidasPorLey, List<String> borrables) {}

    /**
     * Reparte las entidades del titular entre lo que se borra y lo que se conserva.
     *
     * @param ultimaActividad desde cuando corre la retencion
     */
    public static Desenlace resolver(List<Politica> politicas, LocalDate ultimaActividad, LocalDate hoy) {
        List<String> retenidas = politicas.stream()
                .filter(p -> !p.venceEl(ultimaActividad).isBefore(hoy))
                .map(p -> p.entidad() + " (" + p.baseLegal() + ")")
                .sorted()
                .toList();

        List<String> borrables = politicas.stream()
                .filter(p -> p.venceEl(ultimaActividad).isBefore(hoy))
                .map(Politica::entidad)
                .sorted()
                .toList();

        if (retenidas.isEmpty()) {
            // Nada que conservar: se borra de verdad.
            return new Desenlace(EstrategiaDeAnonimizacion.BORRADO_TOTAL, retenidas, borrables);
        }
        if (borrables.isEmpty()) {
            // Todo bajo retencion: no se borra nada, se seudonimiza. El dato sigue
            // existiendo para el regulador y deja de identificar a la persona.
            return new Desenlace(EstrategiaDeAnonimizacion.SEUDONIMIZACION, retenidas, borrables);
        }
        return new Desenlace(EstrategiaDeAnonimizacion.BORRADO_PARCIAL, retenidas, borrables);
    }
}
