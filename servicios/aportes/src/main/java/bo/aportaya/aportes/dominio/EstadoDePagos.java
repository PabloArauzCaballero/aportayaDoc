package bo.aportaya.aportes.dominio;

import java.math.BigDecimal;

/**
 * Si esta al dia, cuanto puso y cuanto debe.
 *
 * <p>Vive en el dominio y no dentro del repositorio que lo lee porque **sale del
 * servicio**: es lo que `grupos` mira antes de una permuta y lo que `nucleo-financiero`
 * mira antes de cerrar una billetera. Un tipo que cruza la frontera no puede estar
 * atado a como se consulto la base, o la pagina web termina dependiendo de la
 * infraestructura para nombrar lo que devuelve.
 */
public record EstadoDePagos(
        boolean alDia,
        BigDecimal totalAportado,
        BigDecimal deudaVigente,
        BigDecimal porAportar,
        int obligacionesAbiertas,
        String moneda) {

    /**
     * El estado de quien no tiene ninguna obligacion.
     *
     * <p>Esta al dia: no deber nada porque todavia no se le pidio nada es estar al dia.
     * Decir que no lo esta bloquearia a cualquiera que recien entra.
     */
    public static EstadoDePagos sinObligaciones() {
        return new EstadoDePagos(true, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, "BOB");
    }
}
