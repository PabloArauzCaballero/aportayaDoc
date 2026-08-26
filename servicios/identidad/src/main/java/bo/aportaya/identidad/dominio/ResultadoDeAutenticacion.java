package bo.aportaya.identidad.dominio;

import bo.aportaya.plataforma.dominio.CodigoError;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Como termino el intento. **No es una excepcion, y eso es deliberado.**
 *
 * <p>El intento fallido tiene que quedar registrado: si el caso de uso lanzara para
 * rechazar, la transaccion revertiria y con ella el {@code intento_autenticacion} que
 * acaba de escribir. Los fallidos se perderian, que es exactamente lo contrario de lo
 * que pide el caso de uso — «asi los fallidos tambien quedan».
 *
 * <p>La traduccion a {@code 422} la hace la pagina, despues del {@code COMMIT}.
 */
public record ResultadoDeAutenticacion(
        boolean exitoso,
        Optional<UUID> sesionId,
        Optional<OffsetDateTime> expiraEn,
        boolean requiereFactorAdicional,
        boolean dispositivoConfiable,
        Optional<CodigoError> codigo,
        String mensaje) {

    public static ResultadoDeAutenticacion sesionAbierta(
            UUID sesionId, OffsetDateTime expiraEn, boolean dispositivoConfiable) {
        return new ResultadoDeAutenticacion(
                true, Optional.of(sesionId), Optional.of(expiraEn), false, dispositivoConfiable, Optional.empty(), "");
    }

    public static ResultadoDeAutenticacion faltaSegundoFactor(boolean dispositivoConfiable) {
        return new ResultadoDeAutenticacion(
                false,
                Optional.empty(),
                Optional.empty(),
                true,
                dispositivoConfiable,
                Optional.of(CodigoError.de(4, 3)),
                "Confirma el codigo que te enviamos para terminar de entrar.");
    }

    public static ResultadoDeAutenticacion rechazado(CodigoError codigo, String mensaje) {
        return new ResultadoDeAutenticacion(
                false, Optional.empty(), Optional.empty(), false, false, Optional.of(codigo), mensaje);
    }
}
