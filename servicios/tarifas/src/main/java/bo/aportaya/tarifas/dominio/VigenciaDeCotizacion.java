package bo.aportaya.tarifas.dominio;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Hasta cuando vale el numero que se le mostro al usuario.
 *
 * <p>El plazo se **guarda al cotizar** y no se recalcula al mirarlo (invariante 8).
 * Recalcularlo al consultar significa que una cotizacion vencida puede volver a
 * parecer vigente porque cambio la politica, y entonces se cobra un precio que el
 * usuario nunca vio.
 */
public record VigenciaDeCotizacion(OffsetDateTime validaHasta) {

    public static VigenciaDeCotizacion desde(OffsetDateTime ahora, Duration ventana) {
        return new VigenciaDeCotizacion(ahora.plus(ventana));
    }

    public boolean vencidaEn(OffsetDateTime momento) {
        return !momento.isBefore(validaHasta);
    }
}
