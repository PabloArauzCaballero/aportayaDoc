package bo.aportaya.organizador.dominio;

import java.util.Set;

/**
 * Que acciones automatizadas **no** se ejecutan solas.
 *
 * <p>R-ORG-06: las acciones sensibles exigen confirmacion humana. La linea no es
 * caprichosa — separa lo que solo molesta si sale mal (mandar un recordatorio de mas)
 * de lo que **mueve plata ajena** (ejecutar una entrega, aplicar mora, escalar a
 * cobranza). Una regla que entrega el fondo sin que nadie mire es una regla que un
 * dia entrega el fondo al participante equivocado y nadie se entera hasta el reclamo.
 */
public final class AccionSensible {

    /**
     * Las que exigen que una persona confirme.
     *
     * <p>Salen de {@code ck_regla_automatizacion_accion}: de las seis admitidas, estas
     * cuatro tocan dinero o la reputacion de alguien.
     */
    public static final Set<String> EXIGEN_CONFIRMACION =
            Set.of("EJECUTAR_ENTREGA", "APLICAR_MORA", "ESCALAR_COBRANZA", "LIQUIDAR_PERIODO");

    /** Las que pueden correr solas: avisan, no cobran. */
    public static final Set<String> AUTOMATICAS = Set.of("ENVIAR_RECORDATORIO", "GENERAR_COBROS");

    private AccionSensible() {}

    public static boolean exigeConfirmacion(String accion) {
        return EXIGEN_CONFIRMACION.contains(accion);
    }

    /**
     * Si la accion es sensible, la regla **tiene** que pedir confirmacion.
     *
     * <p>Que la bandera exista no alcanza: una regla marcada como automatica sobre una
     * accion sensible es exactamente el agujero que R-ORG-06 cierra.
     */
    public static boolean esCoherente(String accion, boolean requiereConfirmacionHumana) {
        return !exigeConfirmacion(accion) || requiereConfirmacionHumana;
    }
}
