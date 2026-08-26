package bo.aportaya.plataforma.dominio;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Quien esta operando. Sin esto no hay consulta.
 *
 * <p>La ausencia de contexto es un defecto, no un caso: no se «asume el usuario del
 * sistema». Un trabajo programado o un consumidor de Kafka usan {@link #deSistema},
 * que es un rol con sus propias politicas de fila, no una excepcion a las politicas.
 */
public record ContextoSesion(UUID usuarioId, String rol, Traza traza, String dispositivo) {

    /** El rol de los trabajos y los consumidores. Tiene politicas, no privilegios. */
    public static final String ROL_SISTEMA = "sistema";

    public ContextoSesion {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(traza, "traza");
        if (rol == null || rol.isBlank()) {
            throw new SinContextoDeSesion("sin rol");
        }
    }

    public static ContextoSesion de(UUID usuarioId, String rol, Traza traza) {
        return new ContextoSesion(usuarioId, rol, traza, null);
    }

    public static ContextoSesion deSistema(UUID procesoId, Traza traza) {
        return new ContextoSesion(procesoId, ROL_SISTEMA, traza, null);
    }

    public boolean esSistema() {
        return ROL_SISTEMA.equals(rol);
    }

    public Optional<String> dispositivoUsado() {
        return Optional.ofNullable(dispositivo);
    }
}
