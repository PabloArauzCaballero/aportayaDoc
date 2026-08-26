package bo.aportaya.identidad.dominio;

import java.util.Optional;
import java.util.UUID;

/**
 * Los tres ambitos del modelo, y solo esos. **No existe un ambito {@code PLATAFORMA}:
 * el que manda es el {@code .puml}.**
 */
public enum AmbitoDeRol {
    GLOBAL,
    GRUPO,
    ORGANIZACION;

    /**
     * Un rol de grupo sin grupo seria un rol de plataforma disfrazado, y por eso la
     * base tambien lo rechaza ({@code ck_asignacion_ambito_completo}).
     */
    public boolean completoCon(Optional<UUID> ambitoId) {
        return this == GRUPO ? ambitoId.isPresent() : ambitoId.isEmpty();
    }

    public boolean esDeOperador() {
        return this == GLOBAL;
    }
}
