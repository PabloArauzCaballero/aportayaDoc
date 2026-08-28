package bo.aportaya.nucleofinanciero.dominio.puertos;

import bo.aportaya.plataforma.dominio.Dinero;
import java.util.Optional;
import java.util.UUID;

/**
 * Cuanto cuesta una operacion, segun quien fija los precios.
 *
 * <p>El tarifario es de {@code tarifas} y este servicio no lo lee: se lo pregunta. La
 * consulta va **fuera de la transaccion** (invariante 6), y por eso es un puerto y no
 * una llamada dentro del caso de uso.
 *
 * <p>La diferencia entre <b>cero</b> y <b>vacio</b> es la que importa y no se puede
 * confundir: cero significa que el tarifario dice que esa operacion es gratuita; vacio
 * significa que no se pudo saber. Cobrar cero cuando no se supo es regalar plata en
 * silencio, asi que quien recibe vacio rechaza (invariante 9).
 */
public interface CotizadorDeComision {

    Optional<Dinero> costoDe(String hechoGenerador, UUID referenciaId, Dinero montoBase, String claveIdempotencia);
}
