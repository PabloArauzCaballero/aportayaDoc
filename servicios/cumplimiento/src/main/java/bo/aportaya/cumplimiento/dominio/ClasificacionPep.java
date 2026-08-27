package bo.aportaya.cumplimiento.dominio;

import java.util.List;
import java.util.Optional;

/**
 * CU-03 · Traduce una declaracion PEP a nivel de riesgo. Puro.
 *
 * <p>La clasificacion no es un adorno: de ella cuelgan la periodicidad de revision,
 * el tipo de debida diligencia y la intensidad del monitoreo. Por eso vive separada
 * de la persistencia y se puede probar sin base.
 */
public final class ClasificacionPep {

    private ClasificacionPep() {}

    public enum TipoPep {
        NACIONAL,
        EXTRANJERO,
        ORG_INTERNACIONAL,
        FAMILIAR,
        ALLEGADO
    }

    public enum NivelRiesgo {
        BAJO,
        MEDIO,
        ALTO
    }

    /** Lo declarado, tal como llega del formulario. */
    public record Declaracion(
            boolean esPep, Optional<TipoPep> tipo, Optional<String> cargo, Optional<String> institucion) {}

    /** Un beneficiario final declarado, con su propia condicion de PEP. */
    public record BeneficiarioFinal(String nombre, String documento, boolean esPep, Optional<TipoPep> tipo) {

        boolean identificado() {
            return documento != null && !documento.isBlank();
        }
    }

    public record Resultado(NivelRiesgo nivel, boolean exigeDiligenciaReforzada) {}

    /**
     * Un PEP sin cargo ni institucion no es una declaracion: es un casillero
     * marcado. Sin esos dos datos nadie puede cotejar nada despues.
     */
    public static boolean declaracionCompleta(Declaracion declaracion) {
        if (!declaracion.esPep()) {
            return true;
        }
        return declaracion.cargo().filter(c -> !c.isBlank()).isPresent()
                && declaracion.institucion().filter(i -> !i.isBlank()).isPresent();
    }

    /** Todo beneficiario final tiene que quedar identificado, sin excepcion. */
    public static Optional<BeneficiarioFinal> primeroSinDocumento(List<BeneficiarioFinal> beneficiarios) {
        return beneficiarios.stream().filter(b -> !b.identificado()).findFirst();
    }

    /**
     * Clasifica al titular mirando **tambien** a sus beneficiarios finales.
     *
     * <p>Un titular que no es PEP pero cuya estructura de control termina en un PEP
     * extranjero es exactamente el caso que la norma quiere ver: mirar solo al
     * titular seria dejar la puerta abierta a que el PEP opere por interpuesta
     * persona.
     */
    public static Resultado clasificar(Declaracion titular, List<BeneficiarioFinal> beneficiarios) {
        boolean hayPep = titular.esPep() || beneficiarios.stream().anyMatch(BeneficiarioFinal::esPep);
        if (!hayPep) {
            return new Resultado(NivelRiesgo.BAJO, false);
        }
        // Cualquier PEP —propio o en la estructura de control— exige reforzada. El
        // nivel de riesgo distingue el grado; la diligencia reforzada no se negocia.
        return new Resultado(NivelRiesgo.ALTO, true);
    }
}
