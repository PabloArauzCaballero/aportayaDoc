package bo.aportaya.cumplimiento.dominio;

import java.util.Optional;

/**
 * CU-05 · Decide si la version que la persona dice aceptar es la que corresponde.
 *
 * <p>Se separa del repositorio porque es la regla que mas facil se rompe sin
 * notarlo: aceptar una version vieja «porque estaba en pantalla» deja al usuario
 * atado a condiciones que ya no rigen, y a la empresa sin prueba de que le mostro
 * las nuevas.
 */
public record VersionAceptable(int versionVigente, String estadoVigente) {

    private static final String VIGENTE = "VIGENTE";

    public enum Resultado {
        ACEPTABLE,
        NO_VIGENTE,
        DESACTUALIZADA
    }

    public static Resultado evaluar(Optional<VersionAceptable> contrato, int versionOfrecida) {
        if (contrato.isEmpty() || !VIGENTE.equals(contrato.get().estadoVigente())) {
            return Resultado.NO_VIGENTE;
        }
        // Menor Y mayor caen en el mismo lado: si la version ofrecida no es
        // exactamente la vigente, la persona no vio el documento que rige. Una
        // version «mas nueva» que la vigente tampoco existe.
        return contrato.get().versionVigente() == versionOfrecida ? Resultado.ACEPTABLE : Resultado.DESACTUALIZADA;
    }
}
