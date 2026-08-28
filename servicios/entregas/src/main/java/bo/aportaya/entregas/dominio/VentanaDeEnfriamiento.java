package bo.aportaya.entregas.dominio;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * El tiempo que una cuenta recien verificada no puede recibir plata.
 *
 * <p>Existe por una razon concreta: si alguien toma una cuenta ajena, lo primero que
 * hace es cambiar la cuenta de destino y retirar. La ventana le da al titular real el
 * tiempo de enterarse por el aviso y frenarlo.
 *
 * <p>El plazo se **guarda al verificar** ({@code bloqueada_hasta}, invariante 8). Si se
 * recalculara al consultar, acortar la politica dejaria disponibles de golpe todas las
 * cuentas que estaban en su ventana.
 */
public record VentanaDeEnfriamiento(OffsetDateTime verificadaEn, Duration ventana) {

    public OffsetDateTime disponibleDesde() {
        return verificadaEn.plus(ventana);
    }

    public boolean estaEnfriando(OffsetDateTime momento) {
        return momento.isBefore(disponibleDesde());
    }

    /** Lo que le falta, para poder decirselo al usuario en vez de solo negarle. */
    public Duration restanteEn(OffsetDateTime momento) {
        return estaEnfriando(momento) ? Duration.between(momento, disponibleDesde()) : Duration.ZERO;
    }
}
