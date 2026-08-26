package bo.aportaya.grupos.dominio;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * El orden del sorteo, reproducible por cualquiera y en cualquier lenguaje.
 *
 * <p>Es Fisher-Yates, pero el indice de cada paso NO sale de un generador del
 * lenguaje: sale de {@code SHA256(semilla || ":" || paso)} interpretado como entero.
 * La diferencia importa mas de lo que parece — con {@code Random(semilla)} el
 * resultado dependeria de la implementacion de la JVM, y «cualquiera puede
 * recomputarlo» valdria solo para quien tenga esta misma JVM. Con SHA-256 lo
 * recomputa un participante con veinte lineas de Python.
 *
 * <p>El procedimiento, escrito para que se pueda repetir a mano:
 *
 * <ol>
 *   <li>Se parte de la lista de cupos ordenada por su numero.
 *   <li>Para {@code i} desde el ultimo indice hasta 1:
 *       {@code j = SHA256(semilla + ":" + i) mod (i+1)}, y se intercambian
 *       {@code i} y {@code j}.
 *   <li>El orden resultante es el orden de los turnos, de 1 en adelante.
 * </ol>
 */
public final class SorteoDeterminista {

    private SorteoDeterminista() {}

    public static List<UUID> ordenar(List<UUID> cuposPorNumero, String semilla) {
        List<UUID> mezclados = new ArrayList<>(cuposPorNumero);
        for (int i = mezclados.size() - 1; i > 0; i--) {
            int j = indiceDe(semilla, i, i + 1);
            UUID temporal = mezclados.get(i);
            mezclados.set(i, mezclados.get(j));
            mezclados.set(j, temporal);
        }
        return List.copyOf(mezclados);
    }

    /** {@code SHA256(semilla:paso) mod modulo}, sin signo. */
    private static int indiceDe(String semilla, int paso, int modulo) {
        byte[] digestion = sha256(semilla + ":" + paso);
        return new BigInteger(1, digestion).mod(BigInteger.valueOf(modulo)).intValue();
    }

    /** El compromiso publicado: {@code SHA256(semilla || entropias)}, en hexadecimal. */
    public static String comprometer(String semilla, List<String> entropias) {
        return enHexadecimal(sha256(semilla + String.join("", entropias)));
    }

    public static boolean verifica(String semilla, List<String> entropias, String hashPublicado) {
        // Comparacion en tiempo constante: aunque aca el hash sea publico, la
        // costumbre de comparar secretos con equals es la que despues se cuela.
        return MessageDigest.isEqual(
                comprometer(semilla, entropias).getBytes(StandardCharsets.UTF_8),
                hashPublicado.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tiene que existir en cualquier JVM", e);
        }
    }

    private static String enHexadecimal(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            texto.append("%02x".formatted(b));
        }
        return texto.toString();
    }
}
