package bo.aportaya.garantia.dominio;

import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cuanto se le puede ejecutar a un avalista.
 *
 * <p>**Nunca mas alla del tope firmado** (R-GAR-04). Un avalista acepto responder por
 * una cantidad concreta; cobrarle mas es cobrarle algo que nunca acepto, y es la clase
 * de cosa que hace que nadie vuelva a avalar a nadie.
 *
 * <p>Se descuenta lo **ya ejecutado**: dos ejecuciones parciales que juntas superan el
 * tope lo superan igual, aunque cada una por separado parezca dentro del limite.
 */
public record TopeDelAval(Dinero montoMaximoAvalado, BigDecimal porcentajeResponsabilidad, Dinero yaEjecutado) {

    /** Lo que todavia se le puede ejecutar. Nunca negativo. */
    public Dinero disponible() {
        BigDecimal restante = montoMaximoAvalado.monto().subtract(yaEjecutado.monto());
        return Dinero.de(restante.signum() < 0 ? BigDecimal.ZERO : restante, montoMaximoAvalado.moneda());
    }

    /**
     * Cuanto se ejecuta de una deuda, respetando el porcentaje y el tope.
     *
     * @throws ErrorDeNegocio si ya no queda nada por ejecutar — el avalista cumplio
     */
    public Dinero ejecutable(Dinero deuda) {
        Dinero disponible = disponible();
        if (disponible.monto().signum() == 0) {
            throw new ErrorDeNegocio(
                    CodigoError.de(26, 4), "Ese aval ya se ejecuto hasta su tope de " + montoMaximoAvalado + ".");
        }
        Dinero porResponsabilidad = Dinero.de(
                deuda.monto().multiply(porcentajeResponsabilidad).divide(new BigDecimal("100"), 2, RoundingMode.DOWN),
                deuda.moneda());
        return porResponsabilidad.esMayorQue(disponible) ? disponible : porResponsabilidad;
    }
}
