package bo.aportaya.entregas.dominio;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Si un desembolso fallido se reintenta, y cuando.
 *
 * <p>La distincion que importa: hay errores **transitorios** —el proveedor no
 * respondio, el banco esta caido— y errores **definitivos** —la cuenta no existe, esta
 * cerrada, el titular no coincide. Reintentar un definitivo no lo arregla: solo demora
 * el momento en que alguien tiene que mirar el caso, mientras la plata del beneficiario
 * sigue retenida.
 *
 * <p>La espera crece por intento y es **determinista**: el mismo intento produce la
 * misma espera. Un azar aca haria que dos rearranques del proceso programaran
 * reintentos distintos para la misma orden.
 */
public final class ReintentoDeDesembolso {

    /**
     * Los codigos que no se reintentan.
     *
     * <p>No es una lista de conveniencia: cada uno describe algo que no va a cambiar
     * por esperar. La cuenta cerrada no se reabre porque insistamos.
     */
    public static final Set<String> DEFINITIVOS =
            Set.of("CUENTA_INEXISTENTE", "CUENTA_CERRADA", "TITULAR_NO_COINCIDE", "MONEDA_INCOMPATIBLE");

    private ReintentoDeDesembolso() {}

    public static boolean esDefinitivo(String codigoError) {
        return codigoError != null && DEFINITIVOS.contains(codigoError);
    }

    /**
     * Cuando toca el siguiente intento, o vacio si no hay siguiente.
     *
     * @param baseDeEspera la espera del primer reintento; es configuracion, no una
     *     constante — un proveedor lento y uno rapido no piden lo mismo
     */
    public static java.util.Optional<OffsetDateTime> siguiente(
            String codigoError, int intentosHechos, int intentosMaximos, Duration baseDeEspera, OffsetDateTime ahora) {

        if (esDefinitivo(codigoError) || intentosHechos >= intentosMaximos) {
            return java.util.Optional.empty();
        }
        // Duplica por intento: 1×, 2×, 4×. Insistir cada minuto contra un proveedor
        // caido lo mantiene caido.
        long factor = 1L << Math.max(0, intentosHechos - 1);
        return java.util.Optional.of(ahora.plus(baseDeEspera.multipliedBy(factor)));
    }
}
