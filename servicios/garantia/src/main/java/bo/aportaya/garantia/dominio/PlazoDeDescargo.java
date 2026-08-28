package bo.aportaya.garantia.dominio;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Hasta cuando el participante puede presentar su descargo.
 *
 * <p>El plazo se calcula **al notificar** y se guarda (R-GAR-01, invariante 8). Es la
 * unica forma de que sea un derecho y no una cortesia: un plazo que se recalcula al
 * mirarlo se le puede acortar a alguien despues de habersele comunicado, y eso no lo
 * puede probar nadie.
 *
 * <p>Y corre desde la **notificacion**, no desde la deteccion: nadie puede defenderse
 * de algo que todavia no sabe que se le imputa.
 */
public record PlazoDeDescargo(OffsetDateTime notificadoEn, Duration plazo) {

    public OffsetDateTime limite() {
        return notificadoEn.plus(plazo);
    }

    public boolean admiteDescargoEn(OffsetDateTime momento) {
        return !momento.isAfter(limite());
    }

    /** Lo que falta, para poder decirselo en vez de solo negarle. */
    public Duration restanteEn(OffsetDateTime momento) {
        return admiteDescargoEn(momento) ? Duration.between(momento, limite()) : Duration.ZERO;
    }
}
