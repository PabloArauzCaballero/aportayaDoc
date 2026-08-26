package bo.aportaya.identidad.dominio;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * Que clave se acepta.
 *
 * <p>La comprobacion contra el historial NO compara claves: compara hashes, uno por
 * uno, con el mismo verificador que usa el ingreso. Es lo que permite probar que
 * nadie reutiliza una clave **sin poder reconstruir ninguna**.
 */
public record PoliticaDeClave(int largoMinimo, int clavesQueNoSeRepiten) {

    public PoliticaDeClave {
        if (largoMinimo < 8) {
            throw new IllegalArgumentException("Una politica que acepta menos de ocho caracteres no es una politica");
        }
    }

    public Optional<MotivoDeRechazo> evaluar(
            char[] claveNueva,
            List<String> hashesAnteriores,
            BiPredicate<char[], String> coincide,
            String telefono,
            String documento) {
        if (claveNueva.length < largoMinimo) {
            return Optional.of(MotivoDeRechazo.DEMASIADO_CORTA);
        }
        String comoTexto = new String(claveNueva);
        if (contieneA(comoTexto, telefono) || contieneA(comoTexto, documento)) {
            return Optional.of(MotivoDeRechazo.DERIVADA_DE_DATOS_PERSONALES);
        }
        boolean reutilizada =
                hashesAnteriores.stream().limit(clavesQueNoSeRepiten).anyMatch(hash -> coincide.test(claveNueva, hash));
        return reutilizada ? Optional.of(MotivoDeRechazo.REUTILIZADA) : Optional.empty();
    }

    private boolean contieneA(String clave, String dato) {
        return dato != null && dato.length() >= 4 && clave.contains(dato.substring(dato.length() - 4));
    }

    public enum MotivoDeRechazo {
        DEMASIADO_CORTA,
        DERIVADA_DE_DATOS_PERSONALES,
        REUTILIZADA
    }
}
