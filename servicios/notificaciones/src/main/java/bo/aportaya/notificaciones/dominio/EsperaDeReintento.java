package bo.aportaya.notificaciones.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * CU-83 · Retroceso exponencial con dispersion. Puro y **determinista**.
 *
 * <p>La dispersion no sale de {@code Math.random}: sale del hash del identificador del
 * envio. Dos razones. Una, que el proyecto prohibe el azar debil para cualquier cosa
 * que no deba ser adivinable, y no hace falta discutir caso por caso si esta lo es.
 * Dos, y mas importante: asi el reintento es **reproducible**, y una prueba puede
 * afirmar cuando va a ocurrir en vez de aceptar cualquier numero.
 *
 * <p>Sin dispersion, mil envios que fallan a la vez reintentan a la vez, y el
 * proveedor que ya estaba caido recibe la misma avalancha que lo tumbo.
 */
public final class EsperaDeReintento {

    /**
     * Duplicar es la definicion del retroceso exponencial, no un umbral operativo:
     * cambiarlo por tres no ajusta una politica, cambia el algoritmo.
     */
    private static final int FACTOR_EXPONENCIAL = 2;

    /** Mas alla de esto el desplazamiento desborda un long antes de tocar el techo. */
    private static final int EXPONENTE_MAXIMO = 12;

    private EsperaDeReintento() {}

    /**
     * @param techo cuanto puede llegar a esperar como maximo. Es politica operativa
     *     —cuanto tolera el negocio que tarde un aviso— y por eso entra por parametro
     *     en vez de vivir horneada aca (invariante 10).
     */
    public static Duration para(String envioId, int intento, Duration techo) {
        if (intento < 1) {
            throw new IllegalArgumentException("El primer reintento es el numero 1");
        }
        // 2, 4, 8, 16… segundos, hasta el techo.
        long base = (long) Math.pow(FACTOR_EXPONENCIAL, Math.min(intento, EXPONENTE_MAXIMO));
        long espera = Math.min(base, techo.toSeconds());

        // Hasta un 25% adicional, estable por envio e intento.
        long dispersion = Math.floorMod(semilla(envioId + ":" + intento), Math.max(1, espera / 4));
        return Duration.ofSeconds(espera + dispersion);
    }

    private static long semilla(String material) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            long valor = 0;
            for (int i = 0; i < Long.BYTES; i++) {
                valor = (valor << 8) | (resumen[i] & 0xFFL);
            }
            return valor;
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("Toda JVM trae SHA-256", imposible);
        }
    }
}
