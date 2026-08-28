package bo.aportaya.cumplimiento.dominio;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Valida la expresion de una regla de monitoreo antes de dejarla activar.
 *
 * <p>**Un umbral escrito dentro de la expresion es el defecto que esta clase existe
 * para impedir** (R-UIF-01). Si la regla dice «monto > 10000», el dia que la UIF mueva
 * el umbral hay que desplegar para cumplir una circular, y la circular no espera al
 * despliegue. La expresion apunta al catalogo por su codigo; el numero vive en
 * {@code umbral_reporte_uif}, con su vigencia y su base normativa.
 *
 * <p>Y por eso tambien: **la accion automatica no puede exceder lo que la severidad
 * habilita**. Bloquearle la cuenta a alguien por una alerta de severidad baja es un
 * dano cierto por una sospecha debil.
 */
public final class ExpresionDeRegla {

    /** Campos que una expresion puede nombrar. Cualquier otro es un error, no un aviso. */
    private static final List<String> CAMPOS = List.of(
            "monto",
            "monto_usd",
            "cantidad",
            "concepto",
            "tipo",
            "canal",
            "usuario_id",
            "cuenta_id",
            "hora",
            "pais",
            "ventana_horas",
            "umbral");

    /** Un numero suelto de tres cifras o mas: la forma de un umbral cableado. */
    private static final Pattern NUMERO_GRANDE = Pattern.compile("(?<![\\w.])\\d{3,}(?![\\w.])");

    private static final Pattern REFERENCIA = Pattern.compile("umbral\\('([A-Za-z0-9_\\-]{1,60})'\\)");

    private static final Pattern IDENTIFICADOR = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private ExpresionDeRegla() {}

    public static Veredicto revisar(String expresion, String umbralReferencia) {
        if (expresion == null || expresion.isBlank()) {
            return Veredicto.invalida("La expresion esta vacia.");
        }
        // AP-CU48-02 · R-UIF-01. Se busca el numero cableado ANTES que nada: es el
        // unico error de esta lista que se descubre tarde y en produccion.
        var numero = NUMERO_GRANDE.matcher(expresion);
        if (numero.find()) {
            return new Veredicto(
                    false,
                    true,
                    "La expresion trae el numero " + numero.group()
                            + " escrito. El umbral va al catalogo: usar umbral('CODIGO').");
        }
        var campos = new java.util.ArrayList<String>();
        // Los literales entre comillas —los codigos del catalogo de umbrales— no son
        // campos: sacarlos antes evita rechazar la regla bien escrita por nombrar el
        // umbral al que apunta, que es justo lo que queremos que haga.
        var identificadores = IDENTIFICADOR.matcher(expresion.replaceAll("'[^']*'", "''"));
        while (identificadores.find()) {
            String id = identificadores.group();
            if (esPalabraDeLaGramatica(id)) {
                continue;
            }
            if (!CAMPOS.contains(id)) {
                return Veredicto.invalida("La expresion referencia un campo inexistente: " + id + ".");
            }
            campos.add(id);
        }
        if (campos.isEmpty()) {
            return Veredicto.invalida("La expresion no referencia ningun campo evaluable.");
        }
        if (expresion.contains("umbral(") && !REFERENCIA.matcher(expresion).find()) {
            return Veredicto.invalida("La referencia al catalogo de umbrales esta mal escrita.");
        }
        if (umbralReferencia != null && !expresion.contains("umbral('" + umbralReferencia + "')")) {
            return Veredicto.invalida("La regla declara el umbral " + umbralReferencia + " y la expresion no lo usa.");
        }
        return new Veredicto(true, false, null);
    }

    private static boolean esPalabraDeLaGramatica(String id) {
        return List.of("and", "or", "not", "in", "umbral", "count", "sum", "abs")
                .contains(id.toLowerCase());
    }

    /**
     * Que accion automatica habilita cada severidad.
     *
     * <p>La escala es acotada a proposito: entre no hacer nada y bloquearle la cuenta a
     * alguien hay dos escalones, y saltarselos convierte una sospecha en una sancion.
     */
    public static boolean accionProporcionada(String severidad, String accion) {
        return switch (severidad) {
            case "BAJA", "MEDIA" -> "SOLO_ALERTAR".equals(accion);
            case "ALTA" -> List.of("SOLO_ALERTAR", "RETENER_OPERACION").contains(accion);
            case "CRITICA" ->
                List.of("SOLO_ALERTAR", "RETENER_OPERACION", "BLOQUEAR_CUENTA").contains(accion);
            default -> false;
        };
    }

    /**
     * @param umbralCableado separado de {@code valida} porque tiene su propio codigo de
     *     error: no es «no compila», es «alguien puso la norma dentro del codigo»
     */
    public record Veredicto(boolean valida, boolean umbralCableado, String motivo) {

        static Veredicto invalida(String motivo) {
            return new Veredicto(false, false, motivo);
        }
    }
}
