package bo.aportaya.plataforma.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * El compromiso y el barajado del sorteo de turnos, en <b>una sola implementacion</b>.
 *
 * <p>Vive en {@code plataforma/comun-dominio} y no en un servicio porque lo usan dos:
 * {@code grupos} para sortear (CU-60) y {@code transparencia} para verificar (CU-61).
 * El propio CU-61 lo dice: si la verificacion usara otra implementacion, estariamos
 * comprobando que dos codigos coinciden, no que el sorteo es correcto.
 *
 * <p><b>El protocolo esta fijado aca, y es publico a proposito</b> (ADR-040). CU-61
 * promete que cualquiera puede recomputar el resultado "con veinte lineas de cualquier
 * lenguaje"; eso solo se cumple si cada paso esta definido sin ambiguedad:
 *
 * <ol>
 *   <li><b>Preimagen canonica.</b> {@code semilla} y cada entropia, en el orden en que
 *       fueron aportadas, separadas por un salto de linea y codificadas en UTF-8. El
 *       separador no es decoracion: sin el, {@code ("ab","c")} y {@code ("a","bc")}
 *       producirian el mismo hash, y dos paquetes distintos verificarian igual.
 *   <li><b>Compromiso.</b> {@code hash_semilla = SHA-256(preimagen)}, en hexadecimal
 *       minuscula.
 *   <li><b>Flujo de azar.</b> Bloques {@code SHA-256(semilla || ":" || contador)} con el
 *       contador en decimal, leidos como enteros de 32 bits sin signo, big-endian. No se
 *       usa el generador de numeros de ninguna plataforma: eso no seria reproducible
 *       fuera de la JVM, y el punto entero es que lo sea.
 *   <li><b>Barajado.</b> Fisher-Yates desde el final, tomando cada indice con muestreo
 *       por rechazo para que no haya sesgo de modulo. Un sesgo del 0,00001 % igual
 *       convierte "el sorteo fue limpio" en una afirmacion falsa.
 * </ol>
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
        return constantes(hashDelCompromiso(semilla, entropias), hashComprometido);
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
     * <p>Devuelve una lista nueva: el barajado es puro y no toca la entrada. La misma
     * semilla con los mismos cupos da siempre el mismo orden, en esta maquina y en la del
     * que verifica desde afuera.
     */
    public static <T> List<T> barajarDeterminista(String semilla, List<T> cupos) {
        exigir(semilla != null, "la semilla es obligatoria");
        exigir(cupos != null, "no hay cupos que ordenar");
        List<T> orden = new ArrayList<>(cupos);
        FlujoDeAzar azar = new FlujoDeAzar(semilla);
        for (int i = orden.size() - 1; i > 0; i--) {
            int j = azar.indiceMenorQue(i + 1);
            T guardado = orden.get(i);
            orden.set(i, orden.get(j));
            orden.set(j, guardado);
        }
        return List.copyOf(orden);
    }

    /**
     * Enteros de 32 bits derivados de la semilla por bloques de SHA-256.
     *
     * <p>No se usa {@code java.util.Random} ni el generador de la plataforma: los dos son
     * reproducibles solo dentro de la JVM, y CU-61 promete verificacion desde cualquier
     * lenguaje.
     */
    private static final class FlujoDeAzar {
        private final String semilla;
        private byte[] bloque = new byte[0];
        private int posicion;
        private long contador;

        private FlujoDeAzar(String semilla) {
            this.semilla = semilla;
        }

        /**
         * Un indice uniforme en {@code [0, limite)}, por muestreo por rechazo.
         *
         * <p>Tomar el resto de una palabra de 32 bits sobre un limite que no es potencia
         * de dos favorece a los primeros indices. Con doce cupos el sesgo es minusculo y
         * da exactamente igual: el sorteo se defiende diciendo que es uniforme, y "casi"
         * no es una respuesta cuando lo que se reparte es quien cobra primero.
         */
        private int indiceMenorQue(int limite) {
            long techo = 0x1_0000_0000L - (0x1_0000_0000L % limite);
            while (true) {
                long palabra = siguientePalabra();
                if (palabra < techo) {
                    return (int) (palabra % limite);
                }
            }
        }

        private long siguientePalabra() {
            if (posicion + 4 > bloque.length) {
                bloque = digerir(semilla + ":" + contador++);
                posicion = 0;
            }
            long palabra = 0;
            for (int i = 0; i < 4; i++) {
                palabra = (palabra << 8) | (bloque[posicion + i] & 0xffL);
            }
            posicion += 4;
            return palabra;
        }
    }

    private static byte[] digerir(String texto) {
        try {
            return MessageDigest.getInstance(ALGORITMO).digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException imposible) {
            // SHA-256 es obligatorio en toda JVM desde la 1.4. Si falta, el problema no
            // es el sorteo.
            throw new IllegalStateException("esta JVM no trae " + ALGORITMO, imposible);
        }
    }

    private static String hexadecimal(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xf, 16));
            hex.append(Character.forDigit(b & 0xf, 16));
        }
        return hex.toString();
    }

    /**
     * Comparacion en tiempo constante.
     *
     * <p>La verificacion es publica y se puede invocar en bucle: comparar hashes con
     * cortocircuito filtra por tiempo cuantos caracteres van coincidiendo.
     */
    private static boolean constantes(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diferencia = 0;
        for (int i = 0; i < a.length(); i++) {
            diferencia |= a.charAt(i) ^ b.charAt(i);
        }
        return diferencia == 0;
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new ErrorDeDominio(mensaje);
        }
    }
}
