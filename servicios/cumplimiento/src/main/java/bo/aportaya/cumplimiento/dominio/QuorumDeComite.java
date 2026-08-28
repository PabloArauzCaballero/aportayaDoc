package bo.aportaya.cumplimiento.dominio;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Que hace valida la sesion de un comite.
 *
 * <p>Tres cosas, y las tres por el mismo motivo: **una decision tomada sin ellas no se
 * puede defender ante nadie**.
 *
 * <ul>
 *   <li>**Quorum.** Menos asistentes que el minimo y la decision es de quienes pudieron
 *       venir, no del comite.
 *   <li>**Composicion.** Tres asistentes sin el rol de cumplimiento no son un comite de
 *       cumplimiento: falta justamente quien tenia que objetar.
 *   <li>**Nadie vota su propio asunto.** Quien tiene interes directo se abstiene y lo
 *       dice; si vota, la decision queda viciada aunque el resultado sea el correcto.
 * </ul>
 */
public final class QuorumDeComite {

    private QuorumDeComite() {}

    public static Veredicto revisar(List<Asistente> asistentes, int quorumMinimo, Set<String> composicionRequerida) {

        if (asistentes.size() < quorumMinimo) {
            return new Veredicto(
                    false, false, "Sesionaron " + asistentes.size() + " de " + quorumMinimo + " requeridos.");
        }
        var rolesPresentes = asistentes.stream().map(Asistente::rol).collect(java.util.stream.Collectors.toSet());
        var faltantes = new java.util.TreeSet<>(composicionRequerida);
        faltantes.removeAll(rolesPresentes);
        if (!faltantes.isEmpty()) {
            return new Veredicto(true, false, "Falta en la mesa: " + String.join(", ", faltantes) + ".");
        }
        return new Veredicto(true, true, null);
    }

    /**
     * Quien no puede votar un asunto.
     *
     * @param interesados por asunto, quienes tienen interes directo
     * @return los votos viciados, con su asunto
     */
    public static List<String> votosViciados(List<Voto> votos, Map<Integer, Set<UUID>> interesados) {
        var viciados = new java.util.ArrayList<String>();
        for (var voto : votos) {
            if (interesados.getOrDefault(voto.asunto(), Set.of()).contains(voto.usuarioId())
                    && !"ABSTENCION".equals(voto.voto())) {
                viciados.add("Asunto " + voto.asunto() + ": " + voto.usuarioId() + " tiene interes directo.");
            }
        }
        return List.copyOf(viciados);
    }

    /** Una abstencion sin motivo no explica nada; la norma pide que se registre por que. */
    public static List<String> abstencionesSinMotivo(List<Voto> votos) {
        return votos.stream()
                .filter(v -> "ABSTENCION".equals(v.voto()))
                .filter(v ->
                        v.motivoAbstencion() == null || v.motivoAbstencion().isBlank())
                .map(v -> "Asunto " + v.asunto() + ": abstencion sin motivo.")
                .toList();
    }

    public record Asistente(UUID usuarioId, String rol) {}

    public record Voto(int asunto, UUID usuarioId, String voto, String motivoAbstencion) {}

    /**
     * @param hayQuorum vinieron los suficientes
     * @param composicionCompleta y ademas vinieron los que tenian que venir
     */
    public record Veredicto(boolean hayQuorum, boolean composicionCompleta, String motivo) {}
}
