package bo.aportaya.tarifas.dominio;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Hasta cuando hay para registrar un evento significativo ante el servicio de impuestos.
 *
 * <p>El plazo se **guarda al abrir la contingencia** (R-TAR-13, invariante 8). Si se
 * recalculara al consultar, un cambio de politica movería la vara de un evento ya
 * ocurrido — y ese es un argumento que el regulador no acepta.
 */
public record PlazoDeContingencia(OffsetDateTime inicio, Duration plazoTrasElCierre) {

    /**
     * @param fin cuando se restablecio el servicio; nulo mientras siga caido
     */
    public OffsetDateTime limiteDeRegistro(OffsetDateTime fin) {
        // Mientras la contingencia sigue abierta el plazo corre desde el inicio: no se
        // puede esperar indefinidamente a que el servicio vuelva para empezar a contar.
        return (fin == null ? inicio : fin).plus(plazoTrasElCierre);
    }

    public boolean vencidoEn(OffsetDateTime momento, OffsetDateTime fin) {
        return momento.isAfter(limiteDeRegistro(fin));
    }
}
