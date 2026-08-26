package bo.aportaya.plataforma.web.traza;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * El identificador de la peticion en curso, accesible desde cualquier capa sin
 * pasarlo por parametro.
 *
 * <p>Vive en el MDC de SLF4J: toda linea de registro lo lleva, y lo mismo el cuerpo
 * de un error {@code 500} —donde es lo UNICO que sale—. Con hilos virtuales el MDC
 * viaja con el hilo, que es exactamente lo que hace falta.
 */
public final class Traza {

    public static final String CABECERA = "X-Request-Id";
    static final String CLAVE = "trazaId";
    static final String CLAVE_CU = "cu";
    static final String CLAVE_USUARIO = "usuarioId";

    private Traza() {}

    public static String actual() {
        String valor = MDC.get(CLAVE);
        return valor != null ? valor : "sin-traza";
    }

    static void fijar(String valor) {
        MDC.put(
                CLAVE,
                valor != null && !valor.isBlank() ? valor : UUID.randomUUID().toString());
    }

    /** Lo que hace que una traza de produccion lleve al caso de uso sin herramientas. */
    public static void marcarCasoDeUso(String cu, String usuarioId) {
        MDC.put(CLAVE_CU, cu);
        MDC.put(CLAVE_USUARIO, usuarioId);
    }

    static void limpiar() {
        MDC.remove(CLAVE);
        MDC.remove(CLAVE_CU);
        MDC.remove(CLAVE_USUARIO);
    }
}
