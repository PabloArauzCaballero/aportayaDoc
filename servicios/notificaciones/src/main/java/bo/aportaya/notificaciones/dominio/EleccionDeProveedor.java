package bo.aportaya.notificaciones.dominio;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * CU-83 · Elige por donde sale el mensaje. Puro.
 *
 * <p>Tres criterios, en este orden: **salud, prioridad, costo**. La salud va primero
 * porque el proveedor mas barato que no entrega sale carisimo — cada mensaje perdido
 * es una persona que no se entero de que le vencia el aporte.
 */
public final class EleccionDeProveedor {

    private EleccionDeProveedor() {}

    /**
     * @param saludPorcentaje entregados sobre enviados en la ventana movil, 0 a 100
     */
    public record Candidato(
            String codigo,
            List<String> canalesSoportados,
            int prioridad,
            BigDecimal costoPorMensaje,
            int saludPorcentaje,
            boolean activo) {

        boolean sirvePara(Canal canal) {
            return activo && canalesSoportados.contains(canal.name());
        }
    }

    /**
     * @param umbralDeSalud por debajo de este porcentaje el proveedor se considera
     *     degradado. Llega de configuracion: es politica operativa, no una constante.
     */
    public static Optional<Candidato> elegir(
            List<Candidato> candidatos, Canal canal, List<String> yaIntentados, int umbralDeSalud) {

        List<Candidato> sanos = candidatos.stream()
                .filter(c -> c.sirvePara(canal))
                .filter(c -> !yaIntentados.contains(c.codigo()))
                .filter(c -> c.saludPorcentaje() >= umbralDeSalud)
                .sorted(Comparator.comparingInt(Candidato::prioridad).thenComparing(Candidato::costoPorMensaje))
                .toList();

        if (!sanos.isEmpty()) {
            return Optional.of(sanos.get(0));
        }

        // Todos degradados: antes que no mandar nada, se usa el menos malo. Un
        // proveedor al 40% entrega cuatro de cada diez; no mandar entrega cero.
        return candidatos.stream()
                .filter(c -> c.sirvePara(canal))
                .filter(c -> !yaIntentados.contains(c.codigo()))
                .max(Comparator.comparingInt(Candidato::saludPorcentaje));
    }
}
