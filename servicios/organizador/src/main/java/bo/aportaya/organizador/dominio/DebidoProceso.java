package bo.aportaya.organizador.dominio;

import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Las dos reglas que hacen que una sancion se pueda defender.
 *
 * <p>La primera: **quien resuelve la apelacion no es quien aplico la sancion**
 * (R-ORG-05). Sin eso, apelar es pedirle a la misma persona que se desdiga, y el
 * procedimiento existe solo en el papel.
 *
 * <p>La segunda: el plazo para apelar se **guarda** al aplicar la sancion, no se
 * recalcula al mirar (invariante 8). Un plazo que se mueve cuando cambia la politica
 * es un plazo que el sancionado no puede planificar.
 */
public record DebidoProceso(OffsetDateTime aplicadaEn, Duration plazoParaApelar) {

    public OffsetDateTime limiteParaApelar() {
        return aplicadaEn.plus(plazoParaApelar);
    }

    public boolean admiteApelacionEn(OffsetDateTime momento) {
        return !momento.isAfter(limiteParaApelar());
    }

    /** Falla si quien resuelve es quien aplico. No es formalismo: es el procedimiento. */
    public static void exigirRevisorDistinto(UUID aplicadaPor, UUID resuelvePor) {
        if (aplicadaPor.equals(resuelvePor)) {
            throw new ErrorDeNegocio(
                    CodigoError.de(93, 3), "Quien resuelve la apelacion no puede ser quien aplico la sancion.");
        }
    }
}
