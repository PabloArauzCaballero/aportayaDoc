package bo.aportaya.plataforma.dominio;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * El compromiso y el barajado del sorteo de turnos, en <b>una sola implementacion</b>.
 *
 * <p>Vive en {@code plataforma/comun-dominio} y no dentro de un servicio porque lo usan
 * dos: {@code grupos} para sortear (CU-60) y {@code transparencia} para verificar
 * (CU-61). El propio CU-61 lo dice: si la verificacion usara otra implementacion,
 * estariamos comprobando que dos codigos coinciden, no que el sorteo es correcto
 * (ADR-040 §4).
 *
 * <p><b>El protocolo esta fijado aca, y es publico a proposito.</b> CU-61 promete que
 * cualquiera puede recomputar el resultado "con veinte lineas de cualquier lenguaje", y
 * eso solo se cumple si cada paso esta definido sin ambiguedad:
 *
 * <ol>
 *   <li><b>Preimagen canonica.</b> {@code semilla} y cada entropia, en el orden en que
 *       fueron aportadas, separadas por un salto de linea y codificadas en UTF-8. El
 *       separador no es decoracion: sin el, {@code ("ab","c")} y {@code ("a","bc")}
 *       producirian el mismo hash, y dos paquetes distintos verificarian igual.
 *   <li><b>Compromiso.</b> {@code hash_semilla = SHA-256(preimagen)}, en hexadecimal
 *       minuscula.
 *   <li><b>Barajado.</b> Fisher-Yates desde el final. El indice de cada paso es
 *       {@code SHA-256(semilla || ":" || paso)} leido como entero sin signo y tomado
 *       modulo {@code paso + 1}. <b>No se usa el generador de numeros de ninguna
 *       plataforma</b>: {@code Random} sembrado depende de la implementacion de la JVM,
 *       y "cualquiera puede recomputarlo" valdria solo para quien tenga esta misma JVM.
 * </ol>
 *
 * <p>El sesgo de tomar modulo sobre 256 bits es del orden de 2⁻²⁵⁰ y no se corrige:
 * corregirlo con muestreo por rechazo agregaria un bucle que quien verifica desde afuera
 * tendria que reimplementar, a cambio de nada medible.
 */
public final class SorteoVerificable {

    private static final String ALGORITMO = "SHA-256";
    private static final char SEPARADOR = '\n';

    private SorteoVerificable() {}

    /**
     * Recomputa el compromiso y lo compara con el publicado.
     *
     * <p>Es la fase que hace que el sorteo no se pueda arreglar: el hash se publica antes
     * de revelar la semilla, y despues cualquiera comprueba que la semilla revelada es la
     * que estaba comprometida.
     */
    public static boolean verificarCompromiso(String semilla, List<String> entropias, String hashComprometido) {
        if (semilla == null || hashComprometido == null) {
            return false;
        }
        // Comparacion en tiempo constante. El hash es publico, pero comparar material
        // criptografico con `equals` es la costumbre que despues se cuela donde importa.
        return MessageDigest.isEqual(
                hashDelCompromiso(semilla, entropias).getBytes(StandardCharsets.UTF_8),
                hashComprometido.getBytes(StandardCharsets.UTF_8));
    }

    /** El compromiso que se publica en la fase 1, en hexadecimal minuscula. */
    public static String hashDelCompromiso(String semilla, List<String> entropias) {
        exigir(semilla != null, "la semilla es obligatoria");
        StringBuilder preimagen = new StringBuilder(semilla);
        for (String entropia : entropias == null ? List.<String>of() : entropias) {
            exigir(entropia != null, "una entropia nula no se puede comprometer");
            preimagen.append(SEPARADOR).append(entropia);
        }
        return hexadecimal(digerir(preimagen.toString()));
    }

    /**
     * Ordena los cupos con Fisher-Yates sembrado por la semilla.
     *
     * <p>Devuelve una lista nueva e inmutable: el barajado es puro y no toca la entrada.
     * La misma semilla con los mismos cupos da siempre el mismo orden, en esta maquina y
     * en la de quien verifica desde afuera.
     */
    public static <T> List<T> barajarDeterminista(String semilla, List<T> cupos) {
        exigir(semilla != null, "la semilla es obligatoria");
        exigir(cupos != null, "no hay cupos que ordenar");
        List<T> orden = new ArrayList<>(cupos);
        for (int i = orden.size() - 1; i > 0; i--) {
            int j = indiceDelPaso(semilla, i, i + 1);
            T guardado = orden.get(i);
            orden.set(i, orden.get(j));
            orden.set(j, guardado);
        }
        return List.copyOf(orden);
    }

    /** {@code SHA-256(semilla:paso) mod modulo}, sin signo. */
    private static int indiceDelPaso(String semilla, int paso, int modulo) {
        return new BigInteger(1, digerir(semilla + ":" + paso))
                .mod(BigInteger.valueOf(modulo))
                .intValue();
    }

    private static byte[] digerir(String texto) {
        try {
            return MessageDigest.getInstance(ALGORITMO).digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException imposible) {
            // SHA-256 es obligatorio en toda JVM. Si falta, el problema no es el sorteo.
            throw new IllegalStateException("esta JVM no trae " + ALGORITMO, imposible);
        }
    }

    private static String hexadecimal(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append("%02x".formatted(b));
        }
        return hex.toString();
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new ErrorDeDominio(mensaje);
        }
    }
}
