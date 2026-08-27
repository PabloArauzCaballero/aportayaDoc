package bo.aportaya.auditoria.dominio;

import bo.aportaya.plataforma.dominio.ErrorDeDominio;
import java.util.List;
import java.util.Map;

/**
 * Los parametros que un reporte acepta, y nada mas.
 *
 * <p>**Lista blanca, no lista negra.** El nombre, el tipo y el rango se comparan
 * contra `parametros_esperados`; lo que no esta declarado no entra. La alternativa
 * —aceptar lo que venga y filtrar lo peligroso— es como se escriben las inyecciones:
 * siempre falta un caso.
 *
 * <p>Y por eso el valor **nunca se concatena** en la consulta: se liga. Esta clase
 * solo decide si el parametro es admisible; quien lo usa lo pasa como ligadura.
 */
public final class ParametrosDeReporte {

    /** Lo que el reporte declara esperar. */
    public record Esperado(String nombre, String tipo, boolean obligatorio) {}

    private ParametrosDeReporte() {}

    /**
     * @throws ErrorDeDominio si falta uno obligatorio, sobra uno no declarado o el
     *     tipo no coincide
     */
    public static void validar(List<Esperado> esperados, Map<String, String> recibidos) {
        for (Esperado esperado : esperados) {
            String valor = recibidos.get(esperado.nombre());
            if (valor == null || valor.isBlank()) {
                if (esperado.obligatorio()) {
                    throw new ErrorDeDominio("Falta el parametro '" + esperado.nombre() + "'");
                }
                continue;
            }
            if (!esDelTipo(valor, esperado.tipo())) {
                throw new ErrorDeDominio("El parametro '" + esperado.nombre() + "' no es " + esperado.tipo());
            }
        }

        List<String> declarados = esperados.stream().map(Esperado::nombre).toList();
        for (String recibido : recibidos.keySet()) {
            if (!declarados.contains(recibido)) {
                // Un parametro no declarado que llega hasta la consulta es la puerta
                // por la que entra lo que nadie previo.
                throw new ErrorDeDominio("El parametro '" + recibido + "' no esta declarado en el reporte");
            }
        }
    }

    private static boolean esDelTipo(String valor, String tipo) {
        return switch (tipo == null ? "" : tipo.toUpperCase(java.util.Locale.ROOT)) {
            case "UUID" -> valor.matches("[0-9a-fA-F-]{36}");
            case "FECHA" -> valor.matches("\\d{4}-\\d{2}-\\d{2}");
            case "ENTERO" -> valor.matches("-?\\d+");
            case "DECIMAL" -> valor.matches("-?\\d+(\\.\\d+)?");
            case "BOOLEANO" -> "true".equalsIgnoreCase(valor) || "false".equalsIgnoreCase(valor);
            // TEXTO acepta cualquier cosa a proposito: lo que lo hace seguro no es
            // validarlo, es que viaja como ligadura y nunca concatenado.
            case "TEXTO" -> true;
            default -> false;
        };
    }
}
