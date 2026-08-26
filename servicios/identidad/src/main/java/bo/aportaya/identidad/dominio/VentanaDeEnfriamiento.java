package bo.aportaya.identidad.dominio;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Recuperar el acceso y vaciar la billetera en el mismo minuto es el patron de fraude
 * mas comun. Esta ventana es lo que lo corta.
 *
 * <p>Se calcula **al inicio** y se guarda; no se recalcula al consultar. Si se
 * recalculara, cambiar la politica moveria hacia atras un plazo que ya empezo a
 * correr, y eso es cambiarle las reglas a alguien a mitad de camino.
 */
public record VentanaDeEnfriamiento(OffsetDateTime hasta) {

    public static VentanaDeEnfriamiento desde(OffsetDateTime inicio, Duration duracion) {
        return new VentanaDeEnfriamiento(inicio.plus(duracion));
    }

    public boolean vigenteEn(OffsetDateTime momento) {
        return momento.isBefore(hasta);
    }

    /** Cuanto falta, para poder decirlo. Nunca se ofrece un atajo, ni por soporte. */
    public Duration restanteEn(OffsetDateTime momento) {
        return vigenteEn(momento) ? Duration.between(momento, hasta) : Duration.ZERO;
    }
}
