package bo.aportaya.nucleofinanciero.dominio.puertos;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo que este servicio necesita saber y no vive en su esquema.
 *
 * <p>Son tres hechos y ninguno es suyo: a quien apunta un alias lo sabe {@code grupos},
 * si quedan aportes pendientes lo sabe {@code aportes}, y si todavia participa de un
 * pasanaku vivo lo sabe {@code grupos}. Se preguntan por contrato (invariante 11) y
 * **antes** de abrir la transaccion (invariante 6).
 *
 * <p>Es un puerto y no un cliente porque el caso de uso no tiene por que enterarse de
 * que hubo una llamada de red: recibe hechos ya resueltos, igual que recibe el costo de
 * un retiro.
 */
public interface HechosDeOtrosServicios {

    /**
     * A que persona apunta un alias de pasanaku.
     *
     * <p>Vacio si no existe, si esta dado de baja o si {@code grupos} no contesto.
     * Quien pregunta lo trata como destino inexistente: transferir a un alias que no se
     * pudo resolver es mandar plata a ninguna parte.
     */
    Optional<UUID> usuarioDelAlias(String alias);

    /** Si le quedan aportes por pagar. Sin respuesta se asume que si (invariante 9). */
    boolean tieneObligacionesAbiertas(UUID usuarioId);

    /** Si participa hoy de algun grupo vivo. Sin respuesta se asume que si. */
    boolean participaEnGrupoActivo(UUID usuarioId);
}
