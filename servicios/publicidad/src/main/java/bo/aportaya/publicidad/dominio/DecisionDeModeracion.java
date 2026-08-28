package bo.aportaya.publicidad.dominio;

/**
 * La decision de un moderador sobre una pieza creativa, y lo que esa decision exige.
 *
 * <p>Un rechazo sin motivo es una pared: el anunciante no sabe que corregir y vuelve a
 * subir lo mismo. Por eso el motivo es obligatorio al rechazar y no al aprobar
 * (R-PUB-05, {@code ck_revision_creativa_motivo}).
 */
public record DecisionDeModeracion(String decision, String motivo) {

    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";

    public boolean esConocida() {
        return APROBADA.equals(decision) || RECHAZADA.equals(decision);
    }

    public boolean rechaza() {
        return RECHAZADA.equals(decision);
    }

    /** Cierto si falta el motivo que un rechazo exige. */
    public boolean leFaltaMotivo() {
        return rechaza() && (motivo == null || motivo.isBlank());
    }

    /** El estado de moderacion en que queda la pieza tras esta decision. */
    public String estadoResultante() {
        return decision;
    }
}
