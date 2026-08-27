package bo.aportaya.notificaciones.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CU-80 · Interpola una plantilla con sus variables. Puro.
 *
 * <p>**Falla si falta una variable.** Dejar un {@code {{monto}}} sin resolver en el
 * mensaje que le llega a una persona es peor que no mandarlo: parece un error del
 * sistema justo cuando se le esta hablando de su plata.
 *
 * <p>Escapa lo interpolado. El texto de la plantilla lo aprueba cumplimiento; las
 * variables vienen de datos, y un dato con marcas de plantilla adentro no puede
 * inyectar mas plantilla.
 */
public final class PlantillaRenderizada {

    private static final Pattern MARCA = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

    private PlantillaRenderizada() {}

    /** Lo que la plantilla necesita, en el orden en que aparece. */
    public static List<String> variablesDe(String cuerpo) {
        List<String> nombres = new ArrayList<>();
        Matcher marca = MARCA.matcher(cuerpo);
        while (marca.find()) {
            if (!nombres.contains(marca.group(1))) {
                nombres.add(marca.group(1));
            }
        }
        return nombres;
    }

    public static String render(String cuerpo, Map<String, String> variables) {
        List<String> faltantes = variablesDe(cuerpo).stream()
                .filter(nombre -> !variables.containsKey(nombre))
                .toList();
        if (!faltantes.isEmpty()) {
            throw new VariableFaltante(faltantes);
        }

        Matcher marca = MARCA.matcher(cuerpo);
        StringBuilder texto = new StringBuilder();
        while (marca.find()) {
            marca.appendReplacement(texto, Matcher.quoteReplacement(escapar(variables.get(marca.group(1)))));
        }
        marca.appendTail(texto);
        return texto.toString();
    }

    /** Neutraliza las marcas que vengan DENTRO de un valor: no se renderizan de nuevo. */
    private static String escapar(String valor) {
        return valor == null ? "" : valor.replace("{{", "").replace("}}", "");
    }

    /** Que la plantilla pida algo que nadie mando es un defecto, no un mensaje raro. */
    public static final class VariableFaltante extends IllegalArgumentException {
        private final transient List<String> faltantes;

        VariableFaltante(List<String> faltantes) {
            super("La plantilla necesita variables que no llegaron: " + String.join(", ", faltantes));
            this.faltantes = List.copyOf(faltantes);
        }

        public List<String> faltantes() {
            return faltantes;
        }
    }
}
