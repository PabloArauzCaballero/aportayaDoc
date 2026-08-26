package bo.aportaya.identidad.dominio;

import java.util.Set;

/**
 * Que factores puede llevar cada quien.
 *
 * <p>Al operador solo {@code TOTP} y {@code RESPALDO}: {@code SMS} y
 * {@code WHATSAPP} estan apagados ([[ADR-035]]) y ademas expuestos al intercambio de
 * SIM, que es como se roban las cuentas con privilegio. Al participante se le acepta
 * {@code SMS} porque el riesgo que corre es el suyo.
 */
public final class FactorAdmisible {

    private static final Set<String> PARA_OPERADOR = Set.of("TOTP", "RESPALDO");
    private static final Set<String> POR_INTERCAMBIO_DE_SIM = Set.of("SMS", "WHATSAPP");

    private FactorAdmisible() {}

    public static boolean para(PerfilDeAcceso perfil, String tipoDeFactor) {
        if (perfil.esOperador()) {
            return PARA_OPERADOR.contains(tipoDeFactor);
        }
        return !tipoDeFactor.isBlank();
    }

    public static boolean expuestoAIntercambioDeSim(String tipoDeFactor) {
        return POR_INTERCAMBIO_DE_SIM.contains(tipoDeFactor);
    }
}
