package bo.aportaya.transparencia.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * La primera pasada de moderacion, **siempre con revision humana detras**.
 *
 * <p>Lo que detecta no lo publica: lo retiene. Un comentario con el telefono de
 * alguien no se corrige ni se recorta automaticamente —recortar deja el dato en la
 * base y la sospecha en el aire—; queda {@code PENDIENTE} y lo mira una persona.
 *
 * <p>Que no haga esta clase: decidir si una resena es justa. Eso no lo puede hacer
 * una expresion regular, y fingir que si es como se construye una moderacion que
 * castiga a quien escribe distinto.
 */
public final class ModeracionDeResena {

    /** Telefonos bolivianos: 8 digitos, con o sin separadores, con o sin +591. */
    private static final Pattern TELEFONO = Pattern.compile("(\\+?591[\\s-]?)?[67]\\d{3}[\\s-]?\\d{4}");

    /** Cedulas de identidad: 5 a 10 digitos, con complemento opcional. */
    private static final Pattern DOCUMENTO = Pattern.compile("\\b\\d{5,10}(\\s?-?\\s?[A-Za-z]{1,2})?\\b");

    private static final Pattern CORREO = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

    private ModeracionDeResena() {}

    public static Veredicto revisar(String comentario) {
        if (comentario == null || comentario.isBlank()) {
            return new Veredicto(false, List.of());
        }
        var marcas = new java.util.ArrayList<String>();
        if (TELEFONO.matcher(comentario).find()) {
            marcas.add("TELEFONO");
        }
        if (CORREO.matcher(comentario).find()) {
            marcas.add("CORREO");
        }
        // El documento se busca solo si no hubo telefono: un telefono ya casa con la
        // forma de un documento, y contarlo dos veces exagera lo que encontramos.
        if (marcas.isEmpty() && DOCUMENTO.matcher(comentario).find()) {
            marcas.add("DOCUMENTO");
        }
        return new Veredicto(!marcas.isEmpty(), List.copyOf(marcas));
    }

    /**
     * El peso de una resena en la reputacion, siempre **menor que el de los factores de
     * pago**: una opinion no puede pesar lo que pesa haber pagado o no haber pagado.
     *
     * @param expulsadoDelGrupo quien fue expulsado opina desde el conflicto; su resena
     *     no se descarta —seria silenciarlo— pero pesa menos
     * @param resenasPrevias quien resena a todo el mundo no aporta N senales, aporta una
     * @param politica cuanto atenuar y a partir de cuantas resenas. Llega como dato:
     *     cuanto vale la palabra de alguien enojado no se decide en un {@code if}
     */
    public static BigDecimal peso(
            BigDecimal pesoBase, boolean expulsadoDelGrupo, int resenasPrevias, Atenuacion politica) {
        BigDecimal peso = pesoBase;
        if (expulsadoDelGrupo) {
            peso = peso.multiply(politica.factorPorConflicto());
        }
        if (resenasPrevias >= politica.resenasParaDecaer()) {
            peso = peso.multiply(politica.factorPorVolumen());
        }
        return peso.setScale(4, java.math.RoundingMode.HALF_EVEN);
    }

    /** La politica de atenuacion del peso de una resena. Es configuracion, no constante. */
    public record Atenuacion(BigDecimal factorPorConflicto, BigDecimal factorPorVolumen, int resenasParaDecaer) {}

    public record Veredicto(boolean retener, List<String> marcas) {}
}
