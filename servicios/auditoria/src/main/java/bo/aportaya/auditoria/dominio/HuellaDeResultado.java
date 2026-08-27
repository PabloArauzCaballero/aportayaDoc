package bo.aportaya.auditoria.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * El hash de lo que un reporte devolvio.
 *
 * <p>Sirve para una cosa concreta: **probar que el reporte entregado no fue alterado
 * despues**. Dos ejecuciones con los mismos parametros y los mismos datos dan el mismo
 * hash; si el archivo que alguien presenta no lo reproduce, no es el que salio de aca.
 *
 * <p>La forma canonica —filas en orden, campos separados por un caracter que no puede
 * aparecer en el dato— importa tanto como el algoritmo: sin ella, el mismo resultado
 * daria hashes distintos segun como se serialice.
 */
public final class HuellaDeResultado {

    private static final char SEPARADOR_DE_CAMPO = '\u001f';
    private static final char SEPARADOR_DE_FILA = '\u001e';

    private HuellaDeResultado() {}

    public static String de(List<List<String>> filas) {
        StringBuilder canonico = new StringBuilder();
        for (List<String> fila : filas) {
            for (String campo : fila) {
                canonico.append(campo == null ? "" : campo).append(SEPARADOR_DE_CAMPO);
            }
            canonico.append(SEPARADOR_DE_FILA);
        }
        return hexadecimal(digerir(canonico.toString()));
    }

    private static byte[] digerir(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("esta JVM no trae SHA-256", imposible);
        }
    }

    private static String hexadecimal(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append("%02x".formatted(b));
        }
        return hex.toString();
    }
}
