package bo.aportaya.tarifas.dominio;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Que segmento comercial rige para un usuario, y **por que**.
 *
 * <p>Un usuario puede calificar para varios y solo uno rige: gana la prioridad mas
 * baja. La ambiguedad se resuelve al definir el segmento, no al cobrar — descubrirla
 * al cobrar significa que dos usuarios iguales pagaron distinto segun el orden en que
 * la base devolvio las filas.
 *
 * <p>El criterio se evalua contra hechos que el sistema ya tiene y puede recalcular.
 * **Si falta un hecho, no califica**: adivinar a favor regala plata y adivinar en
 * contra cobra de mas, y las dos cosas hay que explicarlas despues.
 */
public final class SegmentoAplicable {

    private SegmentoAplicable() {}

    /**
     * @param criterio hechos exigidos y su valor minimo, tal como vienen del JSON
     */
    public record Candidato(String codigo, int prioridad, Map<String, Integer> criterio) {}

    public record Eleccion(String codigo, String motivo, boolean evaluable) {}

    /** Precio base y su motivo, cuando no califica para ninguno. */
    public static final Eleccion NINGUNO = new Eleccion(null, "No califica para ningun beneficio", true);

    public static Eleccion elegir(List<Candidato> candidatos, Map<String, Integer> hechosDelUsuario) {
        List<Candidato> ordenados = candidatos.stream()
                .sorted(Comparator.comparingInt(Candidato::prioridad))
                .toList();

        for (Candidato candidato : ordenados) {
            Optional<String> faltante = candidato.criterio().keySet().stream()
                    .filter(hecho -> !hechosDelUsuario.containsKey(hecho))
                    .findFirst();
            if (faltante.isPresent()) {
                // No se adivina: se cotiza al precio base y se dice cual hecho falto.
                return new Eleccion(null, "Falta el dato «" + faltante.get() + "» para evaluar el beneficio", false);
            }
            boolean cumple = candidato.criterio().entrySet().stream()
                    .allMatch(exigido -> hechosDelUsuario.get(exigido.getKey()) >= exigido.getValue());
            if (cumple) {
                return new Eleccion(candidato.codigo(), motivoDe(candidato), true);
            }
        }
        return NINGUNO;
    }

    private static String motivoDe(Candidato candidato) {
        String detalle = candidato.criterio().entrySet().stream()
                .map(e -> e.getKey() + " ≥ " + e.getValue())
                .reduce((a, b) -> a + " y " + b)
                .orElse("sin condiciones");
        return "Cumple " + detalle;
    }
}
