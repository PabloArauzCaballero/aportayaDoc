package bo.aportaya.identidad.dominio;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Nadie autoriza y ejecuta el mismo tipo de operacion ({@code R-SEG-04}).
 *
 * <p>Los pares van escritos y no deducidos de una convencion de nombres: una regla de
 * control interno que depende de como alguien bautizo un permiso se rompe el dia que
 * alguien lo bautiza distinto, y nadie se entera hasta la auditoria.
 */
public final class SegregacionDeFunciones {

    /** Quien AUTORIZA algo no puede EJECUTAR eso mismo. */
    private static final Map<String, String> PARES_INCOMPATIBLES = Map.of(
            "ENTREGAS_AUTORIZAR", "ENTREGAS_EJECUTAR",
            "RETIROS_AUTORIZAR", "RETIROS_EJECUTAR",
            "PAGOS_AUTORIZAR", "PAGOS_EJECUTAR",
            "REVERSOS_AUTORIZAR", "REVERSOS_EJECUTAR");

    private SegregacionDeFunciones() {}

    /** El par en conflicto, si la combinacion resultante rompe la segregacion. */
    public static Optional<String> conflicto(Set<String> permisosResultantes) {
        for (Map.Entry<String, String> par : PARES_INCOMPATIBLES.entrySet()) {
            if (permisosResultantes.contains(par.getKey()) && permisosResultantes.contains(par.getValue())) {
                return Optional.of(par.getKey() + " con " + par.getValue());
            }
        }
        return Optional.empty();
    }

    public static boolean viola(Set<String> permisosResultantes) {
        return conflicto(permisosResultantes).isPresent();
    }
}
