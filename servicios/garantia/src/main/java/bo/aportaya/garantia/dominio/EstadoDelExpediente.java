package bo.aportaya.garantia.dominio;

import java.util.Set;

/**
 * En que quedo un expediente de incumplimiento.
 *
 * <p>{@code registro_incumplimiento} es append-only ({@code
 * tg_registro_incumplimiento_append_only}): su columna {@code estado} guarda el estado
 * <b>al detectar</b>, no el de hoy, y **no se puede mover**. El estado corriente vive en
 * {@code historial_estado_incumplimiento}, que tambien es append-only: cada transicion
 * es una fila con su motivo, quien la hizo y cuando.
 *
 * <p>Es mas trabajo que un UPDATE y es lo unico que sostiene el debido proceso: un
 * expediente cuyo estado se puede reescribir no prueba nada, y la persona sancionada no
 * tiene contra que defenderse.
 */
public final class EstadoDelExpediente {

    public static final String DETECTADO = "DETECTADO";
    public static final String NOTIFICADO = "NOTIFICADO";
    public static final String SUBSANADO = "SUBSANADO";
    public static final String CUBIERTO_POR_GARANTIA = "CUBIERTO_POR_GARANTIA";
    public static final String EN_RECUPERACION = "EN_RECUPERACION";
    public static final String EN_GESTION_COBRANZA = "EN_GESTION_COBRANZA";
    public static final String CASTIGADO_INCOBRABLE = "CASTIGADO_INCOBRABLE";
    public static final String ANULADO_POR_ERROR = "ANULADO_POR_ERROR";

    /**
     * Los que cierran el expediente.
     *
     * <p>Un expediente cerrado no vuelve a abrirse: si aparece algo nuevo, se abre otro.
     * Reabrir uno cerrado borraria la fecha en que se dio por terminado, que es la que
     * cuenta para la prescripcion.
     */
    public static final Set<String> TERMINALES =
            Set.of(SUBSANADO, CASTIGADO_INCOBRABLE, ANULADO_POR_ERROR, "PRESCRITO");

    private EstadoDelExpediente() {}

    public static boolean esTerminal(String estado) {
        return TERMINALES.contains(estado);
    }

    /**
     * Si una transicion tiene sentido.
     *
     * <p>Lo unico que se prohibe es **salir de un estado terminal**: un expediente
     * cerrado no vuelve a abrirse, porque reabrirlo borraria la fecha en que se dio por
     * terminado — la que cuenta para la prescripcion. Si aparece algo nuevo, se abre
     * otro expediente.
     *
     * <p>El resto del orden lo imponen los casos de uso, cada uno con su precondicion:
     * no se cubre lo que no se notifico, no se recupera lo que no se cubrio. Ponerlo
     * tambien aca, como una tabla de transiciones permitidas, duplicaria la regla en
     * dos lugares y tarde o temprano las dos dirian cosas distintas.
     */
    public static boolean admiteTransicion(String actual, String siguiente) {
        return !esTerminal(actual);
    }
}
