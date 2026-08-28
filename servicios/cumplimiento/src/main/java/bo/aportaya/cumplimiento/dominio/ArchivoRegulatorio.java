package bo.aportaya.cumplimiento.dominio;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;

/**
 * El archivo que se remite al organismo, y su hash.
 *
 * <p>**Determinista a proposito.** El hash tiene que poder recomputarse desde los
 * mismos registros: un archivo armado con el orden del {@code SELECT} de turno
 * produciria un hash distinto cada vez, y entonces la constancia del organismo no
 * probaria que recibio *esto*, solo que recibio algo.
 */
public final class ArchivoRegulatorio {

    private ArchivoRegulatorio() {}

    public static String armar(String codigo, String periodo, List<Linea> registros) {

        var sb = new StringBuilder(codigo).append('|').append(periodo).append('\n');
        registros.stream().sorted(Comparator.comparing(Linea::id)).forEach(r -> sb.append(r.id())
                .append('|')
                .append(r.formulario())
                .append('|')
                .append(r.montoUsd().toPlainString())
                .append('\n'));
        return sb.toString();
    }

    /**
     * Una linea del archivo.
     *
     * <p>El dominio no conoce los repositorios: recibe lo que va a escribir, ya
     * resuelto. Si importara el tipo del repositorio, el atomo dejaria de ser puro y no
     * se podria probar sin base de datos.
     */
    public record Linea(String id, String formulario, BigDecimal montoUsd) {}

    public static String hashDe(String contenido) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(contenido.getBytes(StandardCharsets.UTF_8));
            var texto = new StringBuilder(resumen.length * 2);
            for (byte b : resumen) {
                texto.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return texto.toString();
        } catch (java.security.NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("Toda JVM trae SHA-256", imposible);
        }
    }
}
