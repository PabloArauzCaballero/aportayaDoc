package bo.aportaya.nucleofinanciero.dominio.puertos;

import java.util.UUID;

/**
 * Quien dice que el titular presento su segundo factor.
 *
 * <p>R-BIL-09 exige MFA del titular para retirar, y esa comprobacion **no la puede
 * hacer este servicio**: las credenciales y los factores viven en {@code identidad}, y
 * leer su esquema es el invariante 11. Tampoco alcanza con creerle al token: ADR-024
 * fija los reclamos del JWT —{@code sub}, {@code rol}, {@code permisos},
 * {@code nivel_diligencia}, {@code dispositivo}, {@code exp}, {@code jti}— y ninguno
 * dice si hubo segundo factor.
 *
 * <p>Por eso es un puerto: la comprobacion es una llamada a otro servicio, se hace
 * FUERA de la transaccion (invariante 6) y hoy la resuelve el adaptador local. Cuando
 * {@code identidad} publique la operacion en su contrato, se cambia el adaptador y no
 * este archivo.
 */
public interface SegundoFactor {

    /**
     * Cierto si {@code factor} es un segundo factor valido de {@code usuarioId} ahora.
     *
     * @param factor lo que el titular presento. Nulo o vacio nunca es valido.
     */
    boolean verificado(UUID usuarioId, String factor);
}
