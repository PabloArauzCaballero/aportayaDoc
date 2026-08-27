package bo.aportaya.notificaciones.dominio;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * CU-80 · Decide si el aviso sale ahora o cuando. Puro.
 *
 * <p>Un recordatorio de cobranza a las tres de la manana no cobra mas rapido: molesta,
 * y consigue que la persona apague las notificaciones para siempre. Por eso hay
 * ventana.
 *
 * <p>Lo **obligatorio** la ignora. Un aviso de seguridad —sesion nueva, cambio de
 * clave— llega cuando pasa, no cuando conviene: retenerlo hasta la manana es
 * regalarle la noche a quien entro sin permiso.
 */
public record VentanaDeEnvio(LocalTime desde, LocalTime hasta) {

    public VentanaDeEnvio {
        if (!desde.isBefore(hasta)) {
            throw new IllegalArgumentException("La ventana de envio tiene que abrir antes de cerrar");
        }
    }

    public record Decision(boolean ahora, OffsetDateTime reprogramadaPara) {

        static Decision yaMismo() {
            return new Decision(true, null);
        }

        static Decision para(OffsetDateTime momento) {
            return new Decision(false, momento);
        }
    }

    public Decision decidir(OffsetDateTime ahora, boolean esObligatorio) {
        if (esObligatorio) {
            return Decision.yaMismo();
        }
        LocalTime hora = ahora.toLocalTime();
        if (!hora.isBefore(desde) && hora.isBefore(hasta)) {
            return Decision.yaMismo();
        }
        // Antes de que abra, hoy mismo; despues de que cierre, manana. Nunca «al
        // rato»: eso amontonaria todos los avisos retenidos en el mismo minuto.
        OffsetDateTime proxima =
                hora.isBefore(desde) ? ahora.with(desde) : ahora.plusDays(1).with(desde);
        return Decision.para(proxima);
    }
}
